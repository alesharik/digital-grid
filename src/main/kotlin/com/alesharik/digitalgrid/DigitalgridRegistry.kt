package com.alesharik.digitalgrid

import com.alesharik.digitalgrid.block.*
import com.alesharik.digitalgrid.block.menu.AssemblyTableMenu
import com.alesharik.digitalgrid.circuit.component.DcDcConverterComponent
import com.alesharik.digitalgrid.circuit.component.NChannelMosfetComponent
import com.alesharik.digitalgrid.circuit.component.PChannelMosfetComponent
import com.alesharik.digitalgrid.client.screen.AssemblyTableScreen
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.DinRackRegistry
import com.alesharik.digitalgrid.din.item.*
import com.alesharik.digitalgrid.din.item.plc.DinRackPlcEntity
import com.alesharik.digitalgrid.din.item.plc.DinRackPlcItem
import com.alesharik.digitalgrid.din.item.plc.component.*
import com.alesharik.digitalgrid.din.rack.DinRackBlock
import com.alesharik.digitalgrid.din.rack.DinRackBlockEntity
import com.alesharik.digitalgrid.din.rack.DinRackBlockEntityRenderer
import com.alesharik.digitalgrid.din.rack.DinRackItem
import com.alesharik.digitalgrid.recipe.AssemblyAttachRecipe
import com.alesharik.digitalgrid.recipe.AssemblyAttachSerializer
import com.alesharik.digitalgrid.recipe.AssemblyCraftRecipe
import com.alesharik.digitalgrid.recipe.AssemblyCraftSerializer
import com.alesharik.digitalgrid.utils.Lang
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem
import dan200.computercraft.api.peripheral.PeripheralCapability
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NewRegistryEvent
import org.patryk3211.powergrid.circuits.components.ComponentRegistry
import org.patryk3211.powergrid.circuits.schematic.ComponentFootprint
import org.patryk3211.powergrid.collections.ModdedItems
import thedarkcolour.kotlinforforge.neoforge.forge.getValue
import net.minecraft.core.registries.Registries as McRegistries
import org.patryk3211.powergrid.circuits.components.Component as PgComponent

object DigitalgridRegistry {
    internal val CREATIVE_MODE_TABS: DeferredRegister<CreativeModeTab> = DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, Digitalgrid.ID)

    val TAB by CREATIVE_MODE_TABS.register("digitalgrid", { ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + Digitalgrid.ID))
            .icon { ItemStack(Items.DIN_RACK) }
            .displayItems { _: ItemDisplayParameters?, output: CreativeModeTab.Output ->
                output.accept(Items.DIN_RACK)
                output.accept(Items.WATCHDOG_TIMER)
                output.accept(Items.ASSEMBLY_TABLE)
                output.accept(Items.DIN_RACK_PATCH)
                output.accept(Items.DIN_RACK_CASING)
                output.accept(Items.DIN_RACK_CASING_DIGIBUS)
                output.accept(Items.DIN_RACK_PLC_SPEAKER)
                output.accept(Items.DIN_RACK_BATTERY)
                output.accept(Items.DIN_RACK_POWER_SUPPLY)
                output.accept(Items.DIN_RACK_PLC)
                output.accept(Items.DIN_RACK_PLC.defaultInstance.apply {
                    set(DataComponents.PLC_COMPONENTS.get(), PlcComponents(listOf(
                        PlcComponentTypes.WATCHDOG.id,
                        PlcComponentTypes.WIRELESS_MODEM.id,
                        PlcComponentTypes.BEEPER.id
                    )))
                })
                output.accept(Items.DIN_RACK_PLC.defaultInstance.apply {
                    set(DataComponents.PLC_COMPONENTS.get(), PlcComponents(listOf(
                        PlcComponentTypes.WATCHDOG.id,
                        PlcComponentTypes.ENDER_MODEM.id,
                        PlcComponentTypes.BEEPER.id
                    )))
                })
                output.accept(Items.DIN_RACK_PLC_IO)
                output.accept(Items.DIN_RACK_PLC_RELAY)
                output.accept(Items.DIN_RACK_PLC_SPEAKER)
                output.accept(Items.DIN_RACK_PLC_DRIVE)
                output.accept(Items.PLC_PROGRAMMER)
                output.accept(Items.PLASTIC)
                output.accept(Items.MICROPROCESSOR)
                output.accept(Items.CONTROL_CIRCUIT)
                output.accept(Items.WIRELESS_CIRCUIT)
                output.accept(Items.DIGIBUS_CONNECTOR)
                output.accept(Items.DC_DC_CONVERTER)
                output.accept(Items.MOSFET_N)
                output.accept(Items.MOSFET_P)
            }
            .build()
    })

    fun registerRenderers(event: RegisterRenderers) {
        event.registerBlockEntityRenderer(BlockEntities.DIN_RACK) { DinRackBlockEntityRenderer() }
        event.registerBlockEntityRenderer(BlockEntities.WATCHDOG_TIMER) { WatchdogTimerBlockEntityRenderer() }
        event.registerBlockEntityRenderer(BlockEntities.ASSEMBLY_TABLE) { AssemblyTableBlockEntityRenderer() }
    }

    fun registerScreens(event: RegisterMenuScreensEvent) {
        event.register(Menus.ASSEMBLY_TABLE.get()) { menu, inventory, title ->
            AssemblyTableScreen(menu, inventory, title)
        }
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
        val ASSEMBLY_TABLE by BLOCKS.register("assembly_table") { -> AssemblyTableBlock(BlockBehaviour.Properties.of().noOcclusion()) }
    }

    object BlockEntities {
        internal val BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Digitalgrid.ID)

        val DIN_RACK by BLOCK_ENTITIES.register("din_rack", { -> BlockEntityType.Builder.of(::DinRackBlockEntity, Blocks.DIN_RACK).build(null) })
        val WATCHDOG_TIMER by BLOCK_ENTITIES.register("watchdog_timer", { -> BlockEntityType.Builder.of(::WatchdogTimerBlockEntity, Blocks.WATCHDOG_TIMER).build(null) })
        val ASSEMBLY_TABLE by BLOCK_ENTITIES.register("assembly_table", { -> BlockEntityType.Builder.of(::AssemblyTableBlockEntity, Blocks.ASSEMBLY_TABLE).build(null) })
    }

    object Items {
        internal val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(Digitalgrid.ID)

        val DIN_RACK by ITEMS.register("din_rack", { -> BlockItem(Blocks.DIN_RACK, Item.Properties()) })
        val WATCHDOG_TIMER by ITEMS.register("watchdog_timer", { -> BlockItem(Blocks.WATCHDOG_TIMER, Item.Properties()) })
        val ASSEMBLY_TABLE by ITEMS.register("assembly_table", { -> BlockItem(Blocks.ASSEMBLY_TABLE, Item.Properties()) })

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
        val DIN_RACK_PLC_SPEAKER by ITEMS.register("din_rack_plc_speaker", { ->
            DinRackItem(
                Item.Properties(),
                DinRackPlcSpeakerEntity::class.java
            )
        })
        val DIN_RACK_PLC_DRIVE by ITEMS.register("din_rack_plc_drive", { ->
            DinRackItem(
                Item.Properties(),
                DinRackPlcDriveEntity::class.java
            )
        })
        val PLC_PROGRAMMER by ITEMS.register("plc_programmer", { -> Item(Item.Properties()) })

        val PLASTIC by ITEMS.register("plastic", { -> Item(Item.Properties()) })
        val MICROPROCESSOR by ITEMS.register("microprocessor", { -> Item(Item.Properties()) })
        val CONTROL_CIRCUIT by ITEMS.register("control_circuit", { -> Item(Item.Properties()) })
        val WIRELESS_CIRCUIT by ITEMS.register("wireless_circuit", { -> Item(Item.Properties()) })
        val DIGIBUS_CONNECTOR by ITEMS.register("digibus_connector", { -> Item(Item.Properties()) })

        val DC_DC_CONVERTER by ITEMS.register("dc_dc_converter", { -> Item(Item.Properties()) })

        val MOSFET_N by ITEMS.register("mosfet_n", { -> Item(Item.Properties()) })
        val MOSFET_P by ITEMS.register("mosfet_p", { -> Item(Item.Properties()) })

        val INCOMPLETE_DC_DC_CONVERTER by ITEMS.register("incomplete_dc_dc_converter", { -> SequencedAssemblyItem(Item.Properties()) })
        val INCOMPLETE_CONTROL_CIRCUIT by ITEMS.register("incomplete_control_circuit", { -> SequencedAssemblyItem(Item.Properties()) })
        val INCOMPLETE_WIRELESS_CIRCUIT by ITEMS.register("incomplete_wireless_circuit", { -> SequencedAssemblyItem(Item.Properties()) })
        val INCOMPLETE_MICROPROCESSOR by ITEMS.register("incomplete_microprocessor", { -> SequencedAssemblyItem(Item.Properties()) })
    }

    object Menus {
        internal val MENUS: DeferredRegister<MenuType<*>> =
            DeferredRegister.create(BuiltInRegistries.MENU, Digitalgrid.ID)

        val ASSEMBLY_TABLE: DeferredHolder<MenuType<*>, MenuType<AssemblyTableMenu>> =
            MENUS.register("assembly_table") { ->
                IMenuTypeExtension.create(AssemblyTableMenu::clientFactory)
            }
    }

    object RecipeSerializers {
        internal val RECIPE_SERIALIZERS: DeferredRegister<RecipeSerializer<*>> =
            DeferredRegister.create(McRegistries.RECIPE_SERIALIZER, Digitalgrid.ID)

        val ASSEMBLY_ATTACH: DeferredHolder<RecipeSerializer<*>, RecipeSerializer<AssemblyAttachRecipe>> =
            RECIPE_SERIALIZERS.register("assembly_attach") { -> AssemblyAttachSerializer }

        val ASSEMBLY_CRAFT: DeferredHolder<RecipeSerializer<*>, RecipeSerializer<AssemblyCraftRecipe>> =
            RECIPE_SERIALIZERS.register("assembly_craft") { -> AssemblyCraftSerializer }
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
        val DIN_RACK_PLC_SPEAKER by DIN_RACK_ENTITIES.register("din_rack_plc_speaker", { -> DinRackPlcSpeakerEntity() })
        val DIN_RACK_PLC_DRIVE by DIN_RACK_ENTITIES.register("din_rack_plc_drive", { -> DinRackPlcDriveEntity() })
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

        val BEEPER: DeferredHolder<PlcComponentRegistry.PlcComponentType, PlcComponentRegistry.PlcComponentType> =
            PLC_COMPONENT_TYPES.register("plc_beeper") { ->
                PlcComponentRegistry.PlcComponentType(
                    Lang.translateItem("plc_beeper"),
                    { listOf(ModdedItems.IRON_WIRE.get(), net.minecraft.world.item.Items.NOTE_BLOCK) },
                    { PlcBeeperComponent() }
                )
            }
    }

    /**
     * Power Grid circuit-board components (the `circuit_design_table` -> `circuit_board` flow), as
     * opposed to DIN rack modules.
     *
     * `ComponentRegistry.REGISTRY_KEY` is created by Power Grid's own NeoForge platform code
     * (`NewRegistryEvent.create(new RegistryBuilder<>(...))` in `PowerGridImpl`), so a plain
     * [DeferredRegister] binds to it — Registrate is not needed here, and Power Grid's
     * `ComponentBuilder` is Registrate-only, so the footprint is built by hand below.
     */
    object CircuitComponents {
        internal val COMPONENTS: DeferredRegister<PgComponent> =
            DeferredRegister.create(ComponentRegistry.REGISTRY_KEY, Digitalgrid.ID)

        private const val DC_DC_KEY_BASE = "component.${Digitalgrid.ID}.dc_dc_converter"

        // ComponentFootprint.Builder(w, h) has no translation-key base, so the
        // addPad(x, y, node, String, String) convenience overload that Registrate's
        // ComponentBuilder.footprint relies on is unavailable — pass the components directly.
        private val DC_DC_FOOTPRINT: ComponentFootprint = ComponentFootprint.Builder(4, 5)
            .addPad(0, 1, 0, Component.translatable("$DC_DC_KEY_BASE.vin_plus"), Component.translatable("$DC_DC_KEY_BASE.vin_plus.short"))
            .addPad(3, 1, 2, Component.translatable("$DC_DC_KEY_BASE.vout_plus"), Component.translatable("$DC_DC_KEY_BASE.vout_plus.short"))
            .addPad(0, 3, 1, Component.translatable("$DC_DC_KEY_BASE.vin_minus"), Component.translatable("$DC_DC_KEY_BASE.vin_minus.short"))
            .addPad(3, 3, 3, Component.translatable("$DC_DC_KEY_BASE.vout_minus"), Component.translatable("$DC_DC_KEY_BASE.vout_minus.short"))
            .withItem()
            .withOutline()
            .build()

        val DC_DC_CONVERTER by COMPONENTS.register("dc_dc_converter", { -> DcDcConverterComponent(DC_DC_FOOTPRINT) })

        private const val MOSFET_KEY_BASE = "component.${Digitalgrid.ID}.mosfet"

        // Terminal indices are the same in both variants (0 = drain, 1 = gate, 2 = source); only
        // the pad positions mirror, the same way PowerGrid's NPN puts collector at x=0 and PNP
        // puts it at x=2.
        private val MOSFET_N_FOOTPRINT: ComponentFootprint = ComponentFootprint.Builder(3, 2)
            .addPad(0, 0, 0, Component.translatable("$MOSFET_KEY_BASE.drain"), Component.translatable("$MOSFET_KEY_BASE.drain.short"))
            .addPad(1, 1, 1, Component.translatable("$MOSFET_KEY_BASE.gate"), Component.translatable("$MOSFET_KEY_BASE.gate.short"))
            .addPad(2, 0, 2, Component.translatable("$MOSFET_KEY_BASE.source"), Component.translatable("$MOSFET_KEY_BASE.source.short"))
            .build()

        private val MOSFET_P_FOOTPRINT: ComponentFootprint = ComponentFootprint.Builder(3, 2)
            .addPad(2, 0, 0, Component.translatable("$MOSFET_KEY_BASE.drain"), Component.translatable("$MOSFET_KEY_BASE.drain.short"))
            .addPad(1, 1, 1, Component.translatable("$MOSFET_KEY_BASE.gate"), Component.translatable("$MOSFET_KEY_BASE.gate.short"))
            .addPad(0, 0, 2, Component.translatable("$MOSFET_KEY_BASE.source"), Component.translatable("$MOSFET_KEY_BASE.source.short"))
            .build()

        val MOSFET_N by COMPONENTS.register("mosfet_n", { -> NChannelMosfetComponent(MOSFET_N_FOOTPRINT) })
        val MOSFET_P by COMPONENTS.register("mosfet_p", { -> PChannelMosfetComponent(MOSFET_P_FOOTPRINT) })
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
            Menus.MENUS.register(bus)
            RecipeSerializers.RECIPE_SERIALIZERS.register(bus)
            DinRackEntities.DIN_RACK_ENTITIES.register(bus)
            PlcComponentTypes.PLC_COMPONENT_TYPES.register(bus)
            DataComponents.DATA_COMPONENTS.register(bus)
            CircuitComponents.COMPONENTS.register(bus)
            CREATIVE_MODE_TABS.register(bus)
        }
    }
}