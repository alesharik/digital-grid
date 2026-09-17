package com.alesharik.digitalgrid.block

import com.alesharik.digitalgrid.DigitalgridRegistry
import com.alesharik.digitalgrid.block.menu.AssemblyTableMenu
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * Render-only block entity for the Assembly Table multiblock master cell.
 *
 * [AssemblyTableBlockEntityRenderer] draws the whole table mesh from here. The table stores
 * NOTHING — crafting is handled transiently by [AssemblyTableMenu] (items returned on close),
 * exactly like a vanilla crafting table. This entity exists only to render and to be the
 * [MenuProvider] the block opens.
 */
class AssemblyTableBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(DigitalgridRegistry.BlockEntities.ASSEMBLY_TABLE, pos, state), MenuProvider {

    override fun getDisplayName(): Component =
        Component.translatable("block.digitalgrid.assembly_table")

    override fun createMenu(containerId: Int, playerInventory: Inventory, player: Player): AbstractContainerMenu =
        AssemblyTableMenu(containerId, playerInventory, ContainerLevelAccess.create(level!!, blockPos))
}
