package com.alesharik.digitalgrid

import com.alesharik.digitalgrid.client.PartialModels
import dan200.computercraft.api.peripheral.PeripheralCapability
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers
import net.neoforged.neoforge.data.event.GatherDataEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist

/**
 * Main mod class.
 *
 * An example for blocks is in the `blocks` package of this mod.
 */
@Mod(Digitalgrid.ID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object Digitalgrid {
    const val ID = "digitalgrid"

    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        DigitalgridRegistry.Registries.register(MOD_BUS)
        ModLoadingContext.get().activeContainer.registerConfig(ModConfig.Type.SERVER, DigitalgridConfig.SPEC)

        runForDist(clientTarget = {
            PartialModels.init()
            Minecraft.getInstance()
        }, serverTarget = { "test" })
    }

    @SubscribeEvent
    fun registerEntityRenderers(event: RegisterRenderers) {
        DigitalgridRegistry.registerRenderers(event)
    }

    @SubscribeEvent
    fun gatherData(event: GatherDataEvent) {
        DigitalgridRecipies.gatherData(event)
    }

    @SubscribeEvent
    fun registerCapabilities(event: RegisterCapabilitiesEvent) {
        DigitalgridRegistry.registerCapabilities(event)
    }
}
