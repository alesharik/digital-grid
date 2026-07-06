package com.alesharik.digitalgrid.utils

import net.createmod.catnip.animation.AnimationTickHolder

@JvmInline
value class Tick(val tick: Int) {
    companion object {
        fun currentAnimation(): Tick = Tick(AnimationTickHolder.getTicks())

        fun fromSeconds(seconds: Float) = Tick((seconds * 20.0f).toInt())
    }
}
