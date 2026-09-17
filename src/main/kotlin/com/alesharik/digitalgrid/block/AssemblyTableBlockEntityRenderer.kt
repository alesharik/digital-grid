package com.alesharik.digitalgrid.block

import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.utils.voxel.rotationYDegrees
import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer
import com.simibubi.create.foundation.render.RenderTypes
import dev.engine_room.flywheel.lib.transform.TransformStack
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.AABB

/**
 * Draws the whole Assembly Table model once, from the master cell. Non-master cells have
 * no block entity, so nothing else renders. The model is authored spanning 2 wide x 2 tall x 1
 * deep from the front-left-bottom (master) corner; it is rotated around the master cell centre
 * by [FACING], which maps the model's right/up extension onto the correct neighbour cells.
 */
class AssemblyTableBlockEntityRenderer : SafeBlockEntityRenderer<AssemblyTableBlockEntity>() {
    override fun renderSafe(
        be: AssemblyTableBlockEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val state = be.blockState
        val facing = state.getValue(AssemblyTableBlock.FACING)
        val stack = TransformStack.of(ms)
        stack.pushPose()
            .center()
            .rotateYDegrees(facing.rotationYDegrees())
            .rotateYDegrees(180f)
            .uncenter()
        CachedBuffers.partial(PartialModels.ASSEMBLY_TABLE, state)
            .light<SuperByteBuffer>(light)
            .renderInto(ms, bufferSource.getBuffer(RenderTypes.entitySolidBlockMipped()))
        stack.popPose()
    }

    override fun getRenderBoundingBox(be: AssemblyTableBlockEntity): AABB =
        // Model reaches ~2 cells beyond the master; inflate so it is not culled off-screen.
        AABB(be.blockPos).inflate(2.0)
}
