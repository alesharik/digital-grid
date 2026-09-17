package com.alesharik.digitalgrid

import com.alesharik.digitalgrid.infra.asVar
import com.alesharik.digitalgrid.infra.block
import com.alesharik.digitalgrid.infra.modConfig
import com.alesharik.digitalgrid.infra.unit.*
import net.neoforged.neoforge.common.ModConfigSpec

object DigitalgridConfig {
    lateinit var CONFIG: Config
        private set

    val SPEC: ModConfigSpec = modConfig {
        CONFIG = Config(
            bus = block("bus") {
                Config.Bus(
                    voltageSpec = comment("Bus voltage")
                        .defineInRange("voltage", 24.0, 1.0, 60.0),
                    minVoltageSpec = comment("Minimum bus voltage for the DIN PLC to stay powered; below this it force-shuts-down")
                        .defineInRange("minVoltage", 20.0, 0.0, 100_000.0)
                )
            },
            battery = block("battery") {
                Config.Battery(
                    capacitySpec = comment("DIN battery module capacity in watt-hours")
                        .defineInRange("capacityWh", 5.0, 0.1, 1_000_000.0),
                    emfEmptySpec = comment("Minimal battery voltage")
                        .defineInRange("emfEmpty", 20.0, 1.0, 60.0),
                    emfSpanSpec = comment("Battery voltage threshold (max = min + span)")
                        .defineInRange("emfSpan", 4.0, 1.0, 60.0),
                    internalResistanceSpec = comment("Internal battery resistance")
                        .defineInRange("internalResistance", 0.5, 0.1, 1_000.0),
                    depletedResistanceSpec = comment("Battery resistance when depleted")
                        .defineInRange("depletedResistance", 10_000.0, 5_000.0, 100_000.0)
                )
            },
            powerSupply = block("powerSupply") {
                Config.PowerSupply(
                    maxPowerSpec = comment("DIN power supply maximum output power in watts")
                        .defineInRange("maxPower", 100.0, 1.0, 1_000_000.0),
                    minInputVoltageSpec = comment("Minimum input voltage for the DIN power supply to produce output")
                        .defineInRange("minInputVoltage", 8.0, 0.0, 100_000.0)
                )
            },
            plc = block("plc") {
                Config.Plc(
                    currentDrawSpec = comment("DIN PLC current draw from the internal 24V bus, in amperes (at nominal voltage)")
                        .defineInRange("currentDraw", 1.0, 0.001, 100.0),
                    storageSizeSpec = comment("PLC internal drive storage size")
                        .defineInRange("storageSize", 64 * 1024L, 4 * 1024, 1 * 1024 * 1024) // 64 KiB
                )
            },
            plcIo = block("plcIo") {
                Config.PlcIo(
                    maxVoltageSpec = comment("Maximum voltage a PLC I/O pin can be driven to, in volts")
                        .defineInRange("maxVoltage", 48.0, 0.0, 100_000.0),
                    maxCurrentSpec = comment("Maximum current a PLC I/O pin can carry, in amperes")
                        .defineInRange("maxCurrent", 1.0, 0.001, 100.0),
                    currentDrawSpec = comment("PLC I/O module constant current draw from the internal 24V bus, in amperes (at nominal voltage)")
                        .defineInRange("currentDraw", 0.1, 0.001, 100.0)
                )
            },
            plcRelay = block("plcRelay") {
                Config.PlcRelay(
                    currentDrawSpec = comment("PLC relay module constant current draw from the internal 24V bus, in amperes (at nominal voltage)")
                        .defineInRange("currentDraw", 0.1, 0.001, 100.0),
                    coilCurrentSpec = comment("PLC relay coil current draw from the internal 24V bus, in amperes (at nominal voltage)")
                        .defineInRange("coilCurrent", 0.2, 0.001, 100.0),
                    minVoltageSpec = comment("Minimum voltage for coil to work")
                        .defineInRange("minVoltage", 16.0, 1.0, 60.0)
                )
            },
            plcSpeaker = block("plcSpeaker") {
                Config.PlcSpeaker(
                    currentDrawSpec = comment("PLC module constant current draw from the internal 24V bus, in amperes (at nominal voltage)")
                        .defineInRange("currentDraw", 0.1, 0.001, 100.0),
                )
            },
            plcDrive = block("plcDrive") {
                Config.PlcDrive(
                    currentDrawSpec = comment("PLC module constant current draw from the internal 24V bus, in amperes (at nominal voltage)")
                        .defineInRange("currentDraw", 0.05, 0.001, 100.0),
                    diskCurrentDrawSpec = comment("Constant current draw when disk is present from the internal 24V bus, in amperes (at nominal voltage)")
                        .defineInRange("diskCurrentDraw", 0.2, 0.001, 100.0),
                )
            },
            dcdcConverter = block("dcdcConverter") {
                Config.DcDc(
                    maxPowerSpec = comment("DC-DC converter maximum output power in watts (above this it burns out)")
                        .defineInRange("maxPower", 100.0, 1.0, 1_000_000.0),
                    efficiencySpec = comment("DC-DC converter conversion efficiency (0-1)")
                        .defineInRange("efficiency", 0.9, 0.1, 1.0),
                    minInputVoltageSpec = comment("Minimum input voltage for the DC-DC converter to produce output")
                        .defineInRange("minInputVoltage", 6.0, 0.0, 100_000.0),
                    maxInputVoltageSpec = comment("Maximum input voltage; above this the DC-DC converter burns out")
                        .defineInRange("maxInputVoltage", 240.0, 1.0, 100_000.0),
                    outputVoltageSpec = comment("Default DC-DC converter output voltage setpoint")
                        .defineInRange("outputVoltage", 24.0, 0.0, 100_000.0),
                    minOutputVoltageSpec = comment("Minimum selectable DC-DC converter output voltage setpoint")
                        .defineInRange("minOutputVoltage", 2.0, 0.0, 100_000.0),
                    maxOutputVoltageSpec = comment("Maximum selectable DC-DC converter output voltage setpoint")
                        .defineInRange("maxOutputVoltage", 60.0, 1.0, 100_000.0)
                )
            },
            mosfet = block("mosfet") {
                Config.Mosfet(
                    thresholdVoltageSpec = comment("Default MOSFET gate threshold voltage")
                        .defineInRange("thresholdVoltage", 2.0, 0.1, 100.0),
                    minThresholdVoltageSpec = comment("Minimum selectable MOSFET gate threshold voltage")
                        .defineInRange("minThresholdVoltage", 1.0, 0.1, 100.0),
                    maxThresholdVoltageSpec = comment("Maximum selectable MOSFET gate threshold voltage")
                        .defineInRange("maxThresholdVoltage", 5.0, 0.1, 100.0),
                    transconductanceSpec = comment("MOSFET transconductance parameter k (A/V^2); higher means lower on-resistance")
                        .defineInRange("transconductance", 2.0, 0.001, 1000.0),
                    channelLengthModulationSpec = comment("MOSFET channel-length modulation lambda (1/V); output conductance in saturation")
                        .defineInRange("channelLengthModulation", 0.02, 0.0, 1.0),
                    maxPowerSpec = comment("MOSFET maximum dissipation in watts before it burns out")
                        .defineInRange("maxPower", 60.0, 1.0, 1_000_000.0),
                    overheatTemperatureSpec = comment("MOSFET overheat temperature in degrees Celsius")
                        .defineInRange("overheatTemperature", 125.0, 50.0, 1000.0)
                )
            }
        )
    }

    data class Config(
        val bus: Bus,
        val battery: Battery,
        val powerSupply: PowerSupply,
        val plc: Plc,
        val plcIo: PlcIo,
        val plcRelay: PlcRelay,
        val plcSpeaker: PlcSpeaker,
        val plcDrive: PlcDrive,
        val dcdcConverter: DcDc,
        val mosfet: Mosfet,
    ) {
        data class Bus(
            private val voltageSpec: ModConfigSpec.DoubleValue,
            private val minVoltageSpec: ModConfigSpec.DoubleValue,
        ) {
            /**
             * Bus voltage
             */
            val voltage: Volt by voltageSpec.asVar(::Volt)

            /**
             * Minimum bus voltage for the device to stay powered; below this it force-shuts-down
             */
            val minVoltage by minVoltageSpec.asVar(::Volt)
        }

        data class Battery(
            private val capacitySpec: ModConfigSpec.DoubleValue,
            private val emfEmptySpec: ModConfigSpec.DoubleValue,
            private val emfSpanSpec: ModConfigSpec.DoubleValue,
            private val internalResistanceSpec: ModConfigSpec.DoubleValue,
            private val depletedResistanceSpec: ModConfigSpec.DoubleValue,
        ) {
            /**
             * DIN battery module capacity in watt-hours
             */
            val capacity by capacitySpec.asVar(::WattHour)

            /**
             * Minimal battery voltage
             */
            val emfEmpty by emfEmptySpec.asVar(::Volt)

            /**
             * Battery voltage threshold (max = min + span)
             */
            val emfSpan by emfSpanSpec.asVar(::Volt)

            /**
             * Internal battery resistance
             */
            val internalResistance by internalResistanceSpec.asVar(::Ohm)

            /**
             * Battery resistance when depleted
             */
            val depletedResistance by depletedResistanceSpec.asVar(::Ohm)
        }

        data class PowerSupply(
            private val maxPowerSpec: ModConfigSpec.DoubleValue,
            private val minInputVoltageSpec: ModConfigSpec.DoubleValue,
        ) {
            /**
             * DIN power supply maximum output power in watts
             */
            val maxPower by maxPowerSpec.asVar(::Watt)

            /**
             * Minimum input voltage for the DIN power supply to produce output
             */
            val minInputVoltage by minInputVoltageSpec.asVar(::Volt)
        }

        data class Plc(
            private val currentDrawSpec: ModConfigSpec.DoubleValue,
            private val storageSizeSpec: ModConfigSpec.LongValue,
        ) {
            /**
             * DIN PLC current draw from the internal 24V bus, in amperes (at nominal voltage)
             */
            val currentDraw by currentDrawSpec.asVar(::Ampere)

            /**
             * PLC storage size, in bytes
             */
            val storageSize by storageSizeSpec.asVar()
        }

        data class PlcIo(
            private val maxVoltageSpec: ModConfigSpec.DoubleValue,
            private val maxCurrentSpec: ModConfigSpec.DoubleValue,
            private val currentDrawSpec: ModConfigSpec.DoubleValue,
        ) {
            /**
             * Maximum voltage a PLC I/O pin can be driven to, in volts
             */
            val maxVoltage by maxVoltageSpec.asVar(::Volt)

            /**
             * Maximum current a PLC I/O pin can carry, in amperes (not enforced yet)
             */
            val maxCurrent by maxCurrentSpec.asVar(::Ampere)

            /**
             * PLC I/O module constant current draw from the internal 24V bus, in amperes (at nominal voltage)
             */
            val currentDraw by currentDrawSpec.asVar(::Ampere)
        }

        data class PlcRelay(
            private val currentDrawSpec: ModConfigSpec.DoubleValue,
            private val coilCurrentSpec: ModConfigSpec.DoubleValue,
            private val minVoltageSpec: ModConfigSpec.DoubleValue,
        ) {
            /**
             * PLC relay module constant current draw from the internal 24V bus, in amperes (at nominal voltage)
             */
            val currentDraw by currentDrawSpec.asVar(::Ampere)

            /**
             * PLC relay coil current draw from the internal 24V bus, in amperes (at nominal voltage)
             */
            val coilCurrent by coilCurrentSpec.asVar(::Ampere)

            /**
             * Minimum voltage for coil to work
             */
            val minVoltage by minVoltageSpec.asVar(::Volt)
        }

        data class PlcSpeaker(
            private val currentDrawSpec: ModConfigSpec.DoubleValue,
        ) {
            /**
             * PLC module constant current draw from the internal 24V bus, in amperes (at nominal voltage)
             */
            val currentDraw by currentDrawSpec.asVar(::Ampere)
        }


        data class PlcDrive(
            private val currentDrawSpec: ModConfigSpec.DoubleValue,
            private val diskCurrentDrawSpec: ModConfigSpec.DoubleValue,
        ) {
            /**
             * PLC module constant current draw from the internal 24V bus, in amperes (at nominal voltage)
             */
            val currentDraw by currentDrawSpec.asVar(::Ampere)

            /**
             * Constant current draw when disk is present from the internal 24V bus, in amperes (at nominal voltage)
             */
            val diskCurrentDraw by diskCurrentDrawSpec.asVar(::Ampere)
        }

        data class DcDc(
            private val maxPowerSpec: ModConfigSpec.DoubleValue,
            private val efficiencySpec: ModConfigSpec.DoubleValue,
            private val minInputVoltageSpec: ModConfigSpec.DoubleValue,
            private val maxInputVoltageSpec: ModConfigSpec.DoubleValue,
            private val outputVoltageSpec: ModConfigSpec.DoubleValue,
            private val minOutputVoltageSpec: ModConfigSpec.DoubleValue,
            private val maxOutputVoltageSpec: ModConfigSpec.DoubleValue,
        ) {
            /**
             * DC-DC converter maximum output power in watts (above this it burns out)
             */
            val maxPower by maxPowerSpec.asVar(::Watt)

            /**
             * DC-DC converter conversion efficiency (0-1)
             */
            val efficiency by efficiencySpec.asVar()

            /**
             * Minimum input voltage for the DC-DC converter to produce output
             */
            val minInputVoltage by minInputVoltageSpec.asVar(::Volt)

            /**
             * Maximum input voltage; above this the DC-DC converter burns out
             */
            val maxInputVoltage by maxInputVoltageSpec.asVar(::Volt)

            /**
             * Default DC-DC converter output voltage setpoint
             */
            val outputVoltage by outputVoltageSpec.asVar(::Volt)

            /**
             * Minimum selectable DC-DC converter output voltage setpoint
             */
            val minOutputVoltage by minOutputVoltageSpec.asVar(::Volt)

            /**
             * Maximum selectable DC-DC converter output voltage setpoint
             */
            val maxOutputVoltage by maxOutputVoltageSpec.asVar(::Volt)
        }

        data class Mosfet(
            private val thresholdVoltageSpec: ModConfigSpec.DoubleValue,
            private val minThresholdVoltageSpec: ModConfigSpec.DoubleValue,
            private val maxThresholdVoltageSpec: ModConfigSpec.DoubleValue,
            private val transconductanceSpec: ModConfigSpec.DoubleValue,
            private val channelLengthModulationSpec: ModConfigSpec.DoubleValue,
            private val maxPowerSpec: ModConfigSpec.DoubleValue,
            private val overheatTemperatureSpec: ModConfigSpec.DoubleValue,
        ) {
            /**
             * Default MOSFET gate threshold voltage
             */
            val thresholdVoltage by thresholdVoltageSpec.asVar(::Volt)

            /**
             * Minimum selectable MOSFET gate threshold voltage
             */
            val minThresholdVoltage by minThresholdVoltageSpec.asVar(::Volt)

            /**
             * Maximum selectable MOSFET gate threshold voltage
             */
            val maxThresholdVoltage by maxThresholdVoltageSpec.asVar(::Volt)

            /**
             * MOSFET transconductance parameter k (A/V^2); higher means lower on-resistance
             */
            val transconductance by transconductanceSpec.asVar()

            /**
             * MOSFET channel-length modulation lambda (1/V); output conductance in saturation
             */
            val channelLengthModulation by channelLengthModulationSpec.asVar()

            /**
             * MOSFET maximum dissipation in watts before it burns out
             */
            val maxPower by maxPowerSpec.asVar(::Watt)

            /**
             * MOSFET overheat temperature in degrees Celsius
             */
            val overheatTemperature by overheatTemperatureSpec.asVar()
        }
    }
}
