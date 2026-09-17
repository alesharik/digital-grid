package com.alesharik.digitalgrid.infra

import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.Tag
import net.neoforged.neoforge.common.ModConfigSpec
import org.patryk3211.powergrid.circuits.components.properties.ComponentProperty
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

private class ConfigFloatProperty(
    namespace: String,
    name: String,
    private val defaultValue: Float,
    private val min: Float,
    private val max: Float
) : ComponentProperty<Float>(namespace, name) {
    protected open fun limit(value: Float): Float {
        if (value < this.min) {
            return this.min
        } else {
            return if (value > this.max) this.max else value
        }
    }

    @Throws(NumberFormatException::class)
    override fun parse(str: String): Float {
        val value = str.toFloat()
        return this.limit(value)
    }

    override fun toString(value: Float?): String = value.toString()

    override fun read(element: Tag?): Float {
        if (element == null) {
            return this.defaultValue
        } else if (element.getId().toInt() != 5) {
            return this.defaultValue
        } else {
            val value = (element as FloatTag).getAsFloat()
            return this.limit(value)
        }
    }

    override fun write(value: Float): Tag {
        return FloatTag.valueOf(value)
    }

    override fun defaultValue(): Float {
        return this.defaultValue
    }
}