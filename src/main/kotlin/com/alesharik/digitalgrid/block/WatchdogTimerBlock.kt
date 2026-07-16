package com.alesharik.digitalgrid.block

import com.alesharik.digitalgrid.DigitalgridRegistry
import com.alesharik.digitalgrid.utils.voxel.DirectionalVoxelShape
import net.minecraft.core.BlockPos
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape


class WatchdogTimerBlock(props: Properties): Block(props), EntityBlock {
    override fun newBlockEntity(p0: BlockPos, p1: BlockState): BlockEntity? =
        DigitalgridRegistry.BlockEntities.WATCHDOG_TIMER.create(p0, p1)

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(BlockStateProperties.FACING)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(BlockStateProperties.FACING, ctx.clickedFace.opposite)

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = SHAPE.get(state)

    @Suppress("UNCHECKED_CAST")
    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        return if (blockEntityType == DigitalgridRegistry.BlockEntities.WATCHDOG_TIMER) {
            BlockEntityTicker<WatchdogTimerBlockEntity> { p0, p1, p2, p3 -> p3.tick(p0, p1, p2) } as BlockEntityTicker<T>?
        } else null
    }
    companion object {
        private val SHAPE = DirectionalVoxelShape(
            Shapes.join(
                box(1.0, 1.0, 0.0, 15.0, 15.0, 2.0),
                box(9.0, 2.0, 2.0, 14.0, 14.0, 3.0),
                BooleanOp.OR).optimize()
        )
    }
}