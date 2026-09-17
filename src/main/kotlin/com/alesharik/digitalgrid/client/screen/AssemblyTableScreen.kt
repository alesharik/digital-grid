package com.alesharik.digitalgrid.client.screen

import com.alesharik.digitalgrid.Digitalgrid
import com.alesharik.digitalgrid.block.menu.AssemblyTableMenu
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory

class AssemblyTableScreen(menu: AssemblyTableMenu, playerInventory: Inventory, title: Component) :
    AbstractContainerScreen<AssemblyTableMenu>(menu, playerInventory, title) {

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        renderTooltip(graphics, mouseX, mouseY)
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x = (width - imageWidth) / 2
        val y = (height - imageHeight) / 2
        graphics.blit(BACKGROUND, x, y, 0, 0, imageWidth, imageHeight)
    }

    companion object {
        val BACKGROUND: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(Digitalgrid.ID, "textures/gui/assembly_table.png")
    }
}
