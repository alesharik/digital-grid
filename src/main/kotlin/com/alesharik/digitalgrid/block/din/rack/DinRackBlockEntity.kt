package com.alesharik.digitalgrid.block.din.rack

import com.alesharik.digitalgrid.DigitalgridRegistry.BlockEntities
import com.alesharik.digitalgrid.block.din.DinRackEntity
import com.alesharik.digitalgrid.block.din.item.contactor.DinRackPatchEntity
import com.alesharik.digitalgrid.utils.voxel.DirectionalVoxelShape
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Block.box
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.base.*
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

    init {
        mEntities = buildList {
            add(DinRackEntityPlacement(
                u = 0,
                entity = DinRackPatchEntity()
            ))
            add(DinRackEntityPlacement(
                u = 5,
                entity = DinRackPatchEntity()
            ))
        }.toMutableList()
    }

    fun invalidateInternal() {
        shapeCache = null
        terminalCache = null
        level?.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_ALL)
        electricBehaviour.rebuildCircuit(true)
    }

    private fun buildShape(): DirectionalVoxelShape {
        val combined = entities.fold(BASE_SHAPE) { acc, placed ->
            Shapes.join(acc, placed.entity.shape.move(placed.u / 16.0, 0.0, 0.0), BooleanOp.OR)
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
            en.entity.buildCircuit(cb, off)
            off += en.entity.terminalBoundingBox.size
        }
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.read(tag, registries, clientPacket)
        invalidateInternal()
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
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
        val u: Int,
        val entity: DinRackEntity,
    )

    companion object {
        internal val BASE_SHAPE = Stream.of(
            box(0.0, 8.0, 13.0, 16.0, 10.0, 16.0),
            box(0.0, 10.0, 13.0, 16.0, 11.0, 14.0),
            box(0.0, 7.0, 13.0, 16.0, 8.0, 14.0)
        ).reduce { v1: VoxelShape, v2: VoxelShape -> Shapes.join(v1, v2, BooleanOp.OR) }.get().optimize()
    }
}