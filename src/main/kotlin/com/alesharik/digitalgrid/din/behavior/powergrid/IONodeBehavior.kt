package com.alesharik.digitalgrid.din.behavior.powergrid

import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.infra.unit.Ohm
import com.alesharik.digitalgrid.infra.unit.Volt
import com.alesharik.digitalgrid.infra.unit.ohms
import com.alesharik.digitalgrid.infra.unit.volts
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import org.patryk3211.powergrid.electricity.sim.SwitchedWire
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode
import org.patryk3211.powergrid.electricity.sim.node.TransformerCoupling

class IONodeBehavior(
    private val terminal: Int,
    private val driverResistance: Ohm = 0.1.ohms,
    private val switchResistance: Ohm = 0.01.ohms,
    private val maxRatio: Float = 10.0f
): PowerGridBehavior {
    private var bus24V: FloatingNode? = null
    private var busMinus: FloatingNode? = null

    @Volatile var driven = false
        private set
    @Volatile private var target: Volt = Volt(0.0)
    @Volatile private var persisted = false

    /** Last measured pin voltage vs GND, cached each tick for the computer thread. */
    @Volatile var measured: Volt = Volt(0.0)
        private set

    private var coupling: TransformerCoupling? = null
    private var switch: SwitchedWire? = null
    private var node: FloatingNode? = null

    val railVoltage: Volt?
        get() {
            val plus = (bus24V ?: return null).voltage
            val minus = (busMinus ?: return null).voltage
            return Volt(plus - minus)
        }

    fun setVoltage(volt: Volt) {
        driven = true
        target = volt
        persisted = false
    }

    fun clearVoltage() {
        driven = false
        target = Volt(0.0)
        persisted = false
    }

    override fun buildCircuit(ctx: DinRackEntity.CircuitContext) {
        bus24V = ctx.bus24V
        busMinus = ctx.busMinus

        // Rail-powered converter per pin: primary across the 24V rail, secondary
        // drives the pin through a switch. Open switch = high-impedance (read-only)
        // pin, so the converter never fights an externally applied voltage. The
        // first tick closes the switch for driven pins with a proper ratio.
        val out = ctx.builder.addInternalNode()
        coupling = ctx.builder.couple(0f, driverResistance.value, ctx.bus24V, ctx.busMinus, out, ctx.busMinus)
        switch = ctx.builder.connectSwitch(switchResistance.value, out, ctx.terminalNode(terminal), false)
        node = ctx.terminalNode(terminal)
    }

    override fun electricalTick(): PowerGridBehavior.TickResult {
        val busMinus = busMinus ?: return PowerGridBehavior.TickResult.NONE
        val node = node ?: return PowerGridBehavior.TickResult.NONE
        val railVoltage = railVoltage ?: return PowerGridBehavior.TickResult.NONE
        val v = node.voltage - busMinus.voltage
        measured = (if (v.isFinite()) v else 0.0).volts

        switch?.let { if (it.state != driven) it.state = driven }
        // Signed rail voltage keeps the output at +target regardless of rail polarity.
        val ratio = if (driven) (target / railVoltage).coerceIn(-maxRatio, maxRatio) else 0.0
        coupling?.setRatio(ratio.toFloat())

        if (!persisted) {
            persisted = true
            return PowerGridBehavior.TickResult.SAVE
        } else {
            return PowerGridBehavior.TickResult.NONE
        }
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        tag.putBoolean("Driven", driven)
        tag.putFloat("Target", target.value)
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        driven = tag.getBoolean("Driven")
        target = Volt(tag.getFloat("Target"))
        persisted = true
    }

    override fun writeSync(buffer: FriendlyByteBuf) {
        buffer.writeBoolean(driven)
    }

    override fun readSync(buffer: FriendlyByteBuf) {
        driven = buffer.readBoolean()
    }
}