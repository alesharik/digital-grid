package com.alesharik.digitalgrid.din.item

import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.behavior.Behavior
import com.alesharik.digitalgrid.din.behavior.digibus.DigibusWireBehavior
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
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox
import java.util.stream.Stream

class DinRackDigibusCasingEntity: DinRackEntity {
    override val shape: VoxelShape = SHAPE
    override val terminalBoundingBox: Array<TerminalBoundingBox> = arrayOf()
    override val width: DINUnit = DINUnit(1)

    private val wireBehavior by lazy { DigibusWireBehavior() }
    override val behaviors: List<Behavior> = listOf(wireBehavior)

    override fun render(
        be: BlockState,
        en: DinRackEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val buffer = CachedBuffers.partial(PartialModels.DIN_CASING_DIGIBUS, be)
        buffer.light<SuperByteBuffer>(light)
            .renderInto(ms, bufferSource.getBuffer(RenderTypes.entitySolidBlockMipped()))
    }

    companion object {
        private val SHAPE = Stream.of(
            Block.box(0.0, 3.0, 14.0, 1.0, 8.0, 15.0),
            Block.box(0.0, 10.0, 14.0, 1.0, 15.0, 15.0),
            Block.box(0.0, 4.0, 13.0, 1.0, 7.0, 14.0),
            Block.box(0.0, 11.0, 13.0, 1.0, 14.0, 14.0),
            Block.box(0.0, 5.0, 11.0, 1.0, 13.0, 13.0)
        ).reduce { v1: VoxelShape, v2: VoxelShape -> Shapes.join(v1, v2, BooleanOp.OR) }.get().optimize()
    }
}