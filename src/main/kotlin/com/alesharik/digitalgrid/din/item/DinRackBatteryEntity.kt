package com.alesharik.digitalgrid.din.item

import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.behavior.Behavior
import com.alesharik.digitalgrid.din.behavior.powergrid.BatteryBehavior
import com.alesharik.digitalgrid.infra.unit.watts
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
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox
import org.patryk3211.powergrid.utility.Unit
import thedarkcolour.kotlinforforge.neoforge.kotlin.enumMapOf
import java.util.stream.Stream
import kotlin.math.roundToInt

class DinRackBatteryEntity: DinRackEntity {
    override val shape: VoxelShape = SHAPE
    override val terminalBoundingBox: Array<TerminalBoundingBox> = emptyArray()
    override val width: DINUnit = DINUnit(4)

    private val batteryBehavior by lazy {
        BatteryBehavior(
            capacity = DigitalgridConfig.CONFIG.battery.capacity.joules,
            emfEmpty = DigitalgridConfig.CONFIG.battery.emfEmpty,
            emfSpan = DigitalgridConfig.CONFIG.battery.emfSpan,
            internalResistance = DigitalgridConfig.CONFIG.battery.internalResistance,
            depletedResistance = DigitalgridConfig.CONFIG.battery.depletedResistance,
        )
    }

    override val behaviors: List<Behavior> by lazy {
        listOf(
            batteryBehavior
        )
    }

    private fun state(): State? {
        val power = batteryBehavior.power ?: return null
        val energy = batteryBehavior.energy
        return when {
            energy <= 0.0 -> State.EMPTY
            power < (-0.05).watts -> State.CHARGING
            energy < batteryBehavior.capacity * LOW_CHARGE_LEVEL -> State.LOW
            power > 0.05.watts -> State.DISCHARGING
            else -> State.CHARGED
        }
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        val state = state() ?: return false
        val voltage = batteryBehavior.voltage ?: return false

        Lang.translate("goggles.battery").style(ChatFormatting.GRAY).forGoggles(tooltip)
        Lang.translate("goggles.state").style(ChatFormatting.GRAY)
            .space().add(state.text())
            .forGoggles(tooltip, 1)

        Lang.translate("goggles.voltage").style(ChatFormatting.GRAY)
            .space().add(Lang.text(String.format("%.1f", voltage.value)).space().add(Unit.VOLTAGE.get()).style(ChatFormatting.AQUA))
            .forGoggles(tooltip, 1)

        val percent = (batteryBehavior.energy / batteryBehavior.capacity * 100.0).roundToInt()
        Lang.translate("goggles.charge").style(ChatFormatting.GRAY)
            .space().add(Lang.text("$percent%").style(ChatFormatting.AQUA))
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