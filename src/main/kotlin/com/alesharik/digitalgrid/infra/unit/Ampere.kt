package com.alesharik.digitalgrid.infra.unit

@JvmInline
value class Ampere(val value: Float): Comparable<Ampere> {
    init {
        require(value.isFinite()) { "Ampere value must be finite" }
    }

    constructor(value: Double): this(value.toFloat())

    operator fun plus(other: Ampere): Ampere = Ampere(value + other.value)

    operator fun minus(other: Ampere): Ampere = Ampere(value - other.value)

    operator fun times(other: Ohm): Volt = Volt(value * other.value)

    operator fun times(volt: Volt): Watt = Watt((value * volt.value).toDouble())

    override fun compareTo(other: Ampere): Int = value.compareTo(other.value)
}

val Float.amperes
    get() = Ampere(this)

val Double.amperes
    get() = Ampere(this)