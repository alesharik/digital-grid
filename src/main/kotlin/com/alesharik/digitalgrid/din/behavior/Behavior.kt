package com.alesharik.digitalgrid.din.behavior

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * Describes part of module, which does something
 */
interface Behavior {
    /**
     * Read data from persistent NBT storage
     */
    fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {}

    /**
     * Write data to persistent NBT storage
     */
    fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {}

    /**
     * Called by the rack when this module instance joins a placement — after creation and NBT
     * read, on both server and client, and again whenever the rack rebuilds its module list
     * (chunk load, client sync). [ctx] reads [Level]/[BlockPos] live from the owning rack, so a
     * module may cache it and use it later.
     */
    fun onAttach(ctx: AttachContext) {}

    /** Called when this module instance leaves a placement (removed by a player) or the rack unloads. */
    fun onDetach() {}

    fun serverTick(level: ServerLevel, be: BlockEntity) {}

    /** Handle to the owning rack, handed to a module in [onAttach]. Values are read live. */
    interface AttachContext {
        val level: Level
        val pos: BlockPos

        /** The owning rack block entity (e.g. as the host for a ComputerCraft modem). */
        val blockEntity: BlockEntity

        /** The backing item stack of this placement — persisted in rack NBT and returned to the player on removal. */
        val stack: ItemStack

        /** Mark the rack for saving (persist a mutated [stack] or module NBT). */
        fun markChanged()

        /** Push a full block-entity sync to clients. */
        fun requestSync()
    }
}