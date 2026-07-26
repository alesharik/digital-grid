package com.alesharik.digitalgrid.circuit.sim

import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode
import org.patryk3211.powergrid.electricity.sim.node.TransformerCoupling
import org.patryk3211.powergrid.electricity.sim.solver.IOuterHook
import kotlin.math.abs
import kotlin.math.pow

/**
 * Sits across VIN+/VIN- and does double duty:
 *  - it is the [IOuterHook] that drives [coupling]'s ratio every solver pass to hold the
 *    secondary voltage at [setpoint] (buck-boost regulation), with an undervoltage lockout
 *    below [minInput];
 *  - its conductance is the input-side efficiency loss, re-derived from the measured output
 *    power each pass so efficiency stays flat at [efficiency] across the whole input range,
 *    instead of varying with Vin^2 the way a fixed resistor would;
 *  - it is the thermal heat source for the converter (see `DcDcConverterComponent.bake`), so
 *    the loss it draws from VIN is exactly the heat that burns the part out on overload.
 *
 * There is no current limiting and no foldback: exceeding [maxInput], or delivering enough
 * output power to push the loss above the component's rated dissipation, simply makes this
 * wire draw more heat until the thermal model destroys the part.
 */
class DcDcConverterWire(
    vinP: IElectricNode,
    vinN: IElectricNode,
    private val coupling: TransformerCoupling,
    private val setpoint: Float,
    private val efficiency: Float,
    private val minInput: Float,
    private val maxInput: Float,
    private val ratedDissipation: Float,
) : AbstractElectricWire(vinP, vinN), IOuterHook {
    private var locked = true
    private var currentConductance: Double = QUIESCENT_G

    /**
     * Latched by the thermal unit's overheat callback (see `DcDcConverterComponent.bake`).
     *
     * The thermal unit destroys a component by calling [AbstractElectricWire.remove] on its heat
     * sources, but that is a no-op while the wire is not attached to a network — which is exactly
     * the state during world load, where `CircuitBoardBlockEntity.read` re-bakes the circuit
     * *before* restoring the thermal state. The freshly baked wire therefore survives the removal,
     * gets attached to the network afterwards, and its [preSolve] would put the converter straight
     * back into regulation. Latching here keeps a destroyed converter dead whether or not the
     * removal took effect.
     *
     * This needs no NBT of its own: the temperature that implies it is persisted by Power Grid,
     * and the callback re-applies the latch on every load.
     */
    var burned = false
        private set

    /** Idempotent; the overheat callback may fire on every temperature change. */
    fun markBurned() {
        burned = true
        coupling.setRatio(0f)
        updateConductance(QUIESCENT_G)
    }

    override fun conductance(): Double = currentConductance

    /** Mirrors [org.patryk3211.powergrid.electricity.sim.special.NeonBulbWire.updateConductance]. */
    private fun updateConductance(target: Double) {
        if (network != null) {
            network.updateConductance(this, target - currentConductance)
        }
        currentConductance = target
    }

    override fun preSolve() {
        // Destroyed: [markBurned] already parked the ratio and the conductance, and nothing else
        // touches them, so simply staying out of the way holds the converter dead.
        if (burned) {
            return
        }

        val vin = potentialDifference()
        val absVin = abs(vin)

        // Undervoltage lockout, hysteresis so it does not chatter at the threshold.
        val threshold = if (locked) minInput.toDouble() else minInput * 0.9
        locked = absVin < threshold
        if (locked) {
            coupling.setRatio(0f)
            updateConductance(QUIESCENT_G)
            return
        }

        // Signed vin keeps the output polarity fixed regardless of input polarity.
        val ratio = (setpoint / vin).coerceIn(-MAX_RATIO, MAX_RATIO)
        coupling.setRatio(ratio.toFloat())

        // Loss draw; stateValue is the coupling's secondary-branch current.
        val pOut = abs(coupling.stateValue * setpoint)
        var pLoss = pOut * (1.0 / efficiency - 1.0)
        if (absVin > maxInput) {
            // Overvoltage stress on top of the efficiency loss - burns the part out in seconds.
            pLoss += ratedDissipation * OVERVOLTAGE_FACTOR * ((absVin / maxInput).pow(2) - 1.0)
        }
        updateConductance((pLoss / (absVin * absVin)).coerceIn(ElectricalNetwork.G_MIN, MAX_G))
    }

    companion object {
        /** Idle draw while locked out; same order of magnitude as NeonBulbWire.OFF_CONDUCTANCE. */
        private const val QUIESCENT_G = 1e-6

        /** Numerical safety bound, not a physical limit - keeps the matrix finite if vin
         *  collapses towards zero (e.g. a near-0V configured lockout threshold). */
        private const val MAX_G = 1000.0

        private const val MAX_RATIO = 100.0

        /**
         * Sharpness of the overvoltage knee. The stress term is proportional to
         * `(Vin/maxInput)^2 - 1`, which fades to zero at the limit itself, so there is always some
         * grace band above `maxInput` before the part actually cooks. This factor sets how wide it
         * is: burnout needs the total loss to clear `ratedDissipation * (overheat - 22) /
         * (overheat - headroom - 22)`, i.e. ~1.19x rated for the constants in
         * `DcDcConverterComponent`, so an unloaded converter dies at
         * `maxInput * sqrt(1 + 1.19/FACTOR)`. At 40 that is ~1.5% over the limit; at 4 it would be
         * ~14% over, which would make the configured 240V ceiling meaningless.
         */
        private const val OVERVOLTAGE_FACTOR = 40.0
    }
}
