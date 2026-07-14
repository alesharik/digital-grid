package com.alesharik.digitalgrid.din.item

import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.behavior.Behavior
import com.alesharik.digitalgrid.din.behavior.digibus.DigibusPeripheralBehavior
import com.alesharik.digitalgrid.din.behavior.powergrid.RelayBehavior
import com.alesharik.digitalgrid.din.behavior.powergrid.WorkDrawBehavior
import com.alesharik.digitalgrid.infra.luaImpl
import com.alesharik.digitalgrid.utils.Lang
import com.alesharik.digitalgrid.utils.light.LightIndicator
import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.render.RenderTypes
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.peripheral.IPeripheral
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.ChatFormatting
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.chat.Component
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

    private val workDrawBehavior by lazy { WorkDrawBehavior.forBus(DigitalgridConfig.CONFIG.plcRelay.currentDraw) }
    private val relayBehavior by lazy {
        RelayBehavior.forBus(
            0,
            1,
            DigitalgridConfig.CONFIG.plcRelay.coilCurrent,
            DigitalgridConfig.CONFIG.plcRelay.minVoltage
        )
    }

    override val behaviors: List<Behavior> by lazy {
        arrayListOf(
            workDrawBehavior,
            relayBehavior,
            DigibusPeripheralBehavior(PlcRelayPeripheral(this))
        )
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
        val buffer = CachedBuffers.partial(PartialModels.DIN_PLC_RELAY, be)
        buffer.light<SuperByteBuffer>(light)
            .renderInto(ms, bufferSource.getBuffer(RenderTypes.entitySolidBlockMipped()))
        if (workDrawBehavior.powered) {
            (if (relayBehavior.closed) LIGHT_ON else LIGHT_OFF).render(be, ms, bufferSource)
        } else {
            LIGHT_NO_POWER.render(be, ms, bufferSource)
        }
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        Lang.builder().translate("goggles.plc_relay").style(ChatFormatting.GRAY).forGoggles(tooltip)
        Lang.builder().translate("goggles.state").style(ChatFormatting.GRAY)
            .space().add(
                if (relayBehavior.closed) Lang.translate("goggles.plc_relay.state.closed").style(ChatFormatting.GREEN)
                else Lang.translate("goggles.plc_relay.state.open").style(ChatFormatting.DARK_GRAY)
            )
            .forGoggles(tooltip, 1)
        Lang.builder().translate("goggles.plc_relay.coil").style(ChatFormatting.GRAY)
            .space().add(
                if (relayBehavior.commanded) Lang.translate("goggles.plc_relay.coil.on").style(ChatFormatting.GREEN)
                else Lang.translate("goggles.plc_relay.coil.off").style(ChatFormatting.DARK_GRAY)
            )
            .forGoggles(tooltip, 1)
        return true
    }

    class PlcRelayPeripheral(private val relay: DinRackPlcRelayEntity) : IPeripheral {
        override fun getType(): String = "plc_relay"

        /** Command the relay coil on or off. */
        @LuaFunction
        fun setState(on: Boolean) = luaImpl {
            ensurePowered()
            relay.relayBehavior.commanded = on
        }

        /** The commanded coil state. */
        @LuaFunction
        fun getState(): Boolean = luaImpl {
            ensurePowered()
            relay.relayBehavior.commanded
        }

        /** Whether the contact is actually closed (commanded on and the rail is powered). */
        @LuaFunction
        fun isClosed(): Boolean = luaImpl {
            ensurePowered()
            relay.relayBehavior.closed
        }

        private fun ensurePowered() {
            if (!relay.workDrawBehavior.powered) {
                throw LuaException("Device not powered")
            }
        }

        override fun equals(other: IPeripheral?): Boolean = other === this
    }

    companion object {
        /** Action light: program-controlled via the `plc` peripheral. */
        private val LIGHT_ON = LightIndicator(PartialModels.DIN_PLC_RELAY_LIGHT, LightIndicator.GREEN)
        private val LIGHT_OFF = LightIndicator(PartialModels.DIN_PLC_RELAY_LIGHT, LightIndicator.RED)
        private val LIGHT_NO_POWER = LightIndicator.off(PartialModels.DIN_PLC_RELAY_LIGHT)

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
