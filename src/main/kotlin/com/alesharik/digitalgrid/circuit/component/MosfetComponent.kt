package com.alesharik.digitalgrid.circuit.component

import com.alesharik.digitalgrid.Digitalgrid
import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.circuit.sim.MosfetWire
import com.google.common.collect.ImmutableCollection
import org.patryk3211.powergrid.circuits.circuitboard.ComponentCircuitBuilder
import org.patryk3211.powergrid.circuits.components.MirrorableComponent
import org.patryk3211.powergrid.circuits.components.properties.ComponentProperty
import org.patryk3211.powergrid.circuits.components.properties.FloatProperty
import org.patryk3211.powergrid.circuits.schematic.ComponentFootprint
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent
import org.patryk3211.powergrid.circuits.thermal.ThermalBuilder

/**
 * An enhancement-mode MOSFET circuit-board component (see [MosfetWire] for the Shichman-Hodges
 * square-law device model). Terminal layout: 0 = drain, 1 = gate, 2 = source, matching
 * [DigitalgridRegistry][com.alesharik.digitalgrid.DigitalgridRegistry.CircuitComponents]'s
 * hand-built footprints.
 */
abstract class MosfetComponent(footprint: ComponentFootprint, private val pChannel: Boolean) : MirrorableComponent(footprint) {
    override fun addProperties(properties: ImmutableCollection.Builder<ComponentProperty<*>>) {
        super.addProperties(properties)
        properties.add(THRESHOLD_VOLTAGE, power(MAX_POWER_DEFAULT))
    }

    // Config must only be read directly here, in bake() - not in the constructor or in a plain
    // property initializer, both of which run during registry events before DigitalgridConfig.CONFIG
    // is loaded. THRESHOLD_VOLTAGE reads config too, but lazily, via `by lazy`.
    override fun bake(placed: PlacedComponent, builder: ComponentCircuitBuilder, thermals: ThermalBuilder.IEmitter) {
        val config = DigitalgridConfig.CONFIG.mosfet

        val wire = MosfetWire(
            builder.terminalNode(0), // Drain
            builder.terminalNode(1), // Gate
            builder.terminalNode(2), // Source
            placed.get(THRESHOLD_VOLTAGE).toDouble(),
            config.transconductance,
            config.channelLengthModulation,
            pChannel
        )
        builder.add(wire)
        placed.add(wire)

        thermals.builder()
            .addHeatSource(wire)
            .setThermalMass(0.01f)
            .setMaxPower(config.maxPower.value.toFloat(), config.overheatTemperature.toFloat())
    }

    companion object {
        // Matches Component.power(20)'s role in BJTComponent.addProperties: a fixed literal
        // shown on the property tooltip, not read from config (properties.add runs during
        // registry events, before config load - see the bake() comment above).
        private const val MAX_POWER_DEFAULT = 60f

        val THRESHOLD_VOLTAGE: FloatProperty by lazy {
            FloatProperty(
                Digitalgrid.ID,
                "mosfet_threshold_voltage",
                2.0f,
                1.0f,
                5.0f
            )
        }
    }
}

class NChannelMosfetComponent(footprint: ComponentFootprint) : MosfetComponent(footprint, false)

class PChannelMosfetComponent(footprint: ComponentFootprint) : MosfetComponent(footprint, true)
