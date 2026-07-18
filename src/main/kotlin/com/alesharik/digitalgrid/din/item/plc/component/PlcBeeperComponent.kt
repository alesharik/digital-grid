package com.alesharik.digitalgrid.din.item.plc.component

import com.alesharik.digitalgrid.din.behavior.Behavior
import com.alesharik.digitalgrid.din.item.plc.DinRackPlcComponent
import com.alesharik.digitalgrid.infra.luaImpl
import com.alesharik.digitalgrid.utils.Lang
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.peripheral.IPeripheral
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.neoforged.neoforge.server.ServerLifecycleHooks

class PlcBeeperComponent : DinRackPlcComponent {
    @Volatile
    private var ctx: Behavior.AttachContext? = null
    private val peripheral = PeripheralImpl()

    override fun onAttach(ctx: Behavior.AttachContext) {
        this.ctx = ctx
    }

    override fun onDetach() {
        ctx = null
    }

    override fun getPeripheral(): IPeripheral = peripheral

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean) {
        Lang.translate("goggles.plc_component.beeper").style(ChatFormatting.GRAY).forGoggles(tooltip, 1)
    }

    inner class PeripheralImpl : IPeripheral {
        override fun getType(): String = "beeper"

        @LuaFunction
        fun beep() = beepWithPitch(1.0)

        @LuaFunction
        fun beepWithPitch(pitch: Double) = luaImpl {
            if (pitch < 0.5) {
                throw LuaException("Pitch must be greater than 0.5 but was $pitch")
            }
            if (pitch > 2) {
                throw LuaException("Pitch must be less than 2 but was $pitch")
            }

            ServerLifecycleHooks.getCurrentServer()?.execute {
                val context = ctx ?: return@execute
                val level = context.level
                if (level is ServerLevel) {
                    level.playSound(null, context.pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 1.0f, pitch.toFloat())
                }
            }
        }

        override fun equals(other: IPeripheral?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return true
        }
    }
}
