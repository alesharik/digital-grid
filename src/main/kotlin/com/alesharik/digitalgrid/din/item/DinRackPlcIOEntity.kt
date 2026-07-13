package com.alesharik.digitalgrid.din.item

import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.DigitalgridRegistry
import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.item.DinRackPlcIOEntity.Companion.TERMINALS
import com.alesharik.digitalgrid.din.item.plc.PlcBusConnector
import com.alesharik.digitalgrid.din.item.plc.PlcBusModule
import com.alesharik.digitalgrid.din.item.plc.PlcIOPeripheral
import com.alesharik.digitalgrid.utils.Lang
import com.alesharik.digitalgrid.utils.light.LightIndicator
import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.render.RenderTypes
import dan200.computercraft.shared.util.NonNegativeId
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.ChatFormatting
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox
import org.patryk3211.powergrid.electricity.sim.SwitchedWire
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode
import org.patryk3211.powergrid.electricity.sim.node.TransformerCoupling
import org.patryk3211.powergrid.utility.Unit
import java.util.stream.Stream
import kotlin.math.abs

class DinRackPlcIOEntity: DinRackEntity, PlcBusModule {
    override val shape: VoxelShape = SHAPE
    override val terminalBoundingBox: Array<TerminalBoundingBox> = TERMINALS
    override val width: DINUnit = DINUnit(3)

    private var context: DinRackEntity.ModuleContext? = null
    private var connector: PlcBusConnector? = null

    private var busPlus: FloatingNode? = null
    private var busMinus: FloatingNode? = null

    /** True while the rail can power the pin drivers; hysteresis like the PLC's lockout. */
    private var railPowered = false

    private val pins = Array(PIN_COUNT) { Pin() }

    /** Snapshot of pin commands persisted last, to request a save only when they change. */
    private val persisted = Array(PIN_COUNT) { PinCommand(false, 0.0) }

    override fun onAttach(ctx: DinRackEntity.ModuleContext) {
        context = ctx
    }

    override fun onDetach() {
        connector?.remove()
        connector = null
        context = null
    }

    override fun busConnector(): PlcBusConnector? {
        connector?.let { return it }
        val ctx = context ?: return null
        val level = ctx.level as? ServerLevel ?: return null
        // The id names this module's peripheral and travels on the item, so the name
        // follows the module when it is moved to another rack.
        val id = NonNegativeId.getOrCreate(
            level.server, ctx.stack, DigitalgridRegistry.DataComponents.MODULE_ID, ID_STORE
        )
        ctx.markChanged()
        return PlcBusConnector(ctx.blockEntity).also {
            it.setPeripherals(mapOf("plc_io_$id" to PlcIOPeripheral(this)))
            connector = it
        }
    }

    override fun buildCircuit(ctx: DinRackEntity.CircuitContext) {
        // Both COMMON terminals are the module's GND reference, tied to the rack's minus rail.
        ctx.builder.connect(COMMON_RESISTANCE, ctx.terminalNode(COMMON_TERMINALS[0]), ctx.busMinus)
        ctx.builder.connect(COMMON_RESISTANCE, ctx.terminalNode(COMMON_TERMINALS[1]), ctx.busMinus)
        // Module electronics as a fixed load across the rail, sized for the configured
        // quiescent current (R = V / I at nominal).
        val loadResistance = (NOMINAL_VOLTAGE / DigitalgridConfig.PLC_IO_CURRENT_DRAW.get()).toFloat()
        ctx.builder.connect(loadResistance, ctx.bus24V, ctx.busMinus)
        for ((i, pin) in pins.withIndex()) {
            // Rail-powered converter per pin: primary across the 24V rail, secondary
            // drives the pin through a switch. Open switch = high-impedance (read-only)
            // pin, so the converter never fights an externally applied voltage. The
            // first tick closes the switch for driven pins with a proper ratio.
            val out = ctx.builder.addInternalNode()
            pin.coupling = ctx.builder.couple(0f, DRIVER_RESISTANCE, ctx.bus24V, ctx.busMinus, out, ctx.busMinus)
            pin.switch = ctx.builder.connectSwitch(SWITCH_RESISTANCE, out, ctx.terminalNode(PIN_TERMINALS[i]), false)
            pin.node = ctx.terminalNode(PIN_TERMINALS[i])
        }
        busPlus = ctx.bus24V
        busMinus = ctx.busMinus
        railPowered = false
    }

    override fun electricalTick(): DinRackEntity.TickResult {
        val plus = busPlus ?: return DinRackEntity.TickResult.NONE
        val minus = busMinus ?: return DinRackEntity.TickResult.NONE
        val railV = plus.voltage - minus.voltage
        // The drivers need a live rail to convert from; hysteresis avoids flapping
        // when driving loads sag the rail near the threshold.
        railPowered = railV.isFinite() &&
                abs(railV) >= if (railPowered) MIN_RAIL_VOLTAGE * 0.9 else MIN_RAIL_VOLTAGE

        var changed = false
        for ((i, pin) in pins.withIndex()) {
            val v = (pin.node?.voltage ?: Double.NaN) - minus.voltage
            pin.measured = if (v.isFinite()) v else 0.0

            val driven = pin.driven
            val target = pin.target
            val driving = driven && railPowered
            pin.switch?.let { if (it.state != driving) it.state = driving }
            // Signed rail voltage keeps the output at +target regardless of rail polarity.
            val ratio = if (driving) (target / railV).coerceIn(-MAX_RATIO, MAX_RATIO) else 0.0
            pin.coupling?.setRatio(ratio.toFloat())

            if (persisted[i].driven != driven || persisted[i].target != target) {
                persisted[i] = PinCommand(driven, target)
                changed = true
            }
        }
        return if (changed) DinRackEntity.TickResult.SAVE else DinRackEntity.TickResult.NONE
    }

    // --- called from the computer thread via PlcIOPeripheral (pin fields are @Volatile) ---

    internal fun luaGetVoltage(pin: Int): Double = pins[pin].measured

    internal fun luaSetVoltage(pin: Int, volts: Double) {
        pins[pin].target = volts
        pins[pin].driven = true
    }

    internal fun luaClearVoltage(pin: Int) {
        pins[pin].driven = false
        pins[pin].target = 0.0
    }

    internal fun luaIsDriven(pin: Int): Boolean = pins[pin].driven

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        val driven = tag.getByteArray("PinDriven")
        val targets = tag.getLongArray("PinTargets")
        for (i in pins.indices) {
            pins[i].driven = driven.getOrNull(i)?.toInt() == 1
            pins[i].target = targets.getOrNull(i)?.let { Double.fromBits(it) } ?: 0.0
            persisted[i] = PinCommand(pins[i].driven, pins[i].target)
        }
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        tag.putByteArray("PinDriven", ByteArray(pins.size) { if (pins[it].driven) 1 else 0 })
        tag.putLongArray("PinTargets", LongArray(pins.size) { pins[it].target.toRawBits() })
    }

    override fun writeSync(buffer: FriendlyByteBuf) {
        var mask = 0
        for ((i, pin) in pins.withIndex()) if (pin.driven) mask = mask or (1 shl i)
        buffer.writeByte(mask)
    }

    override fun readSync(buffer: FriendlyByteBuf) {
        val mask = buffer.readByte().toInt()
        for ((i, pin) in pins.withIndex()) pin.driven = (mask and (1 shl i)) != 0
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
        val buffer = CachedBuffers.partial(PartialModels.DIN_PLC_IO, be)
        buffer.light<SuperByteBuffer>(light)
            .renderInto(ms, bufferSource.getBuffer(RenderTypes.entitySolidBlockMipped()))
        pins.forEachIndexed { index, pin ->
            LIGHTS[index][pin.driven]!!.render(be, ms, bufferSource)
        }
    }

    /** Client-side: pin voltages are computed from Power Grid's synced node states. */
    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        Lang.builder().translate("goggles.plc_io").style(ChatFormatting.GRAY).forGoggles(tooltip)
        val minus = busMinus ?: return true
        for ((i, pin) in pins.withIndex()) {
            val v = (pin.node?.voltage ?: Double.NaN) - minus.voltage
            val volts = if (v.isFinite()) v else 0.0
            val line = Lang.builder().translate("goggles.plc_io.pin", i + 1).style(ChatFormatting.GRAY)
                .space().add(Unit.VOLTAGE.formatWithPrefixes(volts).style(ChatFormatting.AQUA))
            if (pin.driven) {
                line.space().add(Lang.translate("goggles.plc_io.driven").style(ChatFormatting.GOLD))
            }
            line.forGoggles(tooltip, 1)
        }
        return true
    }

    /** Pin command/measurement state; command fields are set from the computer thread. */
    private class Pin {
        @Volatile var driven = false
        @Volatile var target = 0.0

        /** Last measured pin voltage vs GND, cached each tick for the computer thread. */
        @Volatile var measured = 0.0

        // Sim handles; server thread only.
        var coupling: TransformerCoupling? = null
        var switch: SwitchedWire? = null
        var node: FloatingNode? = null
    }

    private data class PinCommand(val driven: Boolean, val target: Double)

    companion object {
        const val PIN_COUNT = 4

        /** Terminal indices of pins 1..4 in [TERMINALS] order (top row: 1, 2 — bottom row: 3, 4). */
        private val PIN_TERMINALS = intArrayOf(0, 4, 1, 5)
        private val COMMON_TERMINALS = intArrayOf(2, 3)

        /** Key of the module-id sequence in CC's id store. */
        private const val ID_STORE = "digitalgrid_plc_io"

        /** Nominal bus voltage the configured current draw is sized against. */
        private const val NOMINAL_VOLTAGE = 24.0

        /** Minimum rail voltage for the pin drivers to produce output. */
        private const val MIN_RAIL_VOLTAGE = 18.0
        private const val MAX_RATIO = 10.0

        private const val COMMON_RESISTANCE = 0.01f
        private const val DRIVER_RESISTANCE = 0.1f
        private const val SWITCH_RESISTANCE = 0.01f

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
