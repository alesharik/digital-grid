package com.alesharik.digitalgrid.utils.light

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.render.RenderTypes
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.level.block.state.BlockState

class LightIndicator(
    private val model: PartialModel,
    private val color: Int,
    private val blinker: Blinker = Blinker.NO_BLINK
) {
    fun render(be: BlockState, ms: PoseStack, bufferSource: MultiBufferSource) {
        val color = if (blinker.isOn()) color else BLACK
        CachedBuffers.partial(model, be)
            .disableDiffuse<SuperByteBuffer>()
            .color<SuperByteBuffer>(color)
            .light<SuperByteBuffer>(LightTexture.FULL_BRIGHT)
            .renderInto(ms, bufferSource.getBuffer(RenderTypes.additive()))
    }

    companion object {
        const val GREEN = 0x30E050
        const val RED = 0x981d1d
        const val BLACK = 0x807676

        fun off(model: PartialModel) = LightIndicator(model, BLACK, Blinker.NO_BLINK)

        fun green(model: PartialModel, blinker: Blinker = Blinker.NO_BLINK): LightIndicator = LightIndicator(model, GREEN, blinker)

        fun red(model: PartialModel, blinker: Blinker = Blinker.NO_BLINK): LightIndicator = LightIndicator(model, RED, blinker)
    }
}