package com.alesharik.digitalgrid.block

import com.alesharik.digitalgrid.DigitalgridRegistry
import com.alesharik.digitalgrid.infra.luaImpl
import com.alesharik.digitalgrid.utils.Tick
import com.alesharik.digitalgrid.utils.ticks
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.neoforge.server.ServerLifecycleHooks

class WatchdogTimerBlockEntity(pos: BlockPos, state: BlockState): BlockEntity(DigitalgridRegistry.BlockEntities.WATCHDOG_TIMER, pos, state) {
    var enabled = false
        private set
    var timeLimit: Tick = 0.ticks
        private set
    var timer: Tick = 0.ticks
        private set

    /**
     * State for renererer
     */
    internal var lastKnownClientTimer: Tick = 0.ticks

    /**
     * State for renderer
     */
    internal var blinkTicks: Tick = 0.ticks

    val peripheral: IPeripheral = PeripheralImpl()

    fun tick(
        level: Level,
        pos: BlockPos,
        state: BlockState
    ) {
        if (level.isClientSide) {
            return
        }

        if (!enabled) {
            return
        }

        if (timer > timeLimit) {
            enabled = false
            restartComputer(level, pos, state)
        } else {
            timer++
        }

        level.sendBlockUpdated(pos, state, state, 2)
    }

    private fun restartComputer(level: Level, pos: BlockPos, state: BlockState) {
        val facing: Direction = state.getValue(BlockStateProperties.FACING)
        val entity = level.getBlockEntity(pos.relative(facing))
        if (entity is AbstractComputerBlockEntity) {
            entity.serverComputer?.reboot()
        }
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        saveAdditional(tag, registries)
        tag.putInt("Timer", timer.tick)
        return tag
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun onDataPacket(
        net: Connection,
        pkt: ClientboundBlockEntityDataPacket,
        lookupProvider: HolderLookup.Provider
    ) {
        super.onDataPacket(net, pkt, lookupProvider)
        timer = pkt.tag.getInt("Timer").ticks
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        enabled = tag.getBoolean("Enabled")
        timeLimit = tag.getInt("TimeLimit").ticks
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putInt("TimeLimit", timeLimit.tick)
        tag.putBoolean("Enabled", enabled)
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
                setChanged()
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
                setChanged()
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
                setChanged()
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