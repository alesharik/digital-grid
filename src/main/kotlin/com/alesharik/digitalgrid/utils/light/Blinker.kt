package com.alesharik.digitalgrid.utils.light

import com.alesharik.digitalgrid.utils.Tick

data class Blinker(
    val interval: Tick,
    val dutyCycle: Tick,
) {
    fun isOn(): Boolean {
        if (interval.tick == 0) {
            return true
        }
        return Tick.currentAnimation().tick % interval.tick < dutyCycle.tick
    }

    companion object {
        val NO_BLINK = Blinker(interval = Tick(0), dutyCycle = Tick(0))

        val HALF_SECOND = Blinker(interval = Tick(10), dutyCycle = Tick(5))
    }
}
