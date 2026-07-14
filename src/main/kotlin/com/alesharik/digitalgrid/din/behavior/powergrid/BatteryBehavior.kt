package com.alesharik.digitalgrid.din.behavior.powergrid

import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.infra.unit.*
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling
import kotlin.math.abs

class BatteryBehavior(
    /**
     * Battery capacity
     */
    val capacity: Joule,

    /**
     * Minimal battery voltage
     */
    private val emfEmpty: Volt,

    /**
     * Battery voltage threshold (max = min + span)
     */
    private val emfSpan: Volt,

    /**
     * Internal battery resistance
     */
    private val internalResistance: Ohm,

    /**
     * Battery resistance when depleted
     */
    private val depletedResistance: Ohm,
): PowerGridBehavior {
    private var coupling: VoltageSourceCoupling? = null

    var energy: Joule = Joule(0.0)
        private set

    val power: Watt?
        get() {
            val source = coupling ?: return null
            val v = -source.current * source.voltage
            if (!v.isFinite()) return null
            return Watt(v)
        }

    val voltage: Volt?
        get() {
            val source = coupling ?: return null
            return Volt(source.positive.voltage - (source.negative?.voltage ?: 0.0))
        }

    override fun buildCircuit(ctx: DinRackEntity.CircuitContext) {
        val source = VoltageSourceCoupling(ctx.bus24V, ctx.busMinus, internalResistance.value)
        ctx.builder.add(source)
        coupling = source
        updateParameters(source)
    }

    override fun electricalTick(): PowerGridBehavior.TickResult {
        val source = coupling ?: return PowerGridBehavior.TickResult.NONE
        // Sign convention as in Power Grid's battery: positive power discharges.
        val power = -source.current * source.voltage
        if (!power.isFinite()) return PowerGridBehavior.TickResult.NONE
        // remove energy for a tick
        energy = (energy - Joule(power * 0.05)).coerceIn(0.0.joules, capacity)
        updateParameters(source)
        // Clients receive energy through the rack's sync appender (rides Power
        // Grid's periodic node state sync), so no explicit block entity sync here.
        return if (abs(power) > 0.05) PowerGridBehavior.TickResult.SAVE else PowerGridBehavior.TickResult.NONE
    }

    private fun updateParameters(source: VoltageSourceCoupling) {
        val chargeLevel = (energy / capacity).coerceIn(0.0, 1.0)
        source.voltage = (emfEmpty + emfSpan * chargeLevel).value.toDouble()
        // An empty battery must not source power, but has to stay revivable:
        // block only the discharge direction and reopen as soon as current reverses.
        val discharging = -source.current * source.voltage > 0
        source.resistance = if (energy <= 0.0 && discharging) depletedResistance.value else internalResistance.value
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        energy = tag.getJoule("Energy").coerceIn(0.0.joules, capacity)
        coupling?.let { updateParameters(it) }
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        tag.put("Energy", energy)
    }

    override fun writeSync(buffer: FriendlyByteBuf) {
        buffer.write(energy)
    }

    override fun readSync(buffer: FriendlyByteBuf) {
        energy = buffer.readJoule().coerceIn(0.0.joules, capacity)
    }
}