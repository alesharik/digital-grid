package com.alesharik.digitalgrid.din.rack

import com.alesharik.digitalgrid.DigitalgridRegistry
import com.simibubi.create.foundation.block.IBE
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.base.ElectricBlock

class DinRackBlock: ElectricBlock(
    Properties.of().dynamicShape()
), IBE<DinRackBlockEntity> {
    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block?, BlockState?>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        return this.defaultBlockState()
            .setValue(FACING, ctx.horizontalDirection.opposite)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        getBlockEntityOptional(level, pos)
            .map { it.shape.get(state) }
            .orElseGet { Shapes.empty() }

    override fun useItemOn(
        item: ItemStack,
        st: BlockState,
        lv: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): ItemInteractionResult {
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
    }

    override fun getBlockEntityClass(): Class<DinRackBlockEntity> = DinRackBlockEntity::class.java
    override fun getBlockEntityType(): BlockEntityType<out DinRackBlockEntity> = DigitalgridRegistry.BlockEntities.DIN_RACK

    companion object {
        val FACING: DirectionProperty = HorizontalDirectionalBlock.FACING
    }
}