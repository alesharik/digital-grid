package com.alesharik.digitalgrid.din.rack

import com.alesharik.digitalgrid.Digitalgrid
import com.alesharik.digitalgrid.DigitalgridRegistry.BlockEntities
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.item.DinRackItem
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

    /** Sim wires continuing this rack's bus rails into the +u neighbor rack; owned by this rack. */
    private val busBridges = ArrayList<ElectricWire>()

    /** Cleared on invalidate so a neighbor's refresh never re-bridges to a rack being unloaded/removed. */
    private var bridgeable = true

    val entities: List<DinRackEntityPlacement>
        get() = mEntities ?: emptyList()

    val shape: DirectionalVoxelShape
        get() = shapeCache ?: buildShape().also { shapeCache = it }

    private val terminals: Array<TerminalBoundingBox>
        get() = terminalCache ?: buildTerminals().also { terminalCache = it }

    fun invalidateInternal() {
        // The rebuild below recreates all external nodes (bus rails included), so bridge wires
        // must detach first and re-resolve after — ours here, the -u neighbor's via the poke.
        dropBusBridges()
        shapeCache = null
        terminalCache = null
        dropStaleClientConnections()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_ALL)
        electricBehaviour.rebuildCircuit(true)
        refreshBusBridges()
        neighborRack(plusU.opposite)?.refreshBusBridges()
    }

    /** World direction of increasing DIN unit; must agree with [DinRackBlock.hitToUnit]. */
    private val plusU: Direction
        get() = blockState.getValue(DinRackBlock.FACING).clockWise

    /** Bus rails sit right after the module terminals in the external node list. */
    private val busPlusIndex: Int
        get() = terminals.size

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

    override fun initialize() {
        super.initialize()
        refreshBusBridges()
        // A rack that appears (placed or chunk-loaded) is what the -u neighbor was waiting for.
        neighborRack(plusU.opposite)?.refreshBusBridges()
    }

    override fun remove() {
        // Detach cleanly before super lets ElectricBehaviour tear down the external nodes.
        dropBusBridges()
        super.remove()
    }

    /**
     * A client-side circuit rebuild must never reach [ElectricBehaviour]'s removed-endpoint
     * path — wire entities broadcast packets from it, which is server-only. The server has
     * already broken these connections via its own rebuild; mirror that locally.
     */
    private fun dropStaleClientConnections() {
        if (level?.isClientSide != true) return
        electricBehaviour.connections.keys.removeIf { it.terminal >= terminals.size }
    }

    fun canPlace(u: DINUnit, width: DINUnit): Boolean = canPlace(entities, u, width)

    private fun canPlace(placements: List<DinRackEntityPlacement>, u: DINUnit, width: DINUnit): Boolean {
        if (width.value <= 0) return false
        if (u.value < 0 || u.value + width.value > RACK_WIDTH) return false
        return placements.none { placed ->
            val start = placed.u.value
            val end = start + placed.entity.width.value
            u.value < end && u.value + width.value > start
        }
    }

    fun moduleAt(u: DINUnit): DinRackEntityPlacement? =
        entities.firstOrNull { it.u.value <= u.value && u.value < it.u.value + it.entity.width.value }

    /** Hands a module its [DinRackEntity.ModuleContext] so it can reach the world and its backing stack. */
    private fun attachModule(placement: DinRackEntityPlacement) {
        placement.entity.onAttach(ModuleContextImpl(placement.stack))
    }

    /** Server-side only. Returns false if the interval is occupied or out of bounds. */
    fun placeModule(u: DINUnit, entity: DinRackEntity, stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (!canPlace(u, entity.width)) return false
        val list = mEntities ?: return false
        val oldTerminals = globalTerminalMap(entities)
        val placement = DinRackEntityPlacement(u, entity, stack)
        list.add(placement)
        attachModule(placement)
        remapConnections(oldTerminals)
        setChanged()
        invalidateInternal()
        return true
    }

    /** Server-side only. Removes the module covering [u]; returns its placement or null. */
    fun removeModuleAt(u: DINUnit): DinRackEntityPlacement? {
        val placement = moduleAt(u) ?: return null
        val oldTerminals = globalTerminalMap(entities)
        if (mEntities?.remove(placement) != true) return null
        placement.entity.onDetach()
        remapConnections(oldTerminals)
        setChanged()
        invalidateInternal()
        return placement
    }

    override fun invalidate() {
        // Runs on both block break and chunk unload. On unload our block stays in the world, so
        // flag ourselves unbridgeable before poking the -u neighbor — its refresh must drop the
        // bridge into our about-to-be-removed nodes and not lay a new one.
        bridgeable = false
        dropBusBridges()
        neighborRack(plusU.opposite)?.refreshBusBridges()
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
        val newTerminals = globalTerminalMap(entities).entries.associate { (idx, key) -> key to idx }
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

    private fun globalTerminalMap(placements: List<DinRackEntityPlacement>): Map<Int, TerminalKey> {
        val map = HashMap<Int, TerminalKey>()
        var off = 0
        for (placed in placements) {
            for (local in placed.entity.terminalBoundingBox.indices) {
                map[off + local] = TerminalKey(placed.u.value, local)
            }
            off += placed.entity.terminalBoundingBox.size
        }
        return map
    }

    private fun buildShape(): DirectionalVoxelShape {
        val combined = entities.fold(BASE_SHAPE) { acc, placed ->
            Shapes.join(acc, placed.entity.shape.move(placed.u.toDouble() / 16.0, 0.0, 0.0), BooleanOp.OR)
        }
        return DirectionalVoxelShape(combined.optimize())
    }

    private fun buildTerminals() = entities
        .filter { it.entity.terminalBoundingBox.isNotEmpty() }
        .flatMap { e -> e.entity.terminalBoundingBox.toList().map { it.offset(e.u.toDouble() / 16.0, 0.0, 0.0) } }
        .toTypedArray()

    override fun buildCircuit(cb: IElectricEntity.CircuitBuilder) {
        // The bus rails are the two external nodes after the module terminals. External nodes
        // survive internal-only rebuilds and chunk pause, so neighbor racks' bridge wires can
        // attach to them; creating them unconditionally keeps the node count — and thus Power
        // Grid's node state sync payload — identical on client and server regardless of which
        // neighbor chunks happen to be loaded. terminal()/terminalCount() still expose only the
        // module terminals, so players cannot wire to the rails directly.
        val moduleTerminals = terminals.size
        cb.setTerminalCount(moduleTerminals + 2)
        val busPlus = cb.terminalNode(moduleTerminals)
        val busMinus = cb.terminalNode(moduleTerminals + 1)
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

    private fun targetedModule(be: DinRackBlockEntity): DinRackEntityPlacement? {
        val hit = Minecraft.getInstance().hitResult as? BlockHitResult ?: return null
        if (hit.blockPos != be.blockPos) return null
        val u = DinRackBlock.hitToUnit(be.blockState, be.blockPos, hit)
        return be.moduleAt(u)
    }

    /** Client-side only (Create's goggle overlay); shows the targeted module's info. */
    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        val placement = targetedModule(this) ?: return false
        return placement.entity.addToGoggleTooltip(tooltip, isPlayerSneaking)
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        // Modules must be parsed before super.read: ElectricBehaviour.read rebuilds the
        // circuit on the client (its "Rebuild" sync flag) and must see the new module list.
        mEntities?.forEach { it.entity.onDetach() }
        mEntities = readModules(tag, registries, clientPacket)
        mEntities?.forEach(::attachModule)
        shapeCache = null
        terminalCache = null
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
            if (!canPlace(rebuilt, u, entity.width)) {
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
    }

    override fun terminalCount(): Int = terminals.size

    override fun terminal(
        state: BlockState,
        term: Int
    ): ITerminalPlacement? {
        if (term < 0 || term >= terminals.size) {
            return null
        }
        val direction = state.getValue(HorizontalDirectionalBlock.FACING)
        val t = terminals[term].rotateAroundY(direction.rotationYDegreesInv().toInt())
        return t
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

        internal val BASE_SHAPE = Stream.of(
            box(0.0, 8.0, 13.0, 16.0, 10.0, 16.0),
            box(0.0, 10.0, 13.0, 16.0, 11.0, 14.0),
            box(0.0, 7.0, 13.0, 16.0, 8.0, 14.0)
        ).reduce { v1: VoxelShape, v2: VoxelShape -> Shapes.join(v1, v2, BooleanOp.OR) }.get().optimize()
    }
}