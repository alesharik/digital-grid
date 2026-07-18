package com.alesharik.digitalgrid.din

import com.alesharik.digitalgrid.din.behavior.Behavior
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox

interface DinRackEntity {
    val shape: VoxelShape
    val terminalBoundingBox: Array<TerminalBoundingBox>
    val width: DINUnit

    val behaviors: List<Behavior>

    fun render(
        be: BlockState,
        en: DinRackEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    )

    /**
     * Right-click on this module. For a module spanning onto the neighbor rack, clicks on the
     * overhang are delegated: [pos], [st] and [hit] then describe the clicked (neighbor) rack.
     */
    fun useItemOn(
        item: ItemStack,
        st: BlockState,
        lv: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): ItemInteractionResult = ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION

    /** Client-side: goggle tooltip lines for this module. Return true if any were added. */
    fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean = false

    /**
     * Empty-hand click on this module (delegated from the block). Default is pass,
     * but modules can override (e.g., drive eject).
     */
    fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult = InteractionResult.PASS
}