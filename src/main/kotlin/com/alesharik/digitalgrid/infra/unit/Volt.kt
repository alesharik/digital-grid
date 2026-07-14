package com.alesharik.digitalgrid.infra.unit

import kotlin.math.absoluteValue

@JvmInline
value class Volt(val value: Float): Comparable<Volt> {
    val absoluteValue: Volt
        get() = Volt(value.absoluteValue)

    init {
        require(value.isFinite()) { "Volt value must be finite" }
    }

    constructor(value: Double): this(value.toFloat())

    operator fun plus(volt: Volt): Volt = Volt(value + volt.value)

    operator fun minus(volt: Volt): Volt = Volt(value - volt.value)

    operator fun times(ratio: Double): Volt = Volt(value * ratio)

    operator fun div(amp: Ampere): Ohm = Ohm((value / amp.value).absoluteValue)

    operator fun div(res: Ohm): Ampere = Ampere((value / res.value).absoluteValue)

    operator fun div(volt: Volt): Float = value / volt.value

    override operator fun compareTo(other: Volt): Int = value.compareTo(other.value)
}

val Float.volts: Volt
    get() = Volt(this)

val Double.volts: Volt
    get() = Volt(this)