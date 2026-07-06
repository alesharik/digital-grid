package com.alesharik.digitalgrid.din.item

import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
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

class DinRackPlcRelayEntity: DinRackEntity {
    override val shape: VoxelShape = SHAPE
    override val terminalBoundingBox: Array<TerminalBoundingBox> = TERMINALS
    override val width: DINUnit = DINUnit(2)

    override fun render(
        be: BlockState,
        en: DinRackEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val buffer = CachedBuffers.partial(PartialModels.DIN_PLC_RELAY, be)
        buffer.light<SuperByteBuffer>(light)
            .renderInto(ms, bufferSource.getBuffer(RenderTypes.entitySolidBlockMipped()))
    }

    override fun buildCircuit(ctx: DinRackEntity.CircuitContext) {}

    companion object {
        private val SHAPE = Stream.of(
            Block.box(0.0, 3.0, 14.0, 2.0, 8.0, 15.0),
            Block.box(0.0, 10.0, 14.0, 2.0, 15.0, 15.0),
            Block.box(0.0, 4.0, 13.0, 2.0, 7.0, 14.0),
            Block.box(0.0, 11.0, 13.0, 2.0, 14.0, 14.0),
            Block.box(0.0, 4.0, 11.0, 2.0, 14.0, 13.0),
            Block.box(0.5, 14.0, 12.1, 1.5, 14.8, 12.9),
            Block.box(0.5, 3.2, 12.1, 1.5, 4.0, 12.9)
        ).reduce({ v1, v2 -> Shapes.join(v1, v2, BooleanOp.OR) }).get().optimize()

        private val TERMINALS = arrayOf<TerminalBoundingBox>(
            TerminalBoundingBox(IDecoratedTerminal.CONNECTOR, 0.5, 14.0, 12.1, 1.5, 14.8, 12.9)
                .withColor(IDecoratedTerminal.GRAY),
            TerminalBoundingBox(IDecoratedTerminal.CONNECTOR, 0.5, 3.2, 12.1, 1.5, 4.0, 12.9)
                .withColor(IDecoratedTerminal.GRAY),
        )
    }
}