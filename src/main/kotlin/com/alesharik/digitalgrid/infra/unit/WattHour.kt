package com.alesharik.digitalgrid.infra.unit

@JvmInline
value class WattHour(val value: Double) {
    val joules: Joule
        get() = Joule(value * 3600)
}