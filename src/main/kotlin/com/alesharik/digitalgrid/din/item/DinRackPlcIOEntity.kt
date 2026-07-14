package com.alesharik.digitalgrid.din.item

import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.behavior.Behavior
import com.alesharik.digitalgrid.din.behavior.digibus.DigibusPeripheralBehavior
import com.alesharik.digitalgrid.din.behavior.powergrid.BusGroundWireBehavior
import com.alesharik.digitalgrid.din.behavior.powergrid.IONodeBehavior
import com.alesharik.digitalgrid.din.behavior.powergrid.WorkDrawBehavior
import com.alesharik.digitalgrid.din.item.DinRackPlcIOEntity.Companion.TERMINALS
import com.alesharik.digitalgrid.infra.luaImpl
import com.alesharik.digitalgrid.infra.unit.Volt
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
import org.patryk3211.powergrid.utility.Unit
import java.util.stream.Stream

class DinRackPlcIOEntity: DinRackEntity {
    override val shape: VoxelShape = SHAPE
    override val terminalBoundingBox: Array<TerminalBoundingBox> = TERMINALS
    override val width: DINUnit = DINUnit(3)

    private val workDrawBehavior by lazy { WorkDrawBehavior.forBus(DigitalgridConfig.CONFIG.plcIo.currentDraw) }
    private val ioBehaviors = Array(PIN_COUNT) {
        IONodeBehavior(PIN_TERMINALS[it])
    }

    override val behaviors: List<Behavior> by lazy {
        arrayListOf(
            workDrawBehavior,
            DigibusPeripheralBehavior(PlcIOPeripheral(this))
        ) + ioBehaviors + COMMON_TERMINALS.map { BusGroundWireBehavior(it) }
    }

    // --- called from the computer thread via PlcIOPeripheral (pin fields are @Volatile) ---

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
        ioBehaviors.forEachIndexed { index, pin ->
            LIGHTS[index][pin.driven]!!.render(be, ms, bufferSource)
        }
    }

    /** Client-side: pin voltages are computed from Power Grid's synced node states. */
    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        Lang.builder().translate("goggles.plc_io").style(ChatFormatting.GRAY).forGoggles(tooltip)
        for ((i, pin) in ioBehaviors.withIndex()) {
            val v = pin.measured
            val line = Lang.builder().translate("goggles.plc_io.pin", i + 1).style(ChatFormatting.GRAY)
                .space().add(Unit.VOLTAGE.formatWithPrefixes(v.value.toDouble()).style(ChatFormatting.AQUA))
            if (pin.driven) {
                line.space().add(Lang.translate("goggles.plc_io.driven").style(ChatFormatting.GOLD))
            }
            line.forGoggles(tooltip, 1)
        }
        return true
    }

    /** Lua pins are 1-based; [DinRackPlcIOEntity] uses 0-based indices internally. */
    inner class PlcIOPeripheral(private val io: DinRackPlcIOEntity) : IPeripheral {
        override fun getType(): String = "plc_io"

        /** Measured pin voltage relative to GND (the COMMON terminals), in volts. */
        @LuaFunction
        fun getVoltage(pin: Int): Float = luaImpl {
            ensurePowered()
            io.ioBehaviors[checkPin(pin)].measured.value
        }

        /** Drive the pin to [volts], converted from the rack's 24V rail. Reading still works. */
        @LuaFunction
        fun setVoltage(pin: Int, volts: Float) = luaImpl {
            ensurePowered()
            val volts = Volt(volts)
            if (volts > DigitalgridConfig.CONFIG.plcIo.maxVoltage) {
                throw LuaException("Voltage out of range (0..${DigitalgridConfig.CONFIG.plcIo.maxVoltage})")
            }
            io.ioBehaviors[checkPin(pin)].setVoltage(volts)
        }

        /** Stop driving the pin (high impedance). */
        @LuaFunction
        fun clearVoltage(pin: Int) = luaImpl {
            ensurePowered()
            io.ioBehaviors[checkPin(pin)].clearVoltage()
        }

        @LuaFunction
        fun isDriven(pin: Int): Boolean = luaImpl {
            ensurePowered()
            io.ioBehaviors[checkPin(pin)].driven
        }

        private fun ensurePowered() {
            if (!workDrawBehavior.powered) {
                throw LuaException("Device not powered")
            }
        }

        private fun checkPin(pin: Int): Int {
            if (pin !in 1..PIN_COUNT) {
                throw LuaException("Invalid pin $pin (1..${PIN_COUNT})")
            }
            return pin - 1
        }

        override fun equals(other: IPeripheral?): Boolean = other === this
    }

    companion object {
        const val PIN_COUNT = 4

        /** Terminal indices of pins 1..4 in [TERMINALS] order (top row: 1, 2 — bottom row: 3, 4). */
        private val PIN_TERMINALS = intArrayOf(0, 4, 1, 5)
        private val COMMON_TERMINALS = intArrayOf(2, 3)

        private val LIGHTS_COLORS = arrayOf(LightIndicator.YELLOW, LightIndicator.AQUAMARIN, LightIndicator.PURPLE, LightIndicator.ORANGE)
        private val LIGHTS = PartialModels.DIN_PLC_IO_LIGHTS.mapIndexed { idx, model ->
            mapOf(
                true to LightIndicator.colored(model, LIGHTS_COLORS[idx]),
                false to LightIndicator.off(model)
            )
        }

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
