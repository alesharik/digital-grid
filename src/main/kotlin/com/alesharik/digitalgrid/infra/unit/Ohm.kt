package com.alesharik.digitalgrid.infra.unit

@JvmInline
value class Ohm(val value: Float): Comparable<Ohm> {
    init {
        require(value >= 0) { "Ohm value must be non-negative: $value" }
        require(value.isFinite()) { "Ohm value must be finite" }
    }

    constructor(value: Double): this(value.toFloat())

    operator fun plus(other: Ohm): Ohm = Ohm(value + other.value)

    operator fun minus(other: Ohm): Ohm = Ohm(value - other.value)

    operator fun times(other: Ampere): Volt = Volt(value * other.value)

    override fun compareTo(other: Ohm): Int = value.compareTo(other.value)
}

val Float.ohms
    get() = Ohm(this)

val Double.ohms
    get() = Ohm(this)