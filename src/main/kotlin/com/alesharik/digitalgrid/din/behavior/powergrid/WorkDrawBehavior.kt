package com.alesharik.digitalgrid.din.behavior.powergrid

import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.infra.unit.Ampere
import com.alesharik.digitalgrid.infra.unit.Ohm
import com.alesharik.digitalgrid.infra.unit.Volt
import net.minecraft.network.FriendlyByteBuf
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode

class WorkDrawBehavior(
    val resistance: Ohm,
    private val minVoltage: Volt = DigitalgridConfig.CONFIG.bus.minVoltage,
): PowerGridBehavior {
    private var bus24V: FloatingNode? = null
    private var busMinus: FloatingNode? = null

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
        ctx.builder.connect(resistance.value, ctx.bus24V, ctx.busMinus)
    }

    override fun electricalTick(): PowerGridBehavior.TickResult {
        val voltage = railVoltage ?: return PowerGridBehavior.TickResult.NONE
        powered = voltage.absoluteValue >= if (powered) minVoltage * 0.9 else minVoltage
        return PowerGridBehavior.TickResult.NONE
    }

    override fun readSync(buffer: FriendlyByteBuf) {
        powered = buffer.readBoolean()
    }

    override fun writeSync(buffer: FriendlyByteBuf) {
        buffer.writeBoolean(powered)
    }

    companion object {
        fun forBus(amps: Ampere): WorkDrawBehavior = WorkDrawBehavior(DigitalgridConfig.CONFIG.bus.voltage / amps)
    }
}