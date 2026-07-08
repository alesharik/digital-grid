package com.alesharik.digitalgrid.din.item.plc

import com.alesharik.digitalgrid.din.item.DinRackPlcRelayEntity
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.peripheral.IPeripheral

class PlcRelayPeripheral(private val relay: DinRackPlcRelayEntity) : IPeripheral {
    override fun getType(): String = "plc_relay"

    /** Command the relay coil on or off. */
    @LuaFunction
    fun setState(on: Boolean) {
        relay.luaSetState(on)
    }

    /** The commanded coil state. */
    @LuaFunction
    fun getState(): Boolean = relay.luaGetState()

    /** Whether the contact is actually closed (commanded on and the rail is powered). */
    @LuaFunction
    fun isClosed(): Boolean = relay.luaIsClosed()

    override fun equals(other: IPeripheral?): Boolean = other === this
}
