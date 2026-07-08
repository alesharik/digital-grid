package com.alesharik.digitalgrid

import net.neoforged.neoforge.common.ModConfigSpec

object DigitalgridConfig {
    val SPEC: ModConfigSpec
    val BATTERY_CAPACITY_WH: ModConfigSpec.DoubleValue
    val PSU_MAX_POWER: ModConfigSpec.DoubleValue
    val PSU_MIN_INPUT_VOLTAGE: ModConfigSpec.DoubleValue
    val PLC_CURRENT_DRAW: ModConfigSpec.DoubleValue
    val PLC_MIN_VOLTAGE: ModConfigSpec.DoubleValue
    val PLC_BOOT_BLINK_TICKS: ModConfigSpec.IntValue
    val PLC_IO_MAX_VOLTAGE: ModConfigSpec.DoubleValue
    val PLC_IO_MAX_CURRENT: ModConfigSpec.DoubleValue
    val PLC_IO_CURRENT_DRAW: ModConfigSpec.DoubleValue
    val PLC_RELAY_COIL_CURRENT: ModConfigSpec.DoubleValue
    val PLC_RELAY_CURRENT_DRAW: ModConfigSpec.DoubleValue

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

        builder.push("plc")
        PLC_CURRENT_DRAW = builder
            .comment("DIN PLC current draw from the internal 24V bus, in amperes (at nominal voltage)")
            .defineInRange("currentDraw", 1.0, 0.001, 100.0)
        PLC_MIN_VOLTAGE = builder
            .comment("Minimum bus voltage for the DIN PLC to stay powered; below this it force-shuts-down")
            .defineInRange("minVoltage", 20.0, 0.0, 100_000.0)
        PLC_BOOT_BLINK_TICKS = builder
            .comment("Duration the PLC work light blinks (loading) after power-on before going solid, in ticks")
            .defineInRange("bootBlinkTicks", 40, 0, 1_000_000)
        builder.pop()

        builder.push("plcIo")
        PLC_IO_MAX_VOLTAGE = builder
            .comment("Maximum voltage a PLC I/O pin can be driven to, in volts")
            .defineInRange("maxVoltage", 48.0, 0.0, 100_000.0)
        PLC_IO_MAX_CURRENT = builder
            // TODO: enforce this and break the module on overcurrent.
            .comment("Maximum current a PLC I/O pin can carry, in amperes (not enforced yet)")
            .defineInRange("maxCurrent", 1.0, 0.001, 100.0)
        PLC_IO_CURRENT_DRAW = builder
            .comment("PLC I/O module constant current draw from the internal 24V bus, in amperes (at nominal voltage)")
            .defineInRange("currentDraw", 0.1, 0.001, 100.0)
        builder.pop()

        builder.push("plcRelay")
        PLC_RELAY_COIL_CURRENT = builder
            .comment("PLC relay coil current draw from the internal 24V bus, in amperes (at nominal voltage)")
            .defineInRange("coilCurrent", 0.02, 0.001, 100.0)
        PLC_RELAY_CURRENT_DRAW = builder
            .comment("PLC relay module constant current draw from the internal 24V bus, in amperes (at nominal voltage)")
            .defineInRange("currentDraw", 0.01, 0.001, 100.0)
        builder.pop()

        SPEC = builder.build()
    }
}
