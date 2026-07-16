package com.alesharik.digitalgrid.din.behavior.powergrid

import com.alesharik.digitalgrid.infra.unit.Ohm
import com.alesharik.digitalgrid.infra.unit.ohms

class BusGroundWireBehavior(
    private val terminal: Int,
    private val resistance: Ohm = 0.1f.ohms
): PowerGridBehavior {
    override fun buildCircuit(ctx: PowerGridBehavior.CircuitContext) {
        val a = ctx.terminalNode(terminal)
        ctx.builder.connect(resistance.value, a, ctx.busMinus)
    }
}