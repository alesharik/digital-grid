package com.alesharik.digitalgrid.block

import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.utils.Tick
import com.alesharik.digitalgrid.utils.light.Blinker
import com.alesharik.digitalgrid.utils.light.LightIndicator
import com.alesharik.digitalgrid.utils.voxel.rotationXDegrees
import com.alesharik.digitalgrid.utils.voxel.rotationYDegrees
import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer
import dev.engine_room.flywheel.lib.transform.TransformStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties

class WatchdogTimerBlockEntityRenderer : SafeBlockEntityRenderer<WatchdogTimerBlockEntity>() {
    override fun renderSafe(
        be: WatchdogTimerBlockEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val state = be.blockState
        val facing = state.getValue(BlockStateProperties.FACING)
        val stack = TransformStack.of(ms)
        stack.pushPose()
            .center()
            .rotateYDegrees(facing.rotationYDegrees())
            .rotateXDegrees(facing.rotationXDegrees())
            .uncenter()

        if (!be.enabled) {
            LIGHT_WORK_OFF.render(state, ms, bufferSource)
            LIGHT_ACTIVITY_OFF.render(state, ms, bufferSource)
        } else {
            handleWorkLight(be, state, ms, bufferSource)

            handleActivityLight(be, state, ms, bufferSource)
        }

        stack.popPose()
    }

    private fun handleActivityLight(
        be: WatchdogTimerBlockEntity,
        state: BlockState,
        ms: PoseStack,
        bufferSource: MultiBufferSource
    ) {
        if (be.blinkTicks > 0) {
            LIGHT_ACTIVITY_ON.render(state, ms, bufferSource)
            be.blinkTicks--
        } else {
            if (be.timer < be.lastKnownClientTimer) {
                LIGHT_ACTIVITY_ON.render(state, ms, bufferSource)
                be.blinkTicks = BLINK_DELAY
            } else {
                LIGHT_ACTIVITY_OFF.render(state, ms, bufferSource)
            }
            be.lastKnownClientTimer = be.timer
        }
    }

    private fun handleWorkLight(
        be: WatchdogTimerBlockEntity,
        state: BlockState,
        ms: PoseStack,
        bufferSource: MultiBufferSource
    ) {
        if (be.timer / be.timeLimit > NEARLY_TRIGGER_PERCENT) {
            LIGHT_WORK_NEARLY_TRIGGER.render(state, ms, bufferSource)
        } else {
            LIGHT_WORK_ON.render(state, ms, bufferSource)
        }
    }

    companion object {
        private const val NEARLY_TRIGGER_PERCENT = 0.9
        private val BLINK_DELAY = Tick.fromSeconds(0.5f)

        private val LIGHT_WORK_OFF = LightIndicator.off(PartialModels.WATCHDOG_TIMER_LIGHT_WORK)
        private val LIGHT_WORK_ON = LightIndicator.green(PartialModels.WATCHDOG_TIMER_LIGHT_WORK)
        private val LIGHT_WORK_NEARLY_TRIGGER =
            LightIndicator.green(PartialModels.WATCHDOG_TIMER_LIGHT_WORK, blinker = Blinker.HALF_SECOND)

        private val LIGHT_ACTIVITY_OFF = LightIndicator.off(PartialModels.WATCHDOG_TIMER_LIGHT_ACTIVITY)
        private val LIGHT_ACTIVITY_ON = LightIndicator.colored(
            PartialModels.WATCHDOG_TIMER_LIGHT_ACTIVITY,
            LightIndicator.YELLOW
        )
    }
}