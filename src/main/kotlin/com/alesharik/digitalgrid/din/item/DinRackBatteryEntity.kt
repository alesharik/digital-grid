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
import org.patryk3211.powergrid.electricity.base.IElectricEntity
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox
import java.util.stream.Stream

class DinRackBatteryEntity: DinRackEntity {
    override val shape: VoxelShape = SHAPE
    override val terminalBoundingBox: Array<TerminalBoundingBox> = emptyArray()
    override val width: DINUnit = DINUnit(4)

    override fun render(
        be: BlockState,
        en: DinRackEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val buffer = CachedBuffers.partial(PartialModels.DIN_BATTERY, be)
        buffer.light<SuperByteBuffer>(light)
            .renderInto(ms, bufferSource.getBuffer(RenderTypes.entitySolidBlockMipped()))
    }

    override fun buildCircuit(cb: IElectricEntity.CircuitBuilder, off: Int) {}

    companion object {
        private val SHAPE = Stream.of(
            Block.box(0.0, 3.0, 14.0, 4.0, 8.0, 15.0),
            Block.box(0.0, 10.0, 14.0, 4.0, 15.0, 15.0),
            Block.box(0.0, 4.0, 13.0, 4.0, 7.0, 14.0),
            Block.box(0.0, 11.0, 13.0, 4.0, 14.0, 14.0),
            Block.box(0.0, 5.0, 12.0, 4.0, 13.0, 13.0),
            Block.box(0.0, 13.0, 12.0, 4.0, 14.0, 13.0),
            Block.box(0.0, 4.0, 12.0, 4.0, 5.0, 13.0),
            Block.box(0.0, 5.0, 11.0, 1.0, 13.0, 12.0),
            Block.box(3.0, 5.0, 11.0, 4.0, 13.0, 12.0),
            Block.box(1.0, 12.0, 11.0, 3.0, 13.0, 12.0),
            Block.box(1.0, 5.0, 11.0, 3.0, 8.0, 12.0),
            Block.box(1.2, 8.2, 11.0, 2.6999999999999997, 11.7, 11.5)
        ).reduce({ v1, v2 -> Shapes.join(v1, v2, BooleanOp.OR) }).get().optimize()
    }
}