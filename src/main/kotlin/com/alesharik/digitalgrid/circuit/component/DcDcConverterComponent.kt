package com.alesharik.digitalgrid.circuit.component

import com.alesharik.digitalgrid.Digitalgrid
import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.circuit.sim.DcDcConverterWire
import com.google.common.collect.ImmutableCollection
import org.patryk3211.powergrid.circuits.circuitboard.ComponentCircuitBuilder
import org.patryk3211.powergrid.circuits.components.Component
import org.patryk3211.powergrid.circuits.components.properties.ComponentProperty
import org.patryk3211.powergrid.circuits.components.properties.FloatProperty
import org.patryk3211.powergrid.circuits.schematic.ComponentFootprint
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent
import org.patryk3211.powergrid.circuits.thermal.ThermalBuilder

/**
 * A buck-boost DC-DC converter circuit-board component: regulates VOUT to a fixed
 * [OUTPUT_VOLTAGE] setpoint from any VIN within the configured window, at a fixed efficiency.
 *
 * There is no current limiting and no foldback - overloading the output or overvolting the
 * input burns the part out via Power Grid's thermal model, the same way its own tubes do (see
 * [DcDcConverterWire] for the regulation/loss/heat-source logic).
 */
class DcDcConverterComponent(footprint: ComponentFootprint) : Component(footprint) {
    override fun addProperties(properties: ImmutableCollection.Builder<ComponentProperty<*>>) {
        properties.add(OUTPUT_VOLTAGE)
    }

    // Config must only be read here, in bake() - not in the constructor or in a property
    // initializer, both of which run during registry events before DigitalgridConfig.CONFIG
    // is loaded.
    override fun bake(placed: PlacedComponent, builder: ComponentCircuitBuilder, thermals: ThermalBuilder.IEmitter) {
        val config = DigitalgridConfig.CONFIG.dcdcConverter
        val efficiency = config.efficiency.toFloat()
        val minInput = config.minInputVoltage.value
        val maxInput = config.maxInputVoltage.value
        val ratedDissipation = config.maxPower.value.toFloat() * (1f / efficiency - 1f)

        val vinP = builder.terminalNode(0)
        val vinN = builder.terminalNode(1)
        val voutP = builder.terminalNode(2)
        val voutN = builder.terminalNode(3)

        val setpoint = placed.get(OUTPUT_VOLTAGE)
        // Ideal transformer: enforces Vout = ratio * Vin and Iin = -ratio * Iout in the matrix,
        // so power transfer is exactly conserved by construction (same core mechanism as
        // DinRackPowerSupplyEntity.PSUBehavior).
        val coupling = builder.couple(0f, TRANSFORMER_RESISTANCE, vinP, vinN, voutP, voutN)

        val wire = DcDcConverterWire(vinP, vinN, coupling, setpoint, efficiency, minInput, maxInput, ratedDissipation)
        builder.add(wire)
        placed.add(wire)

        thermals.builder()
            .addHeatSource(wire)
            .setThermalMass(THERMAL_MASS)
            .setOverheatTemperature(OVERHEAT_TEMPERATURE)
            .setMaxPower(ratedDissipation, OVERHEAT_TEMPERATURE - HEADROOM)
            // The thermal unit only removes heat *sources*, and the coupling is a node, so nothing
            // would zero it without this.
            .withOverheatCallback {
                wire.markBurned()
                // Overheating only mutates the in-memory ThermalUnit; nothing marks the block
                // entity dirty. Without this the chunk saves the pre-overheat temperature, so on
                // reload the unit comes back cool, is not overheated, and the converter revives.
                // Destruction deliberately lives in the thermal state and nowhere else — that is
                // what lets Power Grid's creative `repairBroken()` bring the part back.
                markBoardChanged(placed)
            }
    }

    /**
     * Marks the owning circuit board dirty so the thermal temperature reaches disk. Tolerates being
     * called before the placement has a level (the world supplier is only wired up after bake).
     */
    private fun markBoardChanged(placed: PlacedComponent) {
        val level = runCatching { placed.world }.getOrNull() ?: return
        if (level.isClientSide) {
            return
        }
        level.getBlockEntity(placed.pos)?.setChanged()
    }

    companion object {
        val OUTPUT_VOLTAGE: FloatProperty = FloatProperty(Digitalgrid.ID, "dcdc_output_voltage", 24f, 2f, 60f)

        private const val TRANSFORMER_RESISTANCE = 0.1f

        private const val THERMAL_MASS = 0.1f
        private const val OVERHEAT_TEMPERATURE = 150f
        private const val HEADROOM = 20f
    }
}
