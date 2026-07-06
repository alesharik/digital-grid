package com.alesharik.digitalgrid.din.item

import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.utils.Lang
import com.alesharik.digitalgrid.utils.light.Blinker
import com.alesharik.digitalgrid.utils.light.LightIndicator
import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.render.RenderTypes
import net.createmod.catnip.lang.LangBuilder
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.ChatFormatting
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling
import org.patryk3211.powergrid.utility.Unit
import thedarkcolour.kotlinforforge.neoforge.kotlin.enumMapOf
import java.util.stream.Stream
import kotlin.math.abs
import kotlin.math.roundToInt

class DinRackBatteryEntity: DinRackEntity {
    override val shape: VoxelShape = SHAPE
    override val terminalBoundingBox: Array<TerminalBoundingBox> = emptyArray()
    override val width: DINUnit = DINUnit(4)

    private var coupling: VoltageSourceCoupling? = null
    private var energy = 0.0

    private val capacity: Double
        get() = DigitalgridConfig.BATTERY_CAPACITY_WH.get() * 3600.0

    override fun buildCircuit(ctx: DinRackEntity.CircuitContext) {
        val source = VoltageSourceCoupling(ctx.bus24V, ctx.busMinus, INTERNAL_RESISTANCE)
        ctx.builder.add(source)
        coupling = source
        updateParameters(source)
    }

    override fun electricalTick(): DinRackEntity.TickResult {
        val source = coupling ?: return DinRackEntity.TickResult.NONE
        // Sign convention as in Power Grid's battery: positive power discharges.
        val power = -source.current * source.voltage
        if (!power.isFinite()) return DinRackEntity.TickResult.NONE
        energy = (energy - power * 0.05).coerceIn(0.0, capacity)
        updateParameters(source)
        // Clients receive energy through the rack's sync appender (rides Power
        // Grid's periodic node state sync), so no explicit block entity sync here.
        return if (abs(power) > 0.05) DinRackEntity.TickResult.SAVE else DinRackEntity.TickResult.NONE
    }

    private fun state(): State? {
        val source = coupling ?: return null
        val power = -source.current * source.voltage
        if (!power.isFinite()) return null
        return when {
            energy <= 0.0 -> State.EMPTY
            power < -0.05 -> State.CHARGING
            energy < capacity * LOW_CHARGE_LEVEL -> State.LOW
            power > 0.05 -> State.DISCHARGING
            else -> State.CHARGED
        }
    }

    private fun updateParameters(source: VoltageSourceCoupling) {
        val chargeLevel = (energy / capacity).coerceIn(0.0, 1.0)
        source.setVoltage(EMF_EMPTY + EMF_SPAN * chargeLevel)
        // An empty battery must not source power, but has to stay revivable:
        // block only the discharge direction and reopen as soon as current reverses.
        val discharging = -source.current * source.voltage > 0
        source.setResistance(if (energy <= 0.0 && discharging) DEPLETED_RESISTANCE else INTERNAL_RESISTANCE)
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        energy = tag.getDouble("Energy").coerceIn(0.0, capacity)
        coupling?.let { updateParameters(it) }
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        tag.putDouble("Energy", energy)
    }

    override fun writeSync(buffer: FriendlyByteBuf) {
        buffer.writeFloat(energy.toFloat())
    }

    override fun readSync(buffer: FriendlyByteBuf) {
        energy = buffer.readFloat().toDouble().coerceIn(0.0, capacity)
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        val source = coupling ?: return false
        val state = state() ?: return false

        Lang.builder().translate("goggles.battery").style(ChatFormatting.GRAY).forGoggles(tooltip)
        Lang.builder().translate("goggles.state").style(ChatFormatting.GRAY)
            .space().add(state.text())
            .forGoggles(tooltip, 1)

        val voltage = source.positive.voltage - (source.negative?.voltage ?: 0.0)
        Lang.builder().translate("goggles.voltage").style(ChatFormatting.GRAY)
            .space().add(Lang.builder().text(String.format("%.1f", voltage)).space().add(Unit.VOLTAGE.get()).style(ChatFormatting.AQUA))
            .forGoggles(tooltip, 1)

        val percent = (energy / capacity * 100.0).roundToInt()
        Lang.builder().translate("goggles.charge").style(ChatFormatting.GRAY)
            .space().add(Lang.builder().text("$percent%").style(ChatFormatting.AQUA))
            .forGoggles(tooltip, 1)
        return true
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
        val buffer = CachedBuffers.partial(PartialModels.DIN_BATTERY, be)
        buffer.light<SuperByteBuffer>(light)
            .renderInto(ms, bufferSource.getBuffer(RenderTypes.entitySolidBlockMipped()))
        state()?.let { lights[it] }?.render(be, ms, bufferSource)
    }

    /** Client-visible battery condition; derived from synced energy and coupling current. */
    private enum class State {
        EMPTY,
        CHARGING,
        LOW,
        DISCHARGING,
        CHARGED;

        fun text(): LangBuilder = when (this) {
            EMPTY -> Lang.translate("goggles.battery.state.empty").style(ChatFormatting.DARK_GRAY)
            CHARGING -> Lang.translate("goggles.battery.state.charging").style(ChatFormatting.GREEN)
            LOW -> Lang.translate("goggles.battery.state.low").style(ChatFormatting.RED)
            DISCHARGING -> Lang.translate("goggles.battery.state.discharging").style(ChatFormatting.GOLD)
            CHARGED -> Lang.translate("goggles.battery.state.charged").style(ChatFormatting.GREEN)
        }
    }

    companion object {
        /** EMF is 20V empty .. 24V full — full charge sits just under the PSU bus
         * setpoint so float charge tapers off instead of overcharging. */
        private const val EMF_EMPTY = 20.0
        private const val EMF_SPAN = 4.0
        private const val INTERNAL_RESISTANCE = 0.5f
        private const val DEPLETED_RESISTANCE = 10_000f
        private const val LOW_CHARGE_LEVEL = 0.2

        private val lights = enumMapOf(
            State.EMPTY to LightIndicator.off(PartialModels.DIN_BATTERY_LIGHT),
            State.CHARGING to LightIndicator.green(PartialModels.DIN_BATTERY_LIGHT, Blinker.HALF_SECOND),
            State.LOW to LightIndicator.red(PartialModels.DIN_BATTERY_LIGHT, Blinker.HALF_SECOND),
            State.DISCHARGING to LightIndicator.red(PartialModels.DIN_BATTERY_LIGHT),
            State.CHARGED to LightIndicator.green(PartialModels.DIN_BATTERY_LIGHT),
        )

        private val SHAPE = Stream.of(
            Block.box(0.0, 3.0, 14.0, 4.0, 8.0, 15.0),
            Block.box(0.0, 10.0, 14.0, 4.0, 15.0, 15.0),
            Block.box(0.0, 4.0, 13.0, 4.0, 7.0, 14.0),
            Block.box(0.0, 11.0, 13.0, 4.0, 14.0, 14.0),
            Block.box(0.0, 5.0, 12.0, 4.0, 13.0, 13.0),
            Block.box(0.0, 5.0, 11.0, 1.0, 13.0, 12.0),
            Block.box(3.0, 5.0, 11.0, 4.0, 13.0, 12.0),
            Block.box(1.0, 12.0, 11.0, 3.0, 13.0, 12.0),
            Block.box(1.0, 5.0, 11.0, 3.0, 8.0, 12.0),
            Block.box(1.2, 8.2, 11.0, 2.6999999999999997, 11.7, 11.5)
        ).reduce({ v1, v2 -> Shapes.join(v1, v2, BooleanOp.OR) }).get().optimize()
    }
}