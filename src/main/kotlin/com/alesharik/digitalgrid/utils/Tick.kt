package com.alesharik.digitalgrid.utils

import net.createmod.catnip.animation.AnimationTickHolder

@JvmInline
value class Tick(val tick: Int): Comparable<Tick> {
    val seconds: Int
        get() = tick / 20

    operator fun plus(other: Tick) = Tick(tick + other.tick)

    operator fun minus(other: Tick) = Tick(tick - other.tick)

    operator fun inc() = Tick(tick + 1)

    operator fun dec() = Tick(tick - 1)

    operator fun div(other: Tick): Float = tick / other.tick.toFloat()

    override fun compareTo(other: Tick): Int = tick.compareTo(other.tick)

    operator fun compareTo(other: Int): Int = tick.compareTo(other)

    companion object {
        fun currentAnimation(): Tick = Tick(AnimationTickHolder.getTicks())

        fun fromSeconds(seconds: Float) = Tick((seconds * 20.0f).toInt())

        fun fromSeconds(seconds: Int) = Tick(seconds * 20)
    }
}

val Int.ticks
    get() = Tick(this)