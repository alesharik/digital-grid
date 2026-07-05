package com.alesharik.digitalgrid.din.rack

import com.alesharik.digitalgrid.Digitalgrid
import com.alesharik.digitalgrid.DigitalgridRegistry.BlockEntities
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.item.DinRackItem
import com.alesharik.digitalgrid.utils.voxel.DirectionalVoxelShape
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Block.box
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.base.*
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode
import java.util.stream.Stream

class DinRackBlockEntity(pos: BlockPos, state: BlockState): ElectricBlockEntity(BlockEntities.DIN_RACK, pos, state), IElectric {
    private var mEntities: MutableList<DinRackEntityPlacement>? = ArrayList()
    private var shapeCache: DirectionalVoxelShape? = null
    private var terminalCache: Array<TerminalBoundingBox>? = null

    val entities: List<DinRackEntityPlacement>
        get() = mEntities ?: emptyList()

    val shape: DirectionalVoxelShape
        get() = shapeCache ?: buildShape().also { shapeCache = it }

    private val terminals: Array<TerminalBoundingBox>
        get() = terminalCache ?: buildTerminals().also { terminalCache = it }

    fun invalidateInternal() {
        shapeCache = null
        terminalCache = null
        dropStaleClientConnections()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_ALL)
        electricBehaviour.rebuildCircuit(true)
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

    /** Server-side only. Returns false if the interval is occupied or out of bounds. */
    fun placeModule(u: DINUnit, entity: DinRackEntity, stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (!canPlace(u, entity.width)) return false
        (mEntities ?: return false).add(DinRackEntityPlacement(u, entity, stack))
        setChanged()
        invalidateInternal()
        return true
    }

    /** Server-side only. Removes the module covering [u]; returns its placement or null. */
    fun removeModuleAt(u: DINUnit): DinRackEntityPlacement? {
        val placement = moduleAt(u) ?: return null
        if (mEntities?.remove(placement) != true) return null
        setChanged()
        invalidateInternal()
        return placement
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
        cb.setTerminalCount(terminals.size)
        var off = 0
        for (en in entities) {
            en.entity.buildCircuit(CircuitContextImpl(
                off = off,
                terminalCount = en.entity.terminalBoundingBox.size,
                builder = cb,
            ))
            off += en.entity.terminalBoundingBox.size
        }
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        // Modules must be parsed before super.read: ElectricBehaviour.read rebuilds the
        // circuit on the client (its "Rebuild" sync flag) and must see the new module list.
        mEntities = readModules(tag, registries, clientPacket)
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
        val t = terminals[term]
        return t
    }

    data class DinRackEntityPlacement(
        val u: DINUnit,
        val entity: DinRackEntity,
        val stack: ItemStack,
    )

    class CircuitContextImpl(
        private val off: Int,
        private val terminalCount: Int,
        override val builder: IElectricEntity.CircuitBuilder
    ): DinRackEntity.CircuitContext {
        override fun terminalNode(idx: Int): FloatingNode {
            if (idx !in 0..<terminalCount) {
                throw IllegalArgumentException("Could not select terminal node $idx, max nodes are $terminalCount")
            }
            return builder.terminalNode(off)
        }
    }

    companion object {
        const val RACK_WIDTH = 16

        internal val BASE_SHAPE = Stream.of(
            box(0.0, 8.0, 13.0, 16.0, 10.0, 16.0),
            box(0.0, 10.0, 13.0, 16.0, 11.0, 14.0),
            box(0.0, 7.0, 13.0, 16.0, 8.0, 14.0)
        ).reduce { v1: VoxelShape, v2: VoxelShape -> Shapes.join(v1, v2, BooleanOp.OR) }.get().optimize()
    }
}