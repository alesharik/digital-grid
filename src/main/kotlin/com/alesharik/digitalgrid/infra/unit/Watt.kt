package com.alesharik.digitalgrid.infra.unit

@JvmInline
value class Watt(val value: Double): Comparable<Watt> {
    init {
        require(value.isFinite()) { "Watt value must be finite" }
    }

    operator fun div(watt: Watt): Double = value / watt.value

    override fun compareTo(other: Watt): Int = value.compareTo(other.value)
}

val Double.watts: Watt
    get() = Watt(this)

val Float.watts: Watt
    get() = Watt(this.toDouble())