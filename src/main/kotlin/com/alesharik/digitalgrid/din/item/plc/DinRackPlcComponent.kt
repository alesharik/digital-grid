package com.alesharik.digitalgrid.din.item.plc

import com.alesharik.digitalgrid.din.DinRackEntity
import com.mojang.blaze3d.vertex.PoseStack
import dan200.computercraft.api.peripheral.IPeripheral
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.state.BlockState

interface DinRackPlcComponent {
    fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {}

    fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {}

    /** Peripherals this component contributes to the PLC's internal modem bus, keyed by network name. */
    fun collectPeripherals(): Map<String, IPeripheral> = emptyMap()

    /** Client-side: goggle tooltip lines for this module. Return true if any were added. */
    fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean) {}

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