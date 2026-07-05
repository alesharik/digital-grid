package com.alesharik.digitalgrid.client

import com.alesharik.digitalgrid.Digitalgrid
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import net.minecraft.resources.ResourceLocation

object PartialModels {
    val DIN_PATCH = dinModel("patch")

    // Forces the object initializer to run; must be called during client mod
    // construction so all partials exist before ModelEvent.RegisterAdditional.
    fun init() {}

    private fun dinModel(path: String) = model("din/$path")

    private fun model(path: String): PartialModel =
        PartialModel.of(ResourceLocation.fromNamespaceAndPath(Digitalgrid.ID, path))
}