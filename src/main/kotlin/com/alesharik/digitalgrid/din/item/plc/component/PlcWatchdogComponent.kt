package com.alesharik.digitalgrid.din.item.plc.component

import com.alesharik.digitalgrid.din.behavior.Behavior
import com.alesharik.digitalgrid.din.item.plc.DinRackPlcComponent
import com.alesharik.digitalgrid.infra.luaImpl
import com.alesharik.digitalgrid.utils.Lang
import com.alesharik.digitalgrid.utils.Tick
import com.alesharik.digitalgrid.utils.ticks
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.shared.computer.core.ServerComputer
import net.minecraft.ChatFormatting
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.server.ServerLifecycleHooks

class PlcWatchdogComponent: DinRackPlcComponent {
    private var enabled = false
    private var timeLimit: Tick = 0.ticks
    private var timer: Tick = 0.ticks

    private var ctx: Behavior.AttachContext? = null
    private var computer: ServerComputer? = null

    override fun onAttach(ctx: Behavior.AttachContext) {
        this.ctx = ctx
    }

    override fun onAttachComputer(computer: ServerComputer) {
        this.computer = computer
    }

    override fun onDetach() {
        ctx = null
        computer = null
    }

    override fun getPeripheral(): IPeripheral = PeripheralImpl()

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        enabled = tag.getBoolean("Enabled")
        timeLimit = tag.getInt("TimeLimit").ticks
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        tag.putInt("TimeLimit", timeLimit.tick)
        tag.putBoolean("Enabled", enabled)
    }

    override fun serverTick(level: ServerLevel, be: BlockEntity) {
        if (!enabled) {
            return
        }

        if (timer > timeLimit) {
            enabled = false
            computer?.reboot()
        } else {
            timer++
        }
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean) {
        Lang.translate("goggles.plc_component.watchdog").style(ChatFormatting.GRAY).forGoggles(tooltip, 1)
    }

    inner class PeripheralImpl : IPeripheral {
        override fun getType(): String = "watchdog"

        @LuaFunction
        fun isEnabled(): Boolean = enabled

        @LuaFunction
        fun getTimeout(): Int = timeLimit.seconds

        @LuaFunction
        fun enable() = luaImpl {
            if (timeLimit <= 0) {
                throw LuaException("Time limit should be set")
            }

            ServerLifecycleHooks.getCurrentServer()?.execute {
                enabled = true
                timer = 0.ticks
                ctx?.markChanged()
            }
        }

        @LuaFunction
        fun disable() = luaImpl {
            if (timeLimit <= 0) {
                throw LuaException("Time limit should be set")
            }

            ServerLifecycleHooks.getCurrentServer()?.execute {
                enabled = false
                timer = 0.ticks
                ctx?.markChanged()
            }
        }

        @LuaFunction
        fun setTimeout(time: Int) = luaImpl {
            if (time <= 0) {
                throw LuaException("Time limit should be positive")
            }
            ServerLifecycleHooks.getCurrentServer()?.execute {
                timeLimit = Tick.fromSeconds(time) // in ticks
                timer = 0.ticks
                ctx?.markChanged()
            }
        }

        @LuaFunction
        fun reset() = luaImpl {
            ServerLifecycleHooks.getCurrentServer()?.execute {
                timer = 0.ticks
            }
        }

        override fun equals(other: IPeripheral?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return true
        }
    }
}