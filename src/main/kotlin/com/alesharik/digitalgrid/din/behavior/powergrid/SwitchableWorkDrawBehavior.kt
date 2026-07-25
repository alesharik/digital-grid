package com.alesharik.digitalgrid.din.behavior.powergrid

import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.infra.unit.Ampere
import com.alesharik.digitalgrid.infra.unit.Ohm
import com.alesharik.digitalgrid.infra.unit.Volt
import net.minecraft.network.FriendlyByteBuf
import org.patryk3211.powergrid.electricity.sim.SwitchedWire
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode

class SwitchableWorkDrawBehavior(
    val resistance: Ohm,
    private val minVoltage: Volt = DigitalgridConfig.CONFIG.bus.minVoltage,
): PowerGridBehavior {
    private var bus24V: FloatingNode? = null
    private var busMinus: FloatingNode? = null
    private var sw: SwitchedWire? = null

    var enabled: Boolean = false

    var powered: Boolean = false
        private set

    val railVoltage: Volt?
        get() {
            val plus = (bus24V ?: return null).voltage
            val minus = (busMinus ?: return null).voltage
            return Volt(plus - minus)
        }

    override fun buildCircuit(ctx: PowerGridBehavior.CircuitContext) {
        bus24V = ctx.bus24V
        busMinus = ctx.busMinus
        sw = ctx.builder.connectSwitch(resistance.value, ctx.bus24V, ctx.busMinus, false)
    }

    override fun electricalTick(): PowerGridBehavior.TickResult {
        val sw = this.sw ?: return PowerGridBehavior.TickResult.NONE
        val voltage = railVoltage ?: return PowerGridBehavior.TickResult.NONE

        var result = PowerGridBehavior.TickResult.NONE
        if (sw.state != enabled) {
            sw.state = enabled
            result = PowerGridBehavior.TickResult.SAVE
        }

        powered = sw.state && voltage.absoluteValue >= if (powered) minVoltage * 0.9 else minVoltage
        return result
    }

    override fun readSync(buffer: FriendlyByteBuf) {
        powered = buffer.readBoolean()
        enabled = buffer.readBoolean()
    }

    override fun writeSync(buffer: FriendlyByteBuf) {
        buffer.writeBoolean(powered)
        buffer.writeBoolean(enabled)
    }

    companion object {
        fun forBus(amps: Ampere): SwitchableWorkDrawBehavior = SwitchableWorkDrawBehavior(DigitalgridConfig.CONFIG.bus.voltage / amps)
    }
}