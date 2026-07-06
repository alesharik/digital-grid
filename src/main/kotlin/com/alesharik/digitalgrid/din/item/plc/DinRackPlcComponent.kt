package com.alesharik.digitalgrid.din.item.plc

import com.alesharik.digitalgrid.din.DinRackEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.state.BlockState

interface DinRackPlcComponent {
    fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {}

    fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {}

    /** Client-side: goggle tooltip lines for this module. Return true if any were added. */
    fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean = false

    fun render(
        be: BlockState,
        en: DinRackEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {}
}