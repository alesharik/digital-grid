package com.alesharik.digitalgrid.infra

import dan200.computercraft.api.lua.LuaException

inline fun <R> luaImpl(crossinline fn: () -> R): R {
    try {
        return fn()
    } catch (e: LuaException) {
        throw e
    } catch (e: Exception) {
        throw LuaException(e.message)
    }
}