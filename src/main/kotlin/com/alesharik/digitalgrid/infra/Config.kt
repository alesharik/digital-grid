package com.alesharik.digitalgrid.infra

import net.neoforged.neoforge.common.ModConfigSpec
import kotlin.reflect.KProperty

inline fun modConfig(crossinline fn: ModConfigSpec.Builder.() -> Unit): ModConfigSpec =
    ModConfigSpec.Builder().apply(fn).build()

inline fun <R> ModConfigSpec.Builder.block(name: String, crossinline fn: ModConfigSpec.Builder.() -> R): R {
    push(name)
    val r = fn()
    pop()
    return r
}

fun ModConfigSpec.DoubleValue.asVar(): VarDelegate<Double> = VarDelegate { get() }

fun ModConfigSpec.LongValue.asVar(): VarDelegate<Long> = VarDelegate { get() }

inline fun <T> ModConfigSpec.DoubleValue.asVar(crossinline map: (Double) -> T): VarDelegate<T> = VarDelegate { map(get()) }

class VarDelegate<T>(
    private val producer: () -> T
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = producer()
}