package com.alesharik.digitalgrid.block.din

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.base.IElectricEntity
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox

interface DinRackEntity {
    val shape: VoxelShape
    val terminalBoundingBox: Array<TerminalBoundingBox>

    fun render(
        be: BlockState,
        en: DinRackEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    )

    fun buildCircuit(cb: IElectricEntity.CircuitBuilder, off: Int)
}