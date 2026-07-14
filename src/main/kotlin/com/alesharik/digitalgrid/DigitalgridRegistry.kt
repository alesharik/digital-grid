package com.alesharik.digitalgrid

import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.DinRackRegistry
import com.alesharik.digitalgrid.din.item.*
import com.alesharik.digitalgrid.din.item.plc.DinRackPlcEntity
import com.alesharik.digitalgrid.din.rack.DinRackBlock
import com.alesharik.digitalgrid.din.rack.DinRackBlockEntity
import com.alesharik.digitalgrid.din.rack.DinRackBlockEntityRenderer
import com.alesharik.digitalgrid.din.rack.DinRackItem
import dan200.computercraft.shared.util.NonNegativeId
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NewRegistryEvent
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object DigitalgridRegistry {
    internal val CREATIVE_MODE_TABS: DeferredRegister<CreativeModeTab> = DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, Digitalgrid.ID)

    val TAB by CREATIVE_MODE_TABS.register("digitalgrid", { ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + Digitalgrid.ID))
            .icon { ItemStack(Items.DIN_RACK) }
            .displayItems { _: ItemDisplayParameters?, output: CreativeModeTab.Output ->
                output.accept(Items.DIN_RACK)
                output.accept(Items.DIN_RACK_PATCH)
                output.accept(Items.DIN_RACK_BATTERY)
                output.accept(Items.DIN_RACK_POWER_SUPPLY)
                output.accept(Items.DIN_RACK_PLC)
                output.accept(Items.DIN_RACK_PLC_IO)
                output.accept(Items.DIN_RACK_PLC_RELAY)
                output.accept(Items.PLC_PROGRAMMER)
                output.accept(Items.PLASTIC)
            }
            .build()
    })

    fun registerRenderers(event: RegisterRenderers) {
        event.registerBlockEntityRenderer(BlockEntities.DIN_RACK) { DinRackBlockEntityRenderer() }
    }

    object Blocks {
        internal val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(Digitalgrid.ID)

        val DIN_RACK by BLOCKS.register("din_rack") { -> DinRackBlock() }
    }

    object BlockEntities {
        internal val BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Digitalgrid.ID)

        val DIN_RACK by BLOCK_ENTITIES.register("din_rack", { -> BlockEntityType.Builder.of(::DinRackBlockEntity, Blocks.DIN_RACK).build(null) })
    }

    object Items {
        internal val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(Digitalgrid.ID)

        val DIN_RACK by ITEMS.register("din_rack", { -> BlockItem(Blocks.DIN_RACK, Item.Properties()) })
        val DIN_RACK_PATCH by ITEMS.register("din_rack_patch", { ->
            DinRackItem(
                Item.Properties(),
                DinRackPatchEntity::class.java
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
        val DIN_RACK_PLC by ITEMS.register("din_rack_plc", { ->
            DinRackItem(
                Item.Properties(),
                DinRackPlcEntity::class.java
            )
        })
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
    }

    object DataComponents {
        internal val DATA_COMPONENTS = DeferredRegister.createDataComponents(Digitalgrid.ID)

        /** Persistent per-item module id; names the module's peripheral on the PLC bus (e.g. plc_io_3). */
        val MODULE_ID: DataComponentType<NonNegativeId> by DATA_COMPONENTS.registerComponentType("module_id") { builder ->
            builder.persistent(NonNegativeId.CODEC).networkSynchronized(NonNegativeId.STREAM_CODEC)
        }
    }

    object DinRackEntities {
        internal val DIN_RACK_ENTITIES: DeferredRegister<DinRackEntity> = DeferredRegister.create(DinRackRegistry.KEY, Digitalgrid.ID)

        val DIN_RACK_PATCH by DIN_RACK_ENTITIES.register("din_rack_patch", { -> DinRackPatchEntity() })
        val DIN_RACK_BATTERY by DIN_RACK_ENTITIES.register("din_rack_battery", { -> DinRackBatteryEntity() })
        val DIN_RACK_POWER_SUPPLY by DIN_RACK_ENTITIES.register("din_rack_power_supply", { -> DinRackPowerSupplyEntity() })
        val DIN_RACK_PLC by DIN_RACK_ENTITIES.register("din_rack_plc", { -> DinRackPlcEntity() })
        val DIN_RACK_PLC_IO by DIN_RACK_ENTITIES.register("din_rack_plc_io", { -> DinRackPlcIOEntity() })
        val DIN_RACK_PLC_RELAY by DIN_RACK_ENTITIES.register("din_rack_plc_relay", { -> DinRackPlcRelayEntity() })
    }

    internal object Registries {
        fun register(bus: IEventBus) {
            bus.addListener { event: NewRegistryEvent -> event.register(DinRackRegistry.REGISTRY) }
            Blocks.BLOCKS.register(bus)
            Items.ITEMS.register(bus)
            BlockEntities.BLOCK_ENTITIES.register(bus)
            DinRackEntities.DIN_RACK_ENTITIES.register(bus)
            DataComponents.DATA_COMPONENTS.register(bus)
            CREATIVE_MODE_TABS.register(bus)
        }
    }
}