package com.alesharik.digitalgrid.din

import com.alesharik.digitalgrid.Digitalgrid
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.registries.RegistryBuilder

object DinRackRegistry {
    val KEY: ResourceKey<Registry<DinRackEntity>> = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Digitalgrid.ID, "din_rack"))

    val REGISTRY: Registry<DinRackEntity> = RegistryBuilder(KEY).create()
}
