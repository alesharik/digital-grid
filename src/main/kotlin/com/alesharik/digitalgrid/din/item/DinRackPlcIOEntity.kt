package com.alesharik.digitalgrid.din.item

import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.utils.Lang
import com.alesharik.digitalgrid.utils.light.LightIndicator
import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.render.RenderTypes
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox
import java.util.stream.Stream

class DinRackPlcIOEntity: DinRackEntity {
    override val shape: VoxelShape = SHAPE
    override val terminalBoundingBox: Array<TerminalBoundingBox> = TERMINALS
    override val width: DINUnit = DINUnit(3)

    override fun render(
        be: BlockState,
        en: DinRackEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val buffer = CachedBuffers.partial(PartialModels.DIN_PLC_IO, be)
        buffer.light<SuperByteBuffer>(light)
            .renderInto(ms, bufferSource.getBuffer(RenderTypes.entitySolidBlockMipped()))
    }

    override fun buildCircuit(ctx: DinRackEntity.CircuitContext) {}

    companion object {
        private val SHAPE = Stream.of(
            Block.box(0.0, 3.0, 14.0, 3.0, 8.0, 15.0),
            Block.box(0.0, 10.0, 14.0, 3.0, 15.0, 15.0),
            Block.box(0.0, 4.0, 13.0, 3.0, 7.0, 14.0),
            Block.box(0.0, 11.0, 13.0, 3.0, 14.0, 14.0),
            Block.box(0.0, 4.0, 11.0, 3.0, 14.0, 13.0),
            Block.box(2.1, 14.0, 12.1, 2.9, 14.8, 12.9),
            Block.box(2.1, 3.2, 12.1, 2.9, 4.000000000000001, 12.9),
            Block.box(1.1, 14.0, 12.1, 1.9, 14.8, 12.9),
            Block.box(1.1, 3.2, 12.1, 1.9, 4.000000000000001, 12.9),
            Block.box(0.10000000000000009, 14.0, 12.1, 0.8999999999999999, 14.8, 12.9),
            Block.box(0.10000000000000009, 3.2, 12.1, 0.8999999999999999, 4.000000000000001, 12.9)
        ).reduce({ v1, v2 -> Shapes.join(v1, v2, BooleanOp.OR) }).get().optimize()

        private val TERMINALS = arrayOf<TerminalBoundingBox>(
            TerminalBoundingBox(Lang.text("1").component(), 2.1, 14.0, 12.1, 2.9, 14.8, 12.9)
                .withColor(LightIndicator.YELLOW),
            TerminalBoundingBox(Lang.text("3").component(), 2.1, 3.2, 12.1, 2.9, 4.000000000000001, 12.9)
                .withColor(LightIndicator.PURPLE),
            TerminalBoundingBox(IDecoratedTerminal.COMMON, 1.1, 14.0, 12.1, 1.9, 14.8, 12.9)
                .withColor(IDecoratedTerminal.BLUE),
            TerminalBoundingBox(IDecoratedTerminal.COMMON, 1.1, 3.2, 12.1, 1.9, 4.000000000000001, 12.9)
                .withColor(IDecoratedTerminal.BLUE),
            TerminalBoundingBox(Lang.text("2").component(), 0.10000000000000009, 14.0, 12.1, 0.8999999999999999, 14.8, 12.9)
                .withColor(LightIndicator.AQUAMARIN),
            TerminalBoundingBox(Lang.text("4").component(), 0.10000000000000009, 3.2, 12.1, 0.8999999999999999, 4.000000000000001, 12.9)
                .withColor(LightIndicator.ORANGE)
        )
    }
}