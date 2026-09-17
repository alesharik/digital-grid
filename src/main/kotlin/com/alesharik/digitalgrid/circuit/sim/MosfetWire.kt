package com.alesharik.digitalgrid.circuit.sim

import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork.G_MIN
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode
import org.patryk3211.powergrid.electricity.sim.solver.IAdmittanceAdder
import org.patryk3211.powergrid.electricity.sim.solver.IResidualAdder
import org.patryk3211.powergrid.electricity.sim.solver.ISolverHook
import org.patryk3211.powergrid.electricity.sim.special.CompoundWire
import org.patryk3211.powergrid.electricity.sim.special.CompoundWire.ConductanceWire
import org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/**
 * An enhancement-mode MOSFET using the Shichman-Hodges square-law model, structured as a direct
 * analogue of [org.patryk3211.powergrid.electricity.sim.special.BJTWire] so its MNA
 * residual/stamp sign conventions are correct by construction instead of re-derived. Terminal
 * mapping follows the BJT template: drain <-> collector, gate <-> base, source <-> emitter -
 * [node1] is the gate, [node2] is the source, [drain] is kept as an extra field exactly like
 * BJTWire keeps its collector.
 */
class MosfetWire(
    private val drain: IElectricNode,
    gate: IElectricNode,
    source: IElectricNode,
    private val thresholdVoltage: Double,
    private val k: Double,
    private val lambda: Double,
    pChannel: Boolean,
) : CompoundWire(gate, source), ISolverHook {
    // Drain - Source, carries gds (channel output conductance).
    private val drainSource: ConductanceWire = addDynamicWire(drain, source)

    // Gate-controlled Drain/Source cross term, carries gm (transconductance). Mirrors BJTWire's
    // collectorEmitter/emitterCollector CrossWires, collapsed to the single term a MOSFET needs.
    private val gateDrainVccs: CrossWire = addInternalWire(CrossWire(gate, drain, source))

    private val pch = if (pChannel) -1 else 1

    // Body diode: for an N-channel MOSFET the anode is the source and the cathode is the drain;
    // for a P-channel MOSFET this is reversed. Kept as a field so its conduction loss can be
    // folded into internalPower() - the thermal unit only heats from registered heat sources, and
    // this wire is the only one registered, so without that a part held off while its body diode
    // carries the full load current would never heat up.
    private val bodyDiode: PNJunctionWire = addInternalWire(
        if (pChannel) {
            PNJunctionWire(1e-12, 0.01, 22.0, 1.0, drain, source)
        } else {
            PNJunctionWire(1e-12, 0.01, 22.0, 1.0, source, drain)
        }
    )

    private var prevVgs = 0.0
    private var prevVds = 0.0

    private var iDrain = 0.0
    private var iGate = 0.0
    private var iSource = 0.0

    private var power = 0.0

    init {
        // Gate leakage: fixed at G_MIN. A MOSFET gate has no DC path, so without this the gate
        // node floats and the MNA matrix goes singular. Set once via setConductance(G_MIN) and
        // never changed again.
        setConductance(G_MIN)
    }

    /**
     * Newton step limiter for the gate voltage, in the spirit of SPICE's `fetlim`.
     *
     * Deliberately *not* BJTWire's `pnLim`: that damps on the 25 mV thermal-voltage scale, which is
     * correct for an exponential junction but roughly two orders of magnitude too aggressive for a
     * square-law channel. A gate that has to slew 0 V -> 5 V would move ~0.13 V per iteration under
     * `pnLim` and burn 40+ Newton iterations doing it. Here the allowed step scales with how far
     * the operating point already sits past threshold, with a 2 V floor.
     */
    private fun fetLim(vNew: Double, vOld: Double, vTh: Double): Double {
        val maxStep = abs(2 * (vOld - vTh)) + 2.0
        val dV = vNew - vOld
        if (abs(dV) > maxStep) {
            return vOld + maxStep * sign(dV)
        }
        return vNew
    }

    /** Step clamp for Vds, which has no threshold to scale against. */
    private fun vdsLim(vNew: Double, vOld: Double): Double {
        val maxStep = abs(vOld) * 0.5 + 2.0
        val dV = vNew - vOld
        if (abs(dV) > maxStep) {
            return vOld + maxStep * sign(dV)
        }
        return vNew
    }

    override fun startIteration(iteration: Int) {
        var vgs = pch * (node1.voltage - node2.voltage)
        var vds = pch * (drain.voltage - node2.voltage)

        vgs = fetLim(vgs, prevVgs, thresholdVoltage)
        vds = vdsLim(vds, prevVds)
        prevVgs = vgs
        prevVds = vds

        // Reverse conduction: role-swap drain/source when vds < 0, so a real MOSFET conducts
        // backwards through the channel at full Rds(on) instead of only through the body diode -
        // required for H-bridge / synchronous-rectification circuits.
        val reversed = vds < 0
        val vgsEff = if (reversed) vgs - vds else vgs
        val vdsEff = if (reversed) -vds else vds

        val vov = vgsEff - thresholdVoltage

        var id: Double
        var gm: Double
        var gds: Double
        if (vov <= 0) {
            // Cutoff
            id = 0.0
            gm = 0.0
            gds = 0.0
        } else if (vdsEff < vov) {
            // Triode
            id = k * (2 * vov * vdsEff - vdsEff * vdsEff)
            gm = 2 * k * vdsEff
            gds = 2 * k * (vov - vdsEff)
        } else {
            // Saturation
            id = k * vov * vov * (1 + lambda * vdsEff)
            gm = 2 * k * vov * (1 + lambda * vdsEff)
            gds = k * vov * vov * lambda
        }
        if (reversed) {
            // The drain/source roles were swapped to evaluate the channel, so the current and BOTH
            // derivatives have to be mapped back into (vgs, vds) space. With
            // id = -f(vgs - vds, -vds):
            //   d(id)/d(vgs) = -gm_eff
            //   d(id)/d(vds) =  gm_eff + gds_eff
            // Leaving the effective-space derivatives in place would not move the converged answer
            // (the residual below is derived from whatever gm/gds get stamped, so KCL still holds),
            // but it hands Newton a wrong-signed Jacobian entry - which is exactly what makes a
            // reverse-conducting H-bridge oscillate instead of settle.
            val gmEff = gm
            id = -id
            gm = -gmEff
            gds = gmEff + gds
        }
        gds = max(gds, G_MIN)

        var gAdd = 1e-6
        if (iteration > 100) {
            gAdd = 1e-4
        }
        val gTotal = gds + gAdd

        drainSource.setConductance(gTotal)
        gateDrainVccs.setConductance(-gm)

        // Companion (equivalent) current sources for the Newton residual. Derived the same way
        // BJTWire's Ic/Ib/Ie are: device current minus what the linearized admittance stamps
        // already predict at this operating point, re-multiplied by pch where BJTWire multiplies
        // by pnp.
        //
        // Unlike BJTWire (whose two base-anchored CrossWires cancel each other's contribution to
        // the base row), this wire has only one gate-anchored CrossWire (gateDrainVccs), so its
        // -gm stamp on the gate row is *not* self-cancelling. Working through the same "device
        // current minus row's linear admittance contribution" derivation used to check the BJT
        // formulas against BJTWire's actual source gives:
        //   iDrain  = idReal - gTotal * vdsReal - gm * vgsReal
        //   iGate   =                             gm * vgsReal
        //   iSource = -iDrain - iGate
        // i.e. iGate is *not* ~0 in general - it must cancel the gateDrainVccs stamp's spurious
        // contribution to the gate row, or the gate would source/sink a current on the order of
        // gm * Vgs despite being modelled as a high-impedance node.
        val vgsReal = pch * vgs
        val vdsReal = pch * vds
        val idReal = pch * id

        iDrain = idReal - gTotal * vdsReal - gm * vgsReal
        iGate = gm * vgsReal
        iSource = -iDrain - iGate

        power = abs(vds * id)
    }

    override fun internalPower(): Double =
        power + abs(bodyDiode.current() * bodyDiode.potentialDifference())

    override fun addResidual(residual: IResidualAdder) {
        residual.add(drain.index, iDrain)
        residual.add(node1.index, iGate)
        residual.add(node2.index, iSource)
    }

    override fun coupledNodes(): Collection<IElectricNode> = listOf(drain, node1, node2)

    override fun toString(): String =
        String.format("%s-MOSFET(Vth=%g, k=%g)", if (pch == -1) "P" else "N", thresholdVoltage, k)

    private class CrossWire(private val gate: IElectricNode, node1: IElectricNode, node2: IElectricNode) :
        ConductanceWire(node1, node2) {
        override fun stamp(admittance: IAdmittanceAdder, change: Double) {
            admittance.add(gate.index, gate.index, change)
            admittance.add(gate.index, node2.index, -change)
            admittance.add(node1.index, gate.index, -change)
            admittance.add(node1.index, node2.index, change)
        }

        override fun coupledNodes(): Collection<IElectricNode> = listOf(gate, node1, node2)

        override fun toString(): String = String.format("MOSFET\$CrossWire(G=%g)", conductance())
    }
}
