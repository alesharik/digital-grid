package com.alesharik.digitalgrid

import com.alesharik.digitalgrid.utils.shaped
import com.alesharik.digitalgrid.utils.shapeless
import com.simibubi.create.AllItems
import com.simibubi.create.api.data.recipe.PressingRecipeGen
import dan200.computercraft.shared.ModRegistry
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.world.item.Items
import net.neoforged.neoforge.data.event.GatherDataEvent
import org.patryk3211.powergrid.collections.ModdedItems
import java.util.concurrent.CompletableFuture

object DigitalgridRecipies {
    fun gatherData(event: GatherDataEvent) {
        event.generator.apply {
            addProvider(event.includeServer(), CraftingRecipies(packOutput, event.lookupProvider))
            addProvider(event.includeServer(), PressingRecipes(packOutput, event.lookupProvider))
        }
    }

    class PressingRecipes(out: PackOutput, registries: CompletableFuture<HolderLookup.Provider>) :
        PressingRecipeGen(
            out,
            registries,
            Digitalgrid.ID
        ) {
        val PLASTIC = create("plastic", {
            it.require(AllItems.CARDBOARD).output(DigitalgridRegistry.Items.PLASTIC)
        })
    }

    class CraftingRecipies(out: PackOutput, registries: CompletableFuture<HolderLookup.Provider>) :
        RecipeProvider(out, registries) {
        override fun buildRecipes(r: RecipeOutput) {
            r.apply {
                shaped(DigitalgridRegistry.Items.DIGIBUS_CONNECTOR, 6) {
                    pattern("XXA")
                        .pattern("XXB")
                        .pattern("XXC")
                        .define('X', ModdedItems.INSULATED_COPPER_WIRE)
                        .define('A', Items.YELLOW_DYE)
                        .define('B', Items.RED_DYE)
                        .define('C', Items.BLUE_DYE)
                        .unlockedBy("has_copper_wire", has(ModdedItems.INSULATED_COPPER_WIRE))
                }

                shaped(DigitalgridRegistry.Blocks.DIN_RACK, 1) {
                    pattern(" CX")
                        .pattern("XXX")
                        .pattern(" CX")
                        .define('X', Items.IRON_INGOT)
                        .define('C', ModdedItems.WIRE)
                        .unlockedBy("has_copper_wire", has(ModdedItems.WIRE))
                }

                shaped(DigitalgridRegistry.Blocks.WATCHDOG_TIMER, 1) {
                    pattern(" X ")
                        .pattern("XCX")
                        .pattern(" X ")
                        .define('X', Items.IRON_INGOT)
                        .define('C', Items.CLOCK)
                        .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                }

                shaped(DigitalgridRegistry.Items.PLC_PROGRAMMER, 1) {
                    pattern("XPX")
                        .pattern("XCX")
                        .pattern("XQX")
                        .define('X', Items.IRON_INGOT)
                        .define('P', DigitalgridRegistry.Items.PLASTIC)
                        .define('C', ModRegistry.Items.POCKET_COMPUTER_ADVANCED.get())
                        .define('Q', DigitalgridRegistry.Items.DIGIBUS_CONNECTOR)
                        .unlockedBy("has_digibus_connector", has(DigitalgridRegistry.Items.DIGIBUS_CONNECTOR))
                }

                shaped(DigitalgridRegistry.Items.DIN_RACK_CASING, 3) {
                    pattern("XXX")
                        .pattern("I X")
                        .pattern("XXX")
                        .define('X', DigitalgridTags.Items.PLASTICS)
                        .define('I', Items.IRON_INGOT)
                        .unlockedBy("has_plastic", has(DigitalgridRegistry.Items.PLASTIC))
                        .unlockedBy("has_plastic_tag", has(DigitalgridTags.Items.PLASTICS))
                }

                shapeless(DigitalgridRegistry.Items.DIN_RACK_CASING_DIGIBUS, 1) {
                    requires(DigitalgridRegistry.Items.DIN_RACK_CASING)
                        .requires(DigitalgridRegistry.Items.DIGIBUS_CONNECTOR)
                        .unlockedBy("has_casing", has(DigitalgridRegistry.Items.DIN_RACK_CASING))
                }

                shaped(DigitalgridRegistry.Items.DIN_RACK_PATCH, 1) {
                    pattern("X")
                        .pattern("Y")
                        .pattern("X")
                        .define('X', AllItems.COPPER_NUGGET)
                        .define('Y', DigitalgridRegistry.Items.DIN_RACK_CASING)
                        .unlockedBy("has_casing", has(DigitalgridRegistry.Items.DIN_RACK_CASING))
                }
            }
        }
    }
}