package com.alesharik.digitalgrid.infra.unit

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf

@JvmInline
value class Joule(val value: Float): Comparable<Joule> {
    init {
        require(value.isFinite()) { "Joule value must be finite" }
    }

    constructor(value: Double): this(value.toFloat())

    operator fun plus(other: Joule): Joule = Joule(value + other.value)

    operator fun minus(other: Joule): Joule = Joule(value - other.value)

    operator fun times(scalar: Double): Joule = Joule(value * scalar)

    operator fun div(other: Joule): Double = value.toDouble() / other.value.toDouble()

    operator fun compareTo(other: Double): Int = value.compareTo(other)

    override fun compareTo(other: Joule): Int = value.compareTo(other.value)
}

val Float.joules
    get() = Joule(this)

val Double.joules
    get() = Joule(this)

fun FriendlyByteBuf.write(joule: Joule) {
    writeFloat(joule.value)
}

fun FriendlyByteBuf.readJoule(): Joule {
    return Joule(readFloat().coerceAtLeast(0.0f))
}

fun CompoundTag.put(name: String, joule: Joule) {
    putFloat(name, joule.value)
}

fun CompoundTag.getJoule(name: String): Joule {
    return Joule(getFloat(name).coerceAtLeast(0.0f))
}