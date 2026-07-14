package com.alesharik.digitalgrid.din.behavior.digibus

import com.alesharik.digitalgrid.din.behavior.Behavior
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * Behavior, which indicates that this module can be attached to DigiBus
 */
interface DigibusBehavior: Behavior {
    /**
     * Create DigiBus wire with all peripherals. Server-side only
     */
    fun getWire(context: DigibusWireContext): DigibusWire

    interface DigibusWireContext {
        /**
         * Current level
         */
        val level: ServerLevel

        /**
         * Item, with which player added module
         */
        val item: ItemStack

        /**
         * Current block entity
         */
        val blockEntity: BlockEntity

        fun markChanged()
    }
}