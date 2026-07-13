package com.alesharik.digitalgrid.din.rack

import com.alesharik.digitalgrid.Digitalgrid
import com.alesharik.digitalgrid.DigitalgridRegistry.BlockEntities
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.item.DinRackItem
import com.alesharik.digitalgrid.din.item.plc.PlcBusConnector
import com.alesharik.digitalgrid.din.item.plc.PlcBusModule
import com.alesharik.digitalgrid.utils.voxel.DirectionalVoxelShape
import com.alesharik.digitalgrid.utils.voxel.rotationYDegreesInv
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Block.box
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks
import org.patryk3211.powergrid.electricity.base.*
import org.patryk3211.powergrid.electricity.sim.ElectricWire
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint
import java.util.stream.Stream

class DinRackBlockEntity(pos: BlockPos, state: BlockState):
    ElectricBlockEntity(BlockEntities.DIN_RACK, pos, state), IElectric, IHaveGoggleInformation {
    private var mEntities: MutableList<DinRackEntityPlacement>? = ArrayList()
    private var shapeCache: DirectionalVoxelShape? = null
    private var terminalCache: Array<TerminalBoundingBox>? = null

    /** Overhang of the -u neighbor's spanning module into this rack; persisted, server-authored. */
    private var overhang: OverhangGhost? = null
    private var proxyCache: Array<TerminalBoundingBox>? = null

    /** Sim wires continuing this rack's bus rails into the +u neighbor rack; owned by this rack. */
    private val busBridges = ArrayList<ElectricWire>()

    /** Sim wires tying the +u neighbor's proxy nodes to this rack's spanning module terminals; owned by this rack. */
    private val overhangBridges = ArrayList<ElectricWire>()

    /** Cleared on invalidate so a neighbor's refresh never re-bridges to a rack being unloaded/removed. */
    private var bridgeable = true

    val entities: List<DinRackEntityPlacement>
        get() = mEntities ?: emptyList()

    val shape: DirectionalVoxelShape
        get() = shapeCache ?: buildShape().also { shapeCache = it }

    private val terminals: Array<TerminalBoundingBox>
        get() = terminalCache ?: buildTerminals().also { terminalCache = it }

    /** Ghost terminal boxes shifted into this rack's space; exposed after the own terminals. */
    private val proxyTerminals: Array<TerminalBoundingBox>
        get() = proxyCache ?: buildProxyTerminals().also { proxyCache = it }

    /** Wireable external nodes: own module terminals then proxies; the two bus rails follow. */
    private val exposedTerminalCount: Int
        get() = terminals.size + proxyTerminals.size

    private fun buildProxyTerminals(): Array<TerminalBoundingBox> {
        val ghost = overhang ?: return emptyArray()
        return ghost.proxiedTerminals
            .map { ghost.entity.terminalBoundingBox[it].offset(ghost.offset / 16.0, 0.0, 0.0) }
            .toTypedArray()
    }

    // The owner's renderer draws the spanning module into the +u neighbor's block space;
    // without an inflated render AABB the overhang pops out of view when the owner's block
    // leaves the frustum.
    override fun createRenderBoundingBox(): AABB = AABB(worldPosition).inflate(1.0)

    fun invalidateInternal() {
        // The rebuild below recreates all external nodes (bus rails included), so bridge wires
        // must detach first and re-resolve after — ours here, the -u neighbor's via the poke.
        dropBusBridges()
        dropOverhangBridges()
        shapeCache = null
        terminalCache = null
        proxyCache = null
        dropStaleClientConnections()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_ALL)
        electricBehaviour.rebuildCircuit(true)
        refreshAllBridges()
        neighborRack(plusU.opposite)?.refreshAllBridges()
    }

    /** World direction of increasing DIN unit; must agree with [DinRackBlock.hitToUnit]. */
    private val plusU: Direction
        get() = blockState.getValue(DinRackBlock.FACING).clockWise

    /** Bus rails sit right after the exposed (module + proxy) terminals in the external node list. */
    private val busPlusIndex: Int
        get() = exposedTerminalCount

    private fun neighborRack(dir: Direction): DinRackBlockEntity? {
        val lvl = level ?: return null
        val neighborPos = worldPosition.relative(dir)
        if (!lvl.isLoaded(neighborPos)) return null
        val neighborState = lvl.getBlockState(neighborPos)
        if (neighborState.block !is DinRackBlock) return null
        if (neighborState.getValue(DinRackBlock.FACING) != blockState.getValue(DinRackBlock.FACING)) return null
        return lvl.getBlockEntity(neighborPos) as? DinRackBlockEntity
    }

    private fun dropBusBridges() {
        busBridges.forEach { it.remove() }
        busBridges.clear()
    }

    /**
     * Reconnects the bus rails to the +u neighbor rack, if it exists and faces the same way.
     * Runs on both sides: the client keeps its own sim networks and resolves bridges the same
     * way once the neighbor's block entity is present.
     */
    fun refreshBusBridges() {
        dropBusBridges()
        val lvl = level ?: return
        if (!bridgeable || isRemoved) return
        val neighbor = neighborRack(plusU) ?: return
        if (!neighbor.bridgeable || neighbor.isRemoved) return
        for (rail in 0..1) {
            GlobalElectricNetworks.makeSimpleConnection(
                lvl,
                BlockWireEndpoint(worldPosition, busPlusIndex + rail),
                BlockWireEndpoint(neighbor.worldPosition, neighbor.busPlusIndex + rail),
                RAIL_RESISTANCE,
            )?.let { busBridges.add(it) }
        }
    }

    private fun dropOverhangBridges() {
        overhangBridges.forEach { it.remove() }
        overhangBridges.clear()
    }

    /** Flat index of the placement's first terminal in this rack's external node list, or -1 if absent. */
    private fun terminalOffsetOf(placement: DinRackEntityPlacement): Int {
        var off = 0
        for (p in entities) {
            if (p === placement) return off
            off += p.entity.terminalBoundingBox.size
        }
        return -1
    }

    /**
     * Reconnects this rack's spanning module terminals to the +u neighbor's proxy nodes.
     * Same lifecycle and both-sides semantics as [refreshBusBridges].
     */
    fun refreshOverhangBridges() {
        dropOverhangBridges()
        val lvl = level ?: return
        if (!bridgeable || isRemoved) return
        val spanning = entities.firstOrNull { it.u.value + it.entity.width.value > RACK_WIDTH } ?: return
        val neighbor = neighborRack(plusU) ?: return
        if (!neighbor.bridgeable || neighbor.isRemoved) return
        val ghost = neighbor.overhang ?: return
        if (ghost.offset != spanning.u.value - RACK_WIDTH) return
        if (ghost.item !== spanning.stack.item) return
        val ownBase = terminalOffsetOf(spanning)
        if (ownBase < 0) return
        val proxyBase = neighbor.terminals.size
        ghost.proxiedTerminals.forEachIndexed { ordinal, local ->
            GlobalElectricNetworks.makeSimpleConnection(
                lvl,
                BlockWireEndpoint(worldPosition, ownBase + local),
                BlockWireEndpoint(neighbor.worldPosition, proxyBase + ordinal),
                PROXY_RESISTANCE,
            )?.let { overhangBridges.add(it) }
        }
    }

    /** All cross-rack link kinds refreshed together — call on self after a rebuild, on a neighbor via poke. */
    private fun refreshAllBridges() {
        refreshBusBridges()
        refreshOverhangBridges()
        refreshPlcBusLinks()
    }

    /** Server-side only; runs the full terminal-shift ceremony so wires on proxies remap or break. */
    internal fun setOverhang(ghost: OverhangGhost?) {
        if (level?.isClientSide != false) return
        if (ghost == null && overhang == null) return
        // Snapshot before mutating the ghost — proxy keys must come from the OLD layout.
        val old = globalTerminalMap(entities, overhang)
        overhang = ghost
        remapConnections(old)
        setChanged()
        invalidateInternal()
    }

    internal fun clearOverhang() = setOverhang(null)

    /**
     * Heals an orphaned ghost (owner rack gone or its spanning module lost while this rack
     * was unloaded or the world was edited). Only acts when the owner's chunk is loaded —
     * an unloaded owner is not evidence of anything.
     */
    private fun validateOverhang() {
        if (level?.isClientSide != false) return
        if (overhang == null) return
        val lvl = level ?: return
        if (!lvl.isLoaded(worldPosition.relative(plusU.opposite))) return
        if (spanningOwnerPlacement() == null) clearOverhang()
    }

    /**
     * Owner-side mirror of [validateOverhang]: heals a spanning placement whose +u holder
     * lost the ghost while this rack was unloaded (holder broken or replaced). Re-authors
     * the ghost when the holder can still take it, otherwise drops the module. Only acts
     * when the holder's chunk is loaded.
     */
    private fun validateSpanning() {
        if (level?.isClientSide != false) return
        val lvl = level ?: return
        val spanning = entities.firstOrNull { it.u.value + it.entity.width.value > RACK_WIDTH } ?: return
        if (!lvl.isLoaded(worldPosition.relative(plusU))) return
        val neighbor = neighborRack(plusU)
        val consistent = neighbor?.overhang?.let {
            it.offset == spanning.u.value - RACK_WIDTH && it.item === spanning.stack.item
        } == true
        if (consistent) return
        // A ghost on the holder can only belong to this rack; inconsistent means stale.
        if (neighbor?.overhang != null) neighbor.clearOverhang()
        val over = spanning.u.value + spanning.entity.width.value - RACK_WIDTH
        val item = spanning.stack.item as? DinRackItem
        if (neighbor != null && item != null && neighbor.canAcceptOverhang(over)) {
            neighbor.setOverhang(OverhangGhost.of(spanning.u.value - RACK_WIDTH, item))
        } else {
            removeModuleAt(spanning.u)
            Block.popResource(lvl, worldPosition, spanning.stack)
        }
    }

    /**
     * (Re)connects the PLC bus nodes of directly touching bus modules, including the single
     * cross-rack link into the +u neighbor. Connect-only: links only ever become stale
     * through node removal (a module leaving detaches its node in onDetach, which severs
     * its links and splits the network), so there is nothing to drop here; connectTo is
     * idempotent. Server-only — CC wired networks do not exist on the client.
     */
    private fun refreshPlcBusLinks() {
        val lvl = level ?: return
        if (lvl.isClientSide) return
        if (!bridgeable || isRemoved) return
        val sorted = entities.sortedBy { it.u.value }
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (a.u.value + a.entity.width.value != b.u.value) continue
            val connA = (a.entity as? PlcBusModule)?.busConnector() ?: continue
            val connB = (b.entity as? PlcBusModule)?.busConnector() ?: continue
            connA.connectTo(connB)
        }
        // Cross-rack: our edge module touching the +u neighbor's edge module.
        val neighbor = neighborRack(plusU) ?: return
        if (!neighbor.bridgeable || neighbor.isRemoved) return
        plcSeamPair(neighbor)?.let { (a, b) -> a.connectTo(b) }
    }

    /** The touching PLC bus connector pair across this rack's +u seam into [neighbor], if any. */
    private fun plcSeamPair(neighbor: DinRackBlockEntity): Pair<PlcBusConnector, PlcBusConnector>? {
        val last = entities.maxByOrNull { it.u.value } ?: return null
        // contact == 0: module ends flush at the seam; > 0: it overhangs and its far edge
        // sits at the neighbor's local unit `contact`.
        val contact = last.u.value + last.entity.width.value - RACK_WIDTH
        if (contact < 0) return null
        val connLast = (last.entity as? PlcBusModule)?.busConnector() ?: return null
        val neighborFirst = neighbor.entities.minByOrNull { it.u.value } ?: return null
        if (neighborFirst.u.value != contact) return null
        val connFirst = (neighborFirst.entity as? PlcBusModule)?.busConnector() ?: return null
        return connLast to connFirst
    }

    /**
     * Severs the PLC seam links on both of this rack's seams. Needed before wrench rotation:
     * seam links are connect-only by design (they normally die with node removal), so a
     * facing change must tear them down explicitly or CC keeps routing across the old seam.
     */
    private fun disconnectPlcSeamLinks() {
        if (level?.isClientSide != false) return
        neighborRack(plusU)?.let { neighbor -> plcSeamPair(neighbor)?.let { (a, b) -> a.disconnectFrom(b) } }
        neighborRack(plusU.opposite)?.let { minus -> minus.plcSeamPair(this)?.let { (a, b) -> a.disconnectFrom(b) } }
    }

    /**
     * Server-side; called by the block before wrench rotation. Spanning geometry and
     * cross-rack links cannot survive a facing change: drops seam-spanning modules and
     * severs the PLC seam links while the old facing still resolves the seams. Returns
     * the old -u neighbor so [afterWrenchRotation] can drop its misaligned bridges.
     */
    internal fun beforeWrenchRotation(): DinRackBlockEntity? {
        dropSpanningModules()
        disconnectPlcSeamLinks()
        return neighborRack(plusU.opposite)
    }

    /** Server-side; relinks along the new facing and lets the old -u neighbor drop stale bridges. */
    internal fun afterWrenchRotation(oldMinusNeighbor: DinRackBlockEntity?) {
        invalidateInternal()
        oldMinusNeighbor?.refreshAllBridges()
    }

    override fun initialize() {
        super.initialize()
        validateOverhang()
        validateSpanning()
        refreshAllBridges()
        // A rack that appears (placed or chunk-loaded) is what the -u neighbor was waiting for.
        neighborRack(plusU.opposite)?.also {
            it.validateSpanning()
            it.refreshAllBridges()
        }
        // ...and the +u neighbor may hold a ghost that only we can prove stale.
        neighborRack(plusU)?.validateOverhang()
    }

    override fun remove() {
        // Detach cleanly before super lets ElectricBehaviour tear down the external nodes.
        dropBusBridges()
        dropOverhangBridges()
        super.remove()
    }

    /**
     * A client-side circuit rebuild must never reach [ElectricBehaviour]'s removed-endpoint
     * path — wire entities broadcast packets from it, which is server-only. The server has
     * already broken these connections via its own rebuild; mirror that locally.
     */
    private fun dropStaleClientConnections() {
        if (level?.isClientSide != true) return
        electricBehaviour.connections.keys.removeIf { it.terminal >= exposedTerminalCount }
    }

    fun canPlace(u: DINUnit, width: DINUnit): Boolean {
        if (!canStore(entities, overhang, u, width)) return false
        val over = u.value + width.value - RACK_WIDTH
        if (over > 0) {
            val neighbor = neighborRack(plusU) ?: return false
            if (!neighbor.canAcceptOverhang(over)) return false
        }
        return true
    }

    /** True if units [0, width) are free to host a -u neighbor's overhang. */
    private fun canAcceptOverhang(width: Int): Boolean =
        overhang == null && entities.none { it.u.value < width }

    /**
     * Local-only occupancy check: bounds, own placements and the ghost. Unlike the
     * interactive path it allows u+width to run past RACK_WIDTH and never touches the
     * neighbor — usable at read time when the level is not set yet.
     */
    private fun canStore(
        placements: List<DinRackEntityPlacement>,
        ghost: OverhangGhost?,
        u: DINUnit,
        width: DINUnit
    ): Boolean {
        if (width.value <= 0 || width.value > RACK_WIDTH) return false
        if (u.value < 0 || u.value >= RACK_WIDTH) return false
        if (ghost != null && u.value < ghost.occupiedWidth) return false
        return placements.none { placed ->
            val start = placed.u.value
            val end = start + placed.entity.width.value
            u.value < end && u.value + width.value > start
        }
    }

    fun moduleAt(u: DINUnit): DinRackEntityPlacement? =
        entities.firstOrNull { it.u.value <= u.value && u.value < it.u.value + it.entity.width.value }

    /** A module resolved to the rack that owns it — [rack] is not `this` for overhang hits. */
    data class ResolvedModule(val rack: DinRackBlockEntity, val placement: DinRackEntityPlacement)

    /** The -u neighbor's placement that overhangs into this rack, if any. */
    private fun spanningOwnerPlacement(): ResolvedModule? {
        val ghost = overhang ?: return null
        val owner = neighborRack(plusU.opposite) ?: return null
        val placement = owner.entities.firstOrNull {
            it.u.value + it.entity.width.value > RACK_WIDTH
        } ?: return null
        if (placement.u.value - RACK_WIDTH != ghost.offset) return null
        if (placement.stack.item !== ghost.item) return null
        return ResolvedModule(owner, placement)
    }

    /** Like [moduleAt], but also resolves units covered by the -u neighbor's overhang. */
    fun resolvedModuleAt(u: DINUnit): ResolvedModule? {
        moduleAt(u)?.let { return ResolvedModule(this, it) }
        val ghost = overhang ?: return null
        if (u.value >= ghost.occupiedWidth) return null
        return spanningOwnerPlacement()
    }

    /** Hands a module its [DinRackEntity.ModuleContext] so it can reach the world and its backing stack. */
    private fun attachModule(placement: DinRackEntityPlacement) {
        placement.entity.onAttach(ModuleContextImpl(placement.stack))
    }

    /** Server-side only. Returns false if the interval is occupied or out of bounds. */
    fun placeModule(u: DINUnit, entity: DinRackEntity, stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (!canPlace(u, entity.width)) return false
        val list = mEntities ?: return false
        val oldTerminals = globalTerminalMap(entities, overhang)
        val placement = DinRackEntityPlacement(u, entity, stack)
        list.add(placement)
        attachModule(placement)
        remapConnections(oldTerminals)
        setChanged()
        invalidateInternal()
        if (u.value + entity.width.value > RACK_WIDTH) {
            // After our own rebuild: our refresh above laid nothing (no ghost yet); the
            // neighbor's rebuild inside setOverhang pokes us back and lays the bridges
            // against both racks' final node sets.
            (stack.item as? DinRackItem)?.let { item ->
                neighborRack(plusU)?.setOverhang(OverhangGhost.of(u.value - RACK_WIDTH, item))
            }
        }
        return true
    }

    /** Server-side only. Removes the module covering [u]; returns its placement or null. */
    fun removeModuleAt(u: DINUnit): DinRackEntityPlacement? {
        val placement = moduleAt(u) ?: return null
        val oldTerminals = globalTerminalMap(entities, overhang)
        if (mEntities?.remove(placement) != true) return null
        placement.entity.onDetach()
        remapConnections(oldTerminals)
        if (placement.u.value + placement.entity.width.value > RACK_WIDTH) {
            neighborRack(plusU)?.clearOverhang()
        }
        setChanged()
        invalidateInternal()
        return placement
    }

    /**
     * Server-side; called by the block on real removal (not chunk unload), while this block
     * entity is still alive. Drops every module touching this rack: contained modules, this
     * rack's own spanning module, and the -u owner's module overhanging into this rack.
     */
    fun dropModulesOnBreak() {
        val lvl = level ?: return
        entities.forEach { Block.popResource(lvl, worldPosition, it.stack) }
        if (entities.any { it.u.value + it.entity.width.value > RACK_WIDTH }) {
            neighborRack(plusU)?.clearOverhang()
        }
        if (overhang != null) {
            // Resolve the owner first — spanningOwnerPlacement() reads the ghost. Then drop
            // the ghost without ceremony so the owner's clearOverhang poke below becomes a
            // no-op instead of rebuilding a dying circuit.
            val resolved = spanningOwnerPlacement()
            overhang = null
            resolved?.let { (owner, placement) ->
                owner.removeModuleAt(placement.u)
                Block.popResource(lvl, owner.worldPosition, placement.stack)
            }
        }
    }

    /**
     * Server-side; removes and drops any module spanning this rack's seams (its own spanning
     * module and the -u owner's overhang) — used before wrench rotation, which the spanning
     * geometry cannot survive.
     */
    fun dropSpanningModules() {
        val lvl = level ?: return
        entities.firstOrNull { it.u.value + it.entity.width.value > RACK_WIDTH }?.let {
            removeModuleAt(it.u)
            Block.popResource(lvl, worldPosition, it.stack)
        }
        if (overhang != null) {
            val resolved = spanningOwnerPlacement()
            if (resolved != null) {
                resolved.rack.removeModuleAt(resolved.placement.u)
                Block.popResource(lvl, resolved.rack.worldPosition, resolved.placement.stack)
            } else {
                clearOverhang()
            }
        }
    }

    override fun invalidate() {
        // Runs on both block break and chunk unload. On unload our block stays in the world, so
        // flag ourselves unbridgeable before poking the -u neighbor — its refresh must drop the
        // bridge into our about-to-be-removed nodes and not lay a new one.
        bridgeable = false
        dropBusBridges()
        dropOverhangBridges()
        neighborRack(plusU.opposite)?.refreshAllBridges()
        // Create's SmartBlockEntity.setRemoved() calls invalidate() on BOTH block break and chunk
        // unload, whereas remove() is skipped on chunk unload — so release module transient resources
        // here (e.g. close a PLC's ServerComputer, so it can't linger and get duplicated on a fast
        // reload). onDetach() is idempotent, so the extra call on a genuine block break is harmless.
        super.invalidate()
        mEntities?.forEach { it.entity.onDetach() }
    }

    /**
     * Wire endpoints are keyed by flat terminal index, so a module list change shifts every
     * later module's indices. Re-key each wire to its module's new offset and break wires
     * whose module is gone. Must run before [invalidateInternal]: ElectricBehaviour's rebuild
     * breaks connections by index range alone, which would kill shifted wires that fell past
     * the new terminal count and silently rewire a removed module's wires to other modules.
     */
    private fun remapConnections(oldTerminals: Map<Int, TerminalKey>) {
        if (level?.isClientSide != false) return
        val newTerminals = globalTerminalMap(entities, overhang).entries.associate { (idx, key) -> key to idx }
        // Snapshot: endpointRemoved/setEndpointX mutate the live map reentrantly.
        val snapshot = electricBehaviour.connections.entries.map { it.key to it.value.toList() }
        for ((endpoint, wires) in snapshot) {
            val newIdx = oldTerminals[endpoint.terminal]?.let { newTerminals[it] }
            if (newIdx == endpoint.terminal) continue
            if (newIdx == null) {
                wires.forEach { it.endpointRemoved(endpoint) }
                electricBehaviour.connections.remove(endpoint)
            } else {
                val newEndpoint = BlockWireEndpoint(worldPosition, newIdx)
                for (wire in wires) {
                    when (endpoint) {
                        wire.endpoint1 -> wire.setEndpoint1(newEndpoint)
                        wire.endpoint2 -> wire.setEndpoint2(newEndpoint)
                    }
                    // setEndpointX does not broadcast; sync the new endpoint to clients,
                    // which also converges their connection maps.
                    wire.sendExtraData()
                }
            }
        }
    }

    private fun globalTerminalMap(
        placements: List<DinRackEntityPlacement>,
        ghost: OverhangGhost?
    ): Map<Int, TerminalKey> {
        val map = HashMap<Int, TerminalKey>()
        var off = 0
        for (placed in placements) {
            for (local in placed.entity.terminalBoundingBox.indices) {
                map[off + local] = TerminalKey(placed.u.value, local)
            }
            off += placed.entity.terminalBoundingBox.size
        }
        ghost?.proxiedTerminals?.forEachIndexed { ordinal, local ->
            map[off + ordinal] = TerminalKey(ghost.offset, local)
        }
        return map
    }

    private fun buildShape(): DirectionalVoxelShape {
        var combined = entities.fold(BASE_SHAPE) { acc, placed ->
            Shapes.join(acc, placed.entity.shape.move(placed.u.toDouble() / 16.0, 0.0, 0.0), BooleanOp.OR)
        }
        overhang?.let {
            combined = Shapes.join(combined, it.entity.shape.move(it.offset / 16.0, 0.0, 0.0), BooleanOp.OR)
        }
        // Clip to the block cell: a spanning module's shape must not leak out of the owner
        // (hit attribution would depend on the ray path), and the ghost's part that still
        // belongs to the owner must not leak out of this rack.
        return DirectionalVoxelShape(Shapes.join(combined, Shapes.block(), BooleanOp.AND).optimize())
    }

    private fun buildTerminals() = entities
        .filter { it.entity.terminalBoundingBox.isNotEmpty() }
        .flatMap { e -> e.entity.terminalBoundingBox.toList().map { it.offset(e.u.toDouble() / 16.0, 0.0, 0.0) } }
        .toTypedArray()

    override fun buildCircuit(cb: IElectricEntity.CircuitBuilder) {
        // The bus rails are the two external nodes after the exposed terminals. External nodes
        // survive internal-only rebuilds and chunk pause, so neighbor racks' bridge wires can
        // attach to them; creating them unconditionally keeps the node count — and thus Power
        // Grid's node state sync payload — identical on client and server regardless of which
        // neighbor chunks happen to be loaded. terminal()/terminalCount() expose only the module
        // and proxy terminals, so players cannot wire to the rails directly.
        // Exposed = own module terminals + proxy terminals for the -u neighbor's overhang.
        // Proxy nodes carry no internal connections; the owner rack bridges them to its
        // real terminal nodes with sim wires. setTerminalCount creates them eagerly.
        val exposed = exposedTerminalCount
        cb.setTerminalCount(exposed + 2)
        val busPlus = cb.terminalNode(exposed)
        val busMinus = cb.terminalNode(exposed + 1)
        var off = 0
        for (en in entities) {
            en.entity.buildCircuit(CircuitContextImpl(
                off = off,
                terminalCount = en.entity.terminalBoundingBox.size,
                builder = cb,
                bus24V = busPlus,
                busMinus = busMinus,
            ))
            off += en.entity.terminalBoundingBox.size
        }
    }

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        super.addBehaviours(behaviours)
        // Modules piggyback on Power Grid's periodic node state sync; module lists
        // stay identical on both sides (same NBT order), keeping payloads aligned.
        electricBehaviour.setSyncAppender(object : ElectricBehaviour.SyncAppender {
            override fun writeToSync(buffer: FriendlyByteBuf) {
                entities.forEach { it.entity.writeSync(buffer) }
            }

            override fun readFromSync(buffer: FriendlyByteBuf) {
                entities.forEach { it.entity.readSync(buffer) }
            }
        })
    }

    override fun electricalTick() {
        var save = false
        var sync = false
        for (placed in entities) {
            when (placed.entity.electricalTick()) {
                DinRackEntity.TickResult.SAVE -> save = true
                DinRackEntity.TickResult.SAVE_AND_SYNC -> { save = true; sync = true }
                DinRackEntity.TickResult.NONE -> {}
            }
        }
        if (save) setChanged()
        if (sync) sendData()
    }

    private fun targetedModule(): DinRackEntityPlacement? {
        val hit = Minecraft.getInstance().hitResult as? BlockHitResult ?: return null
        if (hit.blockPos != blockPos) return null
        val u = DinRackBlock.hitToUnit(blockState, blockPos, hit)
        return resolvedModuleAt(u)?.placement
    }

    /** Client-side only (Create's goggle overlay); shows the targeted module's info. */
    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        val placement = targetedModule() ?: return false
        return placement.entity.addToGoggleTooltip(tooltip, isPlayerSneaking)
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        // Modules must be parsed before super.read: ElectricBehaviour.read rebuilds the
        // circuit on the client (its "Rebuild" sync flag) and must see the new module list.
        mEntities?.forEach { it.entity.onDetach() }
        overhang = if (tag.contains("Overhang", Tag.TAG_COMPOUND.toInt())) {
            OverhangGhost.read(tag.getCompound("Overhang")).also {
                if (it == null) Digitalgrid.LOGGER.warn("Dropping DIN rack overhang at {}: unparseable tag", worldPosition)
            }
        } else null
        mEntities = readModules(tag, registries, clientPacket)
        mEntities?.forEach(::attachModule)
        shapeCache = null
        terminalCache = null
        proxyCache = null
        dropStaleClientConnections()
        super.read(tag, registries, clientPacket)
        invalidateInternal()
    }

    private fun readModules(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean
    ): MutableList<DinRackEntityPlacement> {
        val rebuilt = ArrayList<DinRackEntityPlacement>()
        val list = tag.getList("Modules", Tag.TAG_COMPOUND.toInt())
        for (i in list.indices) {
            val entry = list.getCompound(i)
            val itemTag = entry.get("Item") ?: continue
            val stack = ItemStack.parse(registries, itemTag).orElse(ItemStack.EMPTY)
            if (stack.isEmpty) {
                Digitalgrid.LOGGER.warn("Skipping DIN module at {}: unparseable item", worldPosition)
                continue
            }
            val item = stack.item as? DinRackItem
            if (item == null) {
                Digitalgrid.LOGGER.warn("Skipping DIN module at {}: {} is not a DinRackItem", worldPosition, stack.item)
                continue
            }
            val entity = item.createEntity()
            if (entry.contains("Data", Tag.TAG_COMPOUND.toInt())) {
                entity.read(entry.getCompound("Data"), registries, clientPacket)
            }
            val u = DINUnit(entry.getInt("U"))
            if (!canStore(rebuilt, overhang, u, entity.width)) {
                Digitalgrid.LOGGER.warn("Skipping DIN module at {}: invalid or overlapping position {}", worldPosition, u.value)
                continue
            }
            rebuilt.add(DinRackEntityPlacement(u, entity, stack))
        }
        return rebuilt
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        val list = ListTag()
        for (placed in entities) {
            if (placed.stack.isEmpty) continue
            val entry = CompoundTag()
            entry.putInt("U", placed.u.value)
            entry.put("Item", placed.stack.save(registries))
            val data = CompoundTag()
            placed.entity.write(data, registries, clientPacket)
            if (!data.isEmpty) entry.put("Data", data)
            list.add(entry)
        }
        tag.put("Modules", list)
        overhang?.let { tag.put("Overhang", it.write()) }
    }

    override fun terminalCount(): Int = exposedTerminalCount

    override fun terminal(
        state: BlockState,
        term: Int
    ): ITerminalPlacement? {
        val own = terminals
        val proxies = proxyTerminals
        val box = when {
            term < 0 -> return null
            term < own.size -> own[term]
            term < own.size + proxies.size -> proxies[term - own.size]
            else -> return null
        }
        val direction = state.getValue(HorizontalDirectionalBlock.FACING)
        return box.rotateAroundY(direction.rotationYDegreesInv().toInt())
    }

    data class DinRackEntityPlacement(
        val u: DINUnit,
        val entity: DinRackEntity,
        val stack: ItemStack,
    )

    private inner class ModuleContextImpl(override val stack: ItemStack) : DinRackEntity.ModuleContext {
        override val level: Level
            get() = this@DinRackBlockEntity.level ?: error("DIN rack module accessed a null level")
        override val pos: BlockPos
            get() = worldPosition
        override val blockEntity: BlockEntity
            get() = this@DinRackBlockEntity

        override fun markChanged() = setChanged()

        override fun requestSync() = sendData()
    }

    /** Module-space terminal id, stable while the module stays installed. */
    private data class TerminalKey(val u: Int, val local: Int)

    private class CircuitContextImpl(
        private val off: Int,
        private val terminalCount: Int,
        override val builder: IElectricEntity.CircuitBuilder,
        override val bus24V: FloatingNode,
        override val busMinus: FloatingNode,
    ): DinRackEntity.CircuitContext {
        override fun terminalNode(idx: Int): FloatingNode {
            if (idx !in 0..<terminalCount) {
                throw IllegalArgumentException("Could not select terminal node $idx, max nodes are $terminalCount")
            }
            return builder.terminalNode(off + idx)
        }
    }

    companion object {
        const val RACK_WIDTH = 16

        /** Resistance of one bus rail joint between two adjacent racks. */
        private const val RAIL_RESISTANCE = 0.01f

        /** Proxy terminals are the same physical terminal — keep the bridge nearly ideal. */
        private const val PROXY_RESISTANCE = 0.001f

        internal val BASE_SHAPE = Stream.of(
            box(0.0, 8.0, 13.0, 16.0, 10.0, 16.0),
            box(0.0, 10.0, 13.0, 16.0, 11.0, 14.0),
            box(0.0, 7.0, 13.0, 16.0, 8.0, 14.0)
        ).reduce { v1: VoxelShape, v2: VoxelShape -> Shapes.join(v1, v2, BooleanOp.OR) }.get().optimize()
    }
}