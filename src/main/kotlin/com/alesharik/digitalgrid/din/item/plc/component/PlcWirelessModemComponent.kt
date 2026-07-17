package com.alesharik.digitalgrid.din.item.plc.component

import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.behavior.Behavior
import com.alesharik.digitalgrid.din.item.plc.DinRackPlcComponent
import com.alesharik.digitalgrid.utils.Lang
import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.render.RenderTypes
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.shared.peripheral.modem.ModemState
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessModemPeripheral
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.ChatFormatting
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

class PlcWirelessModemComponent(private val advanced: Boolean) : DinRackPlcComponent {
    // Cached context, resolved lazily in the peripheral: onAttach may run during chunk-load
    // deserialization before the rack's level is set, so ctx.level must not be read here.
    // Written on the server thread (attach/re-attach), read from the CC computer thread.
    @Volatile
    private var context: Behavior.AttachContext? = null
    private val peripheral = Peripheral()

    override fun onAttach(ctx: Behavior.AttachContext) {
        context = ctx
    }

    override fun onDetach() {
        // Deliberately do NOT null out context here — the computer thread may still touch the
        // peripheral while the computer is closing down after removal.
        peripheral.removed()
    }

    override fun getPeripheral(): IPeripheral = peripheral

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean) {
        val key = if (advanced) "goggles.plc_component.ender_modem" else "goggles.plc_component.wireless_modem"
        Lang.translate(key).style(ChatFormatting.GRAY).forGoggles(tooltip, 1)
    }

    override fun render(
        be: BlockState,
        en: DinRackEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        CachedBuffers.partial(if (advanced) PartialModels.DIN_PLC_ENDER_MODEM else PartialModels.DIN_PLC_WIRELESS_MODEM, be)
            .light<SuperByteBuffer>(light)
            .renderInto(ms, bufferSource.getBuffer(RenderTypes.entitySolidBlockMipped()))
    }

    private inner class Peripheral : WirelessModemPeripheral(ModemState(), advanced) {
        // The error path is only reachable pre-attach — the computer receives this peripheral
        // strictly after onAttach, and only once the rack is live (ensureComputer in electricalTick).
        override fun getLevel(): Level =
            context?.level ?: error("PLC modem peripheral used before attach")

        override fun getPosition(): Vec3 =
            Vec3.atCenterOf(context?.pos ?: error("PLC modem peripheral used before attach"))

        override fun equals(other: IPeripheral?): Boolean = other === this
    }
}
