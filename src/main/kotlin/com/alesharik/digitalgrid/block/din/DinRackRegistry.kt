package com.alesharik.digitalgrid.block.din

import com.alesharik.digitalgrid.Digitalgrid
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation

object DinRackRegistry {
    val KEY = ResourceKey.createRegistryKey<DinRackEntity>(ResourceLocation.fromNamespaceAndPath(Digitalgrid.ID, "din_rack"))

}