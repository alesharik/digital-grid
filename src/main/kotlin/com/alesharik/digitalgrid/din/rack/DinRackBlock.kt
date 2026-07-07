package com.alesharik.digitalgrid.din.rack

import com.alesharik.digitalgrid.DigitalgridRegistry
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.item.DinRackItem
import com.alesharik.digitalgrid.din.item.plc.DinRackPlcEntity
import com.simibubi.create.foundation.block.IBE
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
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
        // PLC terminal: right-click a PLC module to open its computer's terminal GUI.
        if (item.item == DigitalgridRegistry.Items.PLC_TERMINAL) {
            val module = getBlockEntityOptional(lv, pos).orElse(null)?.moduleAt(hitToUnit(st, pos, hit))?.entity
            com.alesharik.digitalgrid.Digitalgrid.LOGGER.info(
                "[PLC] terminal branch: client={} unit={} module={}",
                lv.isClientSide, hitToUnit(st, pos, hit).value, module?.javaClass?.simpleName
            )
            val plc = module as? DinRackPlcEntity
                ?: return super.useItemOn(item, st, lv, pos, player, hand, hit)
            if (lv.isClientSide) return ItemInteractionResult.sidedSuccess(true)
            plc.openTerminal(player as ServerPlayer)
            return ItemInteractionResult.sidedSuccess(false)
        }
        val dinItem = item.item as? DinRackItem
            ?: return super.useItemOn(item, st, lv, pos, player, hand, hit)
        val be = getBlockEntityOptional(lv, pos).orElse(null)
            ?: return ItemInteractionResult.FAIL
        val u = hitToUnit(st, pos, hit)
        val entity = dinItem.createEntity()
        if (!be.canPlace(u, entity.width)) return ItemInteractionResult.FAIL
        if (lv.isClientSide) return ItemInteractionResult.sidedSuccess(true)
        val stored = item.copyWithCount(1)
        if (!be.placeModule(u, entity, stored)) return ItemInteractionResult.FAIL
        if (!player.hasInfiniteMaterials()) item.shrink(1)
        return ItemInteractionResult.sidedSuccess(false)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult {
        if (player.isSecondaryUseActive && player.mainHandItem.isEmpty) {
            val be = getBlockEntityOptional(level, pos).orElse(null)
                ?: return super.useWithoutItem(state, level, pos, player, hit)
            val u = hitToUnit(state, pos, hit)
            if (be.moduleAt(u) == null) return super.useWithoutItem(state, level, pos, player, hit)
            if (level.isClientSide) return InteractionResult.sidedSuccess(true)
            val removed = be.removeModuleAt(u) ?: return InteractionResult.FAIL
            player.inventory.placeItemBackInInventory(removed.stack)
            return InteractionResult.sidedSuccess(false)
        }
        return super.useWithoutItem(state, level, pos, player, hit)
    }

    override fun getBlockEntityClass(): Class<DinRackBlockEntity> = DinRackBlockEntity::class.java
    override fun getBlockEntityType(): BlockEntityType<out DinRackBlockEntity> = DigitalgridRegistry.BlockEntities.DIN_RACK

    companion object {
        val FACING: DirectionProperty = HorizontalDirectionalBlock.FACING

        fun hitToUnit(state: BlockState, pos: BlockPos, hit: BlockHitResult): DINUnit {
            val localX = hit.location.x - pos.x
            val localZ = hit.location.z - pos.z
            val northX = when (state.getValue(FACING)) {
                Direction.EAST -> localZ
                Direction.SOUTH -> 1.0 - localX
                Direction.WEST -> 1.0 - localZ
                else -> localX
            }
            return DINUnit(Mth.floor(northX * 16.0).coerceIn(0, 15))
        }
    }
}