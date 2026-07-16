package com.alesharik.digitalgrid.din.item.plc.component

import com.alesharik.digitalgrid.Digitalgrid
import com.alesharik.digitalgrid.din.item.plc.DinRackPlcComponent
import net.minecraft.core.Registry
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.neoforged.neoforge.registries.RegistryBuilder

object PlcComponentRegistry {
    val KEY: ResourceKey<Registry<PlcComponentType>> = ResourceKey.createRegistryKey(
        ResourceLocation.fromNamespaceAndPath(Digitalgrid.ID, "plc_component")
    )
    val REGISTRY: Registry<PlcComponentType> = RegistryBuilder(KEY).create()

    class PlcComponentType(
        val displayName: Component,
        /** Item form of this component; a supplier because items register independently of this registry. */
        itemsFactory: () -> List<Item>,
        private val factory: () -> DinRackPlcComponent,
    ) {
        val items: List<Item> by lazy { itemsFactory() }

        fun createComponent(): DinRackPlcComponent = factory()
    }

//    ItemStack.set(
//    DigitalgridRegistry.DataComponents.PLC_COMPONENTS.get(),
//    PlcComponents(a.attached + a.added.map { it.componentId })
//    )
}
