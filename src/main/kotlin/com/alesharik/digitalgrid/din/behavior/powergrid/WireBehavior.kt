package com.alesharik.digitalgrid.din.behavior.powergrid

import com.alesharik.digitalgrid.infra.unit.Ohm
import com.alesharik.digitalgrid.infra.unit.ohms

class WireBehavior(
    private val terminal1: Int,
    private val terminal2: Int,
    private val resistance: Ohm = 0.1f.ohms
): PowerGridBehavior {
    override fun buildCircuit(ctx: PowerGridBehavior.CircuitContext) {
        val a = ctx.terminalNode(terminal1)
        val b = ctx.terminalNode(terminal2)
        ctx.builder.connect(resistance.value, a, b)
    }
}