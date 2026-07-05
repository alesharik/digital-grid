package com.alesharik.digitalgrid.din

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.base.IElectricEntity
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode

interface DinRackEntity {
    val shape: VoxelShape
    val terminalBoundingBox: Array<TerminalBoundingBox>
    val width: DINUnit

    fun render(
        be: BlockState,
        en: DinRackEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    )

    fun buildCircuit(ctx: CircuitContext) {}

    fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {}

    fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {}

    interface CircuitContext {
        val builder: IElectricEntity.CircuitBuilder

        fun terminalNode(idx: Int): FloatingNode
    }
}