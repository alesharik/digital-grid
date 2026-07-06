package com.alesharik.digitalgrid.din.item.plc

import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.peripheral.IPeripheral

class PlcPeripheral(private val plc: DinRackPlcEntity) : IPeripheral {
    override fun getType(): String = "plc"

    /** Turn the action light on or off. */
    @LuaFunction
    fun setActionLight(on: Boolean) {
        plc.luaSetActionLight(on)
    }

    @LuaFunction
    fun getActionLight(): Boolean = plc.luaActionLight()

    /** Reboot this controller's computer. */
    @LuaFunction(mainThread = true)
    fun reboot() {
        plc.luaReboot()
    }

    override fun equals(other: IPeripheral?): Boolean = other === this
}
