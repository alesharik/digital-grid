package com.alesharik.digitalgrid.din

@JvmInline
value class DINUnit(val value: Int) {
    fun toDouble(): Double = value.toDouble()
}