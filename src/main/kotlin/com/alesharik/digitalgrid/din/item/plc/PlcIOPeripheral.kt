package com.alesharik.digitalgrid.din.item.plc

import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.din.item.DinRackPlcIOEntity
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.peripheral.IPeripheral

/** Lua pins are 1-based; [DinRackPlcIOEntity] uses 0-based indices internally. */
class PlcIOPeripheral(private val io: DinRackPlcIOEntity) : IPeripheral {
    override fun getType(): String = "plc_io"

    /** Measured pin voltage relative to GND (the COMMON terminals), in volts. */
    @LuaFunction
    fun getVoltage(pin: Int): Double = io.luaGetVoltage(checkPin(pin))

    /** Drive the pin to [volts], converted from the rack's 24V rail. Reading still works. */
    @LuaFunction
    fun setVoltage(pin: Int, volts: Double) {
        val max = DigitalgridConfig.PLC_IO_MAX_VOLTAGE.get()
        if (!volts.isFinite() || volts < 0 || volts > max) {
            throw LuaException("Voltage out of range (0..$max)")
        }
        io.luaSetVoltage(checkPin(pin), volts)
    }

    /** Stop driving the pin (high impedance). */
    @LuaFunction
    fun clearVoltage(pin: Int) {
        io.luaClearVoltage(checkPin(pin))
    }

    @LuaFunction
    fun isDriven(pin: Int): Boolean = io.luaIsDriven(checkPin(pin))

    private fun checkPin(pin: Int): Int {
        if (pin < 1 || pin > DinRackPlcIOEntity.PIN_COUNT) {
            throw LuaException("Invalid pin $pin (1..${DinRackPlcIOEntity.PIN_COUNT})")
        }
        return pin - 1
    }

    override fun equals(other: IPeripheral?): Boolean = other === this
}
