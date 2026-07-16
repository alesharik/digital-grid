package com.alesharik.digitalgrid.din.item.plc

import com.alesharik.digitalgrid.DigitalgridRegistry
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.item.plc.component.PlcComponentRegistry
import com.alesharik.digitalgrid.din.rack.DinRackItem
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

class DinRackPlcItem(props: Properties) : DinRackItem(props, DinRackPlcEntity::class.java) {
    override fun createEntity(stack: ItemStack): DinRackEntity = DinRackPlcEntity(stack)

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltip: MutableList<Component>,
        flag: TooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltip, flag)
        val components = stack.get(DigitalgridRegistry.DataComponents.PLC_COMPONENTS.get())
            ?: return

        if (!components.ids.isEmpty()) {
            tooltip.add(Component.translatable("digitalgrid.tooltip.plc_components").withStyle(ChatFormatting.GRAY))
            for (id in components.ids) {
                val name = PlcComponentRegistry.REGISTRY.get(id)?.displayName ?: Component.literal(id.toString())
                tooltip.add(Component.literal(" - ").append(name).withStyle(ChatFormatting.DARK_GRAY))
            }
        }

    }
}
