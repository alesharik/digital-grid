package com.alesharik.digitalgrid.din.item.plc

import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.ILuaAPIFactory
import dan200.computercraft.api.lua.LuaFunction

class DinRackPlcLuaAPI(private val component: DinRackPlcComputerComponent): ILuaAPI {
    override fun getModuleName(): String = "plc"

    override fun getNames(): Array<out String> = arrayOf("plc")

    @LuaFunction(mainThread = true)
    fun setActionLight(on: Boolean) {
        component.actionLight = on
    }

    @LuaFunction
    fun getActionLight(): Boolean = component.actionLight

    @LuaFunction
    fun getRailVoltage(): Float = component.railVoltage

    object Factory: ILuaAPIFactory {
        override fun create(computer: IComputerSystem): ILuaAPI? =
            computer.getComponent(DinRackPlcComputerComponent.COMPONENT)?.let { DinRackPlcLuaAPI(it) }
    }
}