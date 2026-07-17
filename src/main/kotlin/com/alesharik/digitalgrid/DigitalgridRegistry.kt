package com.alesharik.digitalgrid

import com.alesharik.digitalgrid.block.WatchdogTimerBlock
import com.alesharik.digitalgrid.block.WatchdogTimerBlockEntity
import com.alesharik.digitalgrid.block.WatchdogTimerBlockEntityRenderer
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.DinRackRegistry
import com.alesharik.digitalgrid.din.item.*
import com.alesharik.digitalgrid.din.item.plc.DinRackPlcEntity
import com.alesharik.digitalgrid.din.item.plc.DinRackPlcItem
import com.alesharik.digitalgrid.din.item.plc.component.PlcComponentRegistry
import com.alesharik.digitalgrid.din.item.plc.component.PlcComponents
import com.alesharik.digitalgrid.din.item.plc.component.PlcWatchdogComponent
import com.alesharik.digitalgrid.din.item.plc.component.PlcWirelessModemComponent
import com.alesharik.digitalgrid.din.rack.DinRackBlock
import com.alesharik.digitalgrid.din.rack.DinRackBlockEntity
import com.alesharik.digitalgrid.din.rack.DinRackBlockEntityRenderer
import com.alesharik.digitalgrid.din.rack.DinRackItem
import com.alesharik.digitalgrid.utils.Lang
import dan200.computercraft.api.peripheral.PeripheralCapability
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NewRegistryEvent
import org.patryk3211.powergrid.collections.ModdedItems
import thedarkcolour.kotlinforforge.neoforge.forge.getValue
import net.minecraft.core.registries.Registries as McRegistries

object DigitalgridRegistry {
    internal val CREATIVE_MODE_TABS: DeferredRegister<CreativeModeTab> = DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, Digitalgrid.ID)

    val TAB by CREATIVE_MODE_TABS.register("digitalgrid", { ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + Digitalgrid.ID))
            .icon { ItemStack(Items.DIN_RACK) }
            .displayItems { _: ItemDisplayParameters?, output: CreativeModeTab.Output ->
                output.accept(Items.DIN_RACK)
                output.accept(Items.WATCHDOG_TIMER)
                output.accept(Items.DIN_RACK_PATCH)
                output.accept(Items.DIN_RACK_CASING)
                output.accept(Items.DIN_RACK_CASING_DIGIBUS)
                output.accept(Items.DIN_RACK_BATTERY)
                output.accept(Items.DIN_RACK_POWER_SUPPLY)
                output.accept(Items.DIN_RACK_PLC)
                output.accept(Items.DIN_RACK_PLC.defaultInstance.apply {
                    set(DataComponents.PLC_COMPONENTS.get(), PlcComponents(listOf(
                        PlcComponentTypes.WATCHDOG.id,
                        PlcComponentTypes.WIRELESS_MODEM.id
                    )))
                })
                output.accept(Items.DIN_RACK_PLC.defaultInstance.apply {
                    set(DataComponents.PLC_COMPONENTS.get(), PlcComponents(listOf(
                        PlcComponentTypes.WATCHDOG.id,
                        PlcComponentTypes.ENDER_MODEM.id
                    )))
                })
                output.accept(Items.DIN_RACK_PLC_IO)
                output.accept(Items.DIN_RACK_PLC_RELAY)
                output.accept(Items.PLC_PROGRAMMER)
                output.accept(Items.PLASTIC)
                output.accept(Items.MICROPROCESSOR)
                output.accept(Items.CONTROL_CIRCUIT)
                output.accept(Items.WIRELESS_CIRCUIT)
                output.accept(Items.DIGIBUS_CONNECTOR)
            }
            .build()
    })

    fun registerRenderers(event: RegisterRenderers) {
        event.registerBlockEntityRenderer(BlockEntities.DIN_RACK) { DinRackBlockEntityRenderer() }
        event.registerBlockEntityRenderer(BlockEntities.WATCHDOG_TIMER) { WatchdogTimerBlockEntityRenderer() }
    }

    fun registerCapabilities(event: RegisterCapabilitiesEvent) {
        event.registerBlockEntity(
            PeripheralCapability.get(),
            BlockEntities.WATCHDOG_TIMER,
            { be, _ -> be.peripheral },
        )
    }

    object Blocks {
        internal val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(Digitalgrid.ID)

        val DIN_RACK by BLOCKS.register("din_rack") { -> DinRackBlock() }
        val WATCHDOG_TIMER by BLOCKS.register("watchdog_timer") { -> WatchdogTimerBlock(BlockBehaviour.Properties.of()) }
    }

    object BlockEntities {
        internal val BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Digitalgrid.ID)

        val DIN_RACK by BLOCK_ENTITIES.register("din_rack", { -> BlockEntityType.Builder.of(::DinRackBlockEntity, Blocks.DIN_RACK).build(null) })
        val WATCHDOG_TIMER by BLOCK_ENTITIES.register("watchdog_timer", { -> BlockEntityType.Builder.of(::WatchdogTimerBlockEntity, Blocks.WATCHDOG_TIMER).build(null) })
    }

    object Items {
        internal val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(Digitalgrid.ID)

        val DIN_RACK by ITEMS.register("din_rack", { -> BlockItem(Blocks.DIN_RACK, Item.Properties()) })
        val WATCHDOG_TIMER by ITEMS.register("watchdog_timer", { -> BlockItem(Blocks.WATCHDOG_TIMER, Item.Properties()) })

        val DIN_RACK_PATCH by ITEMS.register("din_rack_patch", { ->
            DinRackItem(
                Item.Properties(),
                DinRackPatchEntity::class.java
            )
        })
        val DIN_RACK_CASING by ITEMS.register("din_rack_casing", { ->
            DinRackItem(
                Item.Properties(),
                DinRackCasingEntity::class.java
            )
        })
        val DIN_RACK_CASING_DIGIBUS by ITEMS.register("din_rack_casing_digibus", { ->
            DinRackItem(
                Item.Properties(),
                DinRackDigibusCasingEntity::class.java
            )
        })
        val DIN_RACK_BATTERY by ITEMS.register("din_rack_battery", { ->
            DinRackItem(
                Item.Properties(),
                DinRackBatteryEntity::class.java
            )
        })
        val DIN_RACK_POWER_SUPPLY by ITEMS.register("din_rack_power_supply", { ->
            DinRackItem(
                Item.Properties(),
                DinRackPowerSupplyEntity::class.java
            )
        })
        val DIN_RACK_PLC by ITEMS.register("din_rack_plc", { -> DinRackPlcItem(Item.Properties()) })
        val DIN_RACK_PLC_IO by ITEMS.register("din_rack_plc_io", { ->
            DinRackItem(
                Item.Properties(),
                DinRackPlcIOEntity::class.java
            )
        })
        val DIN_RACK_PLC_RELAY by ITEMS.register("din_rack_plc_relay", { ->
            DinRackItem(
                Item.Properties(),
                DinRackPlcRelayEntity::class.java
            )
        })
        val PLC_PROGRAMMER by ITEMS.register("plc_programmer", { -> Item(Item.Properties()) })

        val PLASTIC by ITEMS.register("plastic", { -> Item(Item.Properties()) })
        val MICROPROCESSOR by ITEMS.register("microprocessor", { -> Item(Item.Properties()) })
        val CONTROL_CIRCUIT by ITEMS.register("control_circuit", { -> Item(Item.Properties()) })
        val WIRELESS_CIRCUIT by ITEMS.register("wireless_circuit", { -> Item(Item.Properties()) })
        val DIGIBUS_CONNECTOR by ITEMS.register("digibus_connector", { -> Item(Item.Properties()) })
    }

    object DinRackEntities {
        internal val DIN_RACK_ENTITIES: DeferredRegister<DinRackEntity> = DeferredRegister.create(DinRackRegistry.KEY, Digitalgrid.ID)

        val DIN_RACK_PATCH by DIN_RACK_ENTITIES.register("din_rack_patch", { -> DinRackPatchEntity() })
        val DIN_RACK_CASING by DIN_RACK_ENTITIES.register("din_rack_casing", { -> DinRackCasingEntity() })
        val DIN_RACK_CASING_DIGIBUS by DIN_RACK_ENTITIES.register("din_rack_casing_digibus", { -> DinRackDigibusCasingEntity() })
        val DIN_RACK_BATTERY by DIN_RACK_ENTITIES.register("din_rack_battery", { -> DinRackBatteryEntity() })
        val DIN_RACK_POWER_SUPPLY by DIN_RACK_ENTITIES.register("din_rack_power_supply", { -> DinRackPowerSupplyEntity() })
        val DIN_RACK_PLC by DIN_RACK_ENTITIES.register("din_rack_plc", { -> DinRackPlcEntity(Items.DIN_RACK_PLC.defaultInstance) })
        val DIN_RACK_PLC_IO by DIN_RACK_ENTITIES.register("din_rack_plc_io", { -> DinRackPlcIOEntity() })
        val DIN_RACK_PLC_RELAY by DIN_RACK_ENTITIES.register("din_rack_plc_relay", { -> DinRackPlcRelayEntity() })
    }

    object DataComponents {
        internal val DATA_COMPONENTS: DeferredRegister.DataComponents =
            DeferredRegister.createDataComponents(McRegistries.DATA_COMPONENT_TYPE, Digitalgrid.ID)

        val PLC_COMPONENTS: DeferredHolder<DataComponentType<*>, DataComponentType<PlcComponents>> =
            DATA_COMPONENTS.registerComponentType("plc_components") {
                it.persistent(PlcComponents.CODEC).networkSynchronized(PlcComponents.STREAM_CODEC)
            }
    }

    object PlcComponentTypes {
        internal val PLC_COMPONENT_TYPES: DeferredRegister<PlcComponentRegistry.PlcComponentType> =
            DeferredRegister.create(PlcComponentRegistry.KEY, Digitalgrid.ID)

        // Component registry names deliberately match the item names so the item<->component id mapping is 1:1.
        val WIRELESS_MODEM: DeferredHolder<PlcComponentRegistry.PlcComponentType, PlcComponentRegistry.PlcComponentType> =
            PLC_COMPONENT_TYPES.register("plc_wireless_modem") { ->
                PlcComponentRegistry.PlcComponentType(
                    Lang.translateItem("plc_wireless_modem"),
                    { listOf(Items.WIRELESS_CIRCUIT, net.minecraft.world.item.Items.IRON_INGOT) },
                    { PlcWirelessModemComponent(advanced = false) }
                )
            }

        val ENDER_MODEM: DeferredHolder<PlcComponentRegistry.PlcComponentType, PlcComponentRegistry.PlcComponentType> =
            PLC_COMPONENT_TYPES.register("plc_ender_modem") { ->
                PlcComponentRegistry.PlcComponentType(
                    Lang.translateItem("plc_ender_modem"),
                    { listOf(Items.WIRELESS_CIRCUIT, net.minecraft.world.item.Items.ENDER_PEARL) },
                    { PlcWirelessModemComponent(advanced = true) }
                )
            }

        val WATCHDOG: DeferredHolder<PlcComponentRegistry.PlcComponentType, PlcComponentRegistry.PlcComponentType> =
            PLC_COMPONENT_TYPES.register("plc_watchdog") { ->
                PlcComponentRegistry.PlcComponentType(
                    Lang.translateItem("plc_watchdog"),
                    { listOf(ModdedItems.IRON_WIRE.get(), net.minecraft.world.item.Items.CLOCK) },
                    { PlcWatchdogComponent() }
                )
            }
    }

    internal object Registries {
        fun register(bus: IEventBus) {
            bus.addListener { event: NewRegistryEvent ->
                event.register(DinRackRegistry.REGISTRY)
                event.register(PlcComponentRegistry.REGISTRY)
            }
            Blocks.BLOCKS.register(bus)
            Items.ITEMS.register(bus)
            BlockEntities.BLOCK_ENTITIES.register(bus)
            DinRackEntities.DIN_RACK_ENTITIES.register(bus)
            PlcComponentTypes.PLC_COMPONENT_TYPES.register(bus)
            DataComponents.DATA_COMPONENTS.register(bus)
            CREATIVE_MODE_TABS.register(bus)
        }
    }
}