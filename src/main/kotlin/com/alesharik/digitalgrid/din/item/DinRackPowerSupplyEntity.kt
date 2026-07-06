package com.alesharik.digitalgrid.din.item

import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.utils.Lang
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
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode
import org.patryk3211.powergrid.electricity.sim.node.TransformerCoupling
import org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire
import thedarkcolour.kotlinforforge.neoforge.kotlin.enumMapOf
import java.util.stream.Stream
import kotlin.math.abs
import kotlin.math.sqrt

class DinRackPowerSupplyEntity: DinRackEntity {
    override val shape: VoxelShape = SHAPE
    override val terminalBoundingBox: Array<TerminalBoundingBox> = TERMINALS
    override val width: DINUnit = DINUnit(4)

    private var coupling: TransformerCoupling? = null
    private var diode: PNJunctionWire? = null
    private var input0: FloatingNode? = null
    private var input1: FloatingNode? = null
    private var outMid: FloatingNode? = null
    private var busPlus: FloatingNode? = null
    private var busMinus: FloatingNode? = null
    private var locked = true
    private var foldback = 1.0

    override fun buildCircuit(ctx: DinRackEntity.CircuitContext) {
        val out = ctx.builder.addInternalNode()
        // Secondary voltage = ratio * input voltage; ratio 0 keeps the output dead
        // until the first tick measures the input.
        coupling = ctx.builder.couple(
            0f, TRANSFORMER_RESISTANCE,
            ctx.terminalNode(0), ctx.terminalNode(1),
            out, ctx.busMinus,
        )
        // Power Grid's 1N4007 diode model; blocks any back-feed from the bus.
        val d = PNJunctionWire(5.47e-9, 0.075, 22.0, 1.783, out, ctx.bus24V)
        ctx.builder.add(d)
        diode = d
        input0 = ctx.terminalNode(0)
        input1 = ctx.terminalNode(1)
        outMid = out
        busPlus = ctx.bus24V
        busMinus = ctx.busMinus
        locked = true
        foldback = 1.0
    }

    override fun electricalTick(): DinRackEntity.TickResult {
        val coupling = coupling ?: return DinRackEntity.TickResult.NONE
        val vin = (input0?.voltage ?: return DinRackEntity.TickResult.NONE) -
                (input1?.voltage ?: return DinRackEntity.TickResult.NONE)
        if (!vin.isFinite()) return DinRackEntity.TickResult.NONE

        // Undervoltage lockout with hysteresis: no output and no input draw.
        val minInput = DigitalgridConfig.PSU_MIN_INPUT_VOLTAGE.get()
        locked = abs(vin) < if (locked) minInput else minInput * 0.9
        if (locked) {
            foldback = 1.0
            coupling.setRatio(0f)
            return DinRackEntity.TickResult.NONE
        }

        // Fold output voltage back when delivered power exceeds the configured
        // limit; averaged with the previous tick to avoid oscillation.
        var target = 1.0
        val delivered = deliveredPower()
        val maxPower = DigitalgridConfig.PSU_MAX_POWER.get()
        if (delivered.isFinite() && delivered > maxPower) {
            target = sqrt(maxPower / delivered)
        }
        foldback = (foldback + target) * 0.5

        // Signed input keeps the output positive regardless of input polarity.
        val ratio = (SETPOINT * foldback / vin).coerceIn(-MAX_RATIO, MAX_RATIO)
        coupling.setRatio(ratio.toFloat())
        return DinRackEntity.TickResult.NONE
    }

    private fun deliveredPower(): Double {
        val d = diode ?: return 0.0
        val plus = busPlus ?: return 0.0
        val minus = busMinus ?: return 0.0
        return d.current() * (plus.voltage - minus.voltage)
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
        val buffer = CachedBuffers.partial(PartialModels.DIN_POWER_SUPPLY, be)
        buffer.light<SuperByteBuffer>(light)
            .renderInto(ms, bufferSource.getBuffer(RenderTypes.entitySolidBlockMipped()))
        lights[state()]?.render(be, ms, bufferSource)
    }

    /**
     * Client-usable: the pre-diode output node rides Power Grid's node state sync,
     * so a raised output means the supply is producing on both sides.
     */
    private fun state(): State {
        val out = (outMid?.voltage ?: return State.NO_POWER) - (busMinus?.voltage ?: return State.NO_POWER)
        return if (out.isFinite() && out >= SETPOINT * 0.5) {
            State.WORKING
        } else {
            State.NO_POWER
        }
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        if (coupling == null) return false

        Lang.builder().translate("goggles.power_supply").style(ChatFormatting.GRAY).forGoggles(tooltip)
        Lang.builder().translate("goggles.state").style(ChatFormatting.GRAY)
            .space().add(state().text())
            .forGoggles(tooltip, 1)

        return true
    }

    private enum class State {
        WORKING,
        NO_POWER;

        fun text(): LangBuilder = when (this) {
            WORKING -> Lang.translate("goggles.power_supply.state.working").style(ChatFormatting.GREEN)
            NO_POWER -> Lang.translate("goggles.power_supply.state.no_power").style(ChatFormatting.DARK_GRAY)
        }
    }

    companion object {
        /** Pre-diode setpoint; the bus sees ~23.9-24.5V after the diode drop. */
        private const val SETPOINT = 24.8
        private const val TRANSFORMER_RESISTANCE = 0.1f
        private const val MAX_RATIO = 50.0

        private val lights = enumMapOf(
            State.NO_POWER to LightIndicator.off(PartialModels.DIN_POWER_SUPPLY_LIGHT),
            State.WORKING to LightIndicator.green(PartialModels.DIN_POWER_SUPPLY_LIGHT)
        )

        private val SHAPE = Stream.of(
            Block.box(0.0, 3.0, 14.0, 4.0, 8.0, 15.0),
            Block.box(0.0, 10.0, 14.0, 4.0, 15.0, 15.0),
            Block.box(0.0, 4.0, 13.0, 4.0, 7.0, 14.0),
            Block.box(0.0, 11.0, 13.0, 4.0, 14.0, 14.0),
            Block.box(0.0, 5.0, 12.0, 4.0, 13.0, 13.0),
            Block.box(0.0, 6.0, 11.0, 4.0, 12.0, 12.0),
            Block.box(1.0, 13.0, 12.0, 3.0, 14.0, 13.0),
            Block.box(1.0, 4.0, 12.0, 3.0, 5.0, 13.0)
        ).reduce({ v1, v2 -> Shapes.join(v1, v2, BooleanOp.OR) }).get().optimize()

        private val TERMINALS = arrayOf(
            TerminalBoundingBox(IDecoratedTerminal.POSITIVE, 1.0, 13.0, 12.0, 3.0, 14.0, 13.0)
                .withColor(IDecoratedTerminal.RED),
            TerminalBoundingBox(IDecoratedTerminal.NEGATIVE, 1.0, 4.0, 12.0, 3.0, 5.0, 13.0)
                .withColor(IDecoratedTerminal.BLUE)
        )
    }
}