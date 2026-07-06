package com.alesharik.digitalgrid

import net.neoforged.neoforge.common.ModConfigSpec

object DigitalgridConfig {
    val SPEC: ModConfigSpec
    val BATTERY_CAPACITY_WH: ModConfigSpec.DoubleValue
    val PSU_MAX_POWER: ModConfigSpec.DoubleValue
    val PSU_MIN_INPUT_VOLTAGE: ModConfigSpec.DoubleValue

    init {
        val builder = ModConfigSpec.Builder()

        builder.push("battery")
        BATTERY_CAPACITY_WH = builder
            .comment("DIN battery module capacity in watt-hours")
            .defineInRange("capacityWh", 50.0, 1.0, 1_000_000.0)
        builder.pop()

        builder.push("powerSupply")
        PSU_MAX_POWER = builder
            .comment("DIN power supply maximum output power in watts")
            .defineInRange("maxPower", 100.0, 1.0, 1_000_000.0)
        PSU_MIN_INPUT_VOLTAGE = builder
            .comment("Minimum input voltage for the DIN power supply to produce output")
            .defineInRange("minInputVoltage", 8.0, 0.0, 100_000.0)
        builder.pop()

        SPEC = builder.build()
    }
}
