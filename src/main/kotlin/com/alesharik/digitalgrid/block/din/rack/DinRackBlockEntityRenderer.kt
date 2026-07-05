package com.alesharik.digitalgrid.block.din.rack

import com.alesharik.digitalgrid.utils.voxel.rotationYDegrees
import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer
import dev.engine_room.flywheel.lib.transform.TransformStack
import net.minecraft.client.renderer.MultiBufferSource

class DinRackBlockEntityRenderer: SafeBlockEntityRenderer<DinRackBlockEntity>() {
    override fun renderSafe(
        be: DinRackBlockEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val state = be.blockState
        val facing = state.getValue(DinRackBlock.FACING)
        val stack = TransformStack.of(ms)
        for (placed in be.entities) {
            stack.pushPose()
                .center()
                .rotateYDegrees(facing.rotationYDegrees())
                .uncenter()
                .translate(placed.u / 16f, 0 / 16f, 0f)
            placed.entity.render(be.blockState, placed.entity, partialTicks, ms, bufferSource, light, overlay)
            stack.popPose()
        }
    }
}