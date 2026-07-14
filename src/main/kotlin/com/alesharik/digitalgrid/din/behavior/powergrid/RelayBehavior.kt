package com.alesharik.digitalgrid.din.behavior.powergrid

import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.infra.unit.Ampere
import com.alesharik.digitalgrid.infra.unit.Ohm
import com.alesharik.digitalgrid.infra.unit.Volt
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import org.patryk3211.powergrid.electricity.sim.SwitchedWire
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode

class RelayBehavior(
    val terminal1: Int,
    val terminal2: Int,
    val resistance: Ohm,
    private val minVoltage: Volt = DigitalgridConfig.CONFIG.bus.minVoltage,
): PowerGridBehavior {
    private var bus24V: FloatingNode? = null
    private var busMinus: FloatingNode? = null

    private var contact: SwitchedWire? = null
    private var coil: SwitchedWire? = null

    /** Last [commanded] value persisted, to request a save only when it changes. */
    private var persistedCommand = false

    var commanded: Boolean = false

    var closed: Boolean = false
        private set

    val railVoltage: Volt?
        get() {
            val plus = (bus24V ?: return null).voltage
            val minus = (busMinus ?: return null).voltage
            return Volt(plus - minus)
        }

        override fun buildCircuit(ctx: DinRackEntity.CircuitContext) {
            bus24V = ctx.bus24V
            busMinus = ctx.busMinus

        contact = ctx.builder.connectSwitch(CONTACT_RESISTANCE, ctx.terminalNode(terminal1), ctx.terminalNode(terminal2), closed)
        // Coil load across the rail, drawn only while commanded on (R = V / I at nominal).
        coil = ctx.builder.connectSwitch(resistance.value, ctx.bus24V, ctx.busMinus, commanded)
    }

    override fun electricalTick(): PowerGridBehavior.TickResult {
        val contact = contact ?: return PowerGridBehavior.TickResult.NONE
        val coil = coil ?: return PowerGridBehavior.TickResult.NONE
        val v = railVoltage ?: return PowerGridBehavior.TickResult.NONE
        val cmd = commanded
        if (coil.state != cmd) coil.state = cmd
        closed = cmd && v >= if (closed) minVoltage * 0.9 else minVoltage
        if (contact.state != closed) contact.state = closed

        // Contact state rides Power Grid's periodic node-state sync via the rack's
        // sync appender, so only the persistent command needs a save here.
        return if (cmd != persistedCommand) {
            persistedCommand = cmd
            PowerGridBehavior.TickResult.SAVE
        } else {
            PowerGridBehavior.TickResult.NONE
        }
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        commanded = tag.getBoolean("Commanded")
        persistedCommand = commanded
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        if (commanded) tag.putBoolean("Commanded", true)
    }

    override fun writeSync(buffer: FriendlyByteBuf) {
        buffer.writeByte((if (commanded) 1 else 0) or (if (closed) 2 else 0))
    }

    override fun readSync(buffer: FriendlyByteBuf) {
        val b = buffer.readByte().toInt()
        commanded = (b and 1) != 0
        closed = (b and 2) != 0
    }

    companion object {
        private const val CONTACT_RESISTANCE = 0.01f

        fun forBus(
            terminal1: Int,
            terminal2: Int,
            amps: Ampere,
            minVoltage: Volt = DigitalgridConfig.CONFIG.bus.minVoltage
        ): RelayBehavior = RelayBehavior(terminal1, terminal2, DigitalgridConfig.CONFIG.bus.voltage / amps, minVoltage)
    }
}