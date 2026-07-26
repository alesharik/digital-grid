package com.alesharik.digitalgrid

import com.alesharik.digitalgrid.recipe.BreadboardingAttachRecipe
import com.alesharik.digitalgrid.recipe.BreadboardingCraftRecipe
import com.alesharik.digitalgrid.recipe.CountedIngredient
import com.alesharik.digitalgrid.utils.PressingRecipeGenKt
import com.alesharik.digitalgrid.utils.SequencedAssemblyRecipeGenKt
import com.alesharik.digitalgrid.utils.shaped
import com.alesharik.digitalgrid.utils.shapeless
import com.simibubi.create.AllItems
import dan200.computercraft.shared.ModRegistry
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.Ingredient
import net.neoforged.neoforge.data.event.GatherDataEvent
import org.patryk3211.powergrid.collections.ModdedBlocks
import org.patryk3211.powergrid.collections.ModdedItems
import java.util.concurrent.CompletableFuture

object DigitalgridRecipies {
    fun gatherData(event: GatherDataEvent) {
        event.generator.apply {
            addProvider(event.includeServer(), CraftingRecipies(packOutput, event.lookupProvider))
            addProvider(event.includeServer(), PressingRecipes(packOutput, event.lookupProvider))
            addProvider(event.includeServer(), SequencedAssemblyRecipes(packOutput, event.lookupProvider))
        }
    }

    class PressingRecipes(out: PackOutput, registries: CompletableFuture<HolderLookup.Provider>) :
        PressingRecipeGenKt(
            out,
            registries,
            Digitalgrid.ID
        ) {

        val PLASTIC = create("plastic") {
            require(AllItems.CARDBOARD).output(DigitalgridRegistry.Items.PLASTIC)
        }
    }

    class SequencedAssemblyRecipes(out: PackOutput, registries: CompletableFuture<HolderLookup.Provider>) :
        SequencedAssemblyRecipeGenKt(out, registries, Digitalgrid.ID) {

        val DC_DC_CONVERTER = create("dc_dc_converter") {
            require(ModdedItems.EMPTY_CIRCUIT)
                .transitionTo(DigitalgridRegistry.Items.INCOMPLETE_DC_DC_CONVERTER)
                .addOutput(DigitalgridRegistry.Items.DC_DC_CONVERTER, 100f)
                .loops(1)
                .addDeployerStep(ModdedItems.DIODE)
                .addDeployerStep(ModdedItems.CAPACITOR)
                .addDeployerStep(ModdedItems.COPPER_COIL)
                .addDeployerStep(DigitalgridTags.Items.PLASTICS)
                .addPressStep()
        }

        val CONTROL_CIRCUIT = create("control_circuit") {
            require(ModdedItems.INTEGRATED_CIRCUIT)
                .transitionTo(DigitalgridRegistry.Items.INCOMPLETE_CONTROL_CIRCUIT)
                .addOutput(DigitalgridRegistry.Items.CONTROL_CIRCUIT, 100f)
                .loops(1)
                .addDeployerStep(ModdedItems.CAPACITOR)
                .addDeployerStep(ModdedItems.DIODE)
                .addDeployerStep(ModdedItems.BJT_PNP)
        }

        val WIRELESS_CIRCUIT = create("wireless_circuit") {
            require(ModdedItems.INTEGRATED_CIRCUIT)
                .transitionTo(DigitalgridRegistry.Items.INCOMPLETE_WIRELESS_CIRCUIT)
                .addOutput(DigitalgridRegistry.Items.WIRELESS_CIRCUIT, 100f)
                .loops(1)
                .addDeployerStep(AllItems.TRANSMITTER)
                .addDeployerStep(ModdedItems.COPPER_COIL)
                .addDeployerStep(ModdedItems.BJT_PNP)
        }
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

                shapeless(DigitalgridRegistry.Items.DIN_RACK_BATTERY, 1) {
                    requires(DigitalgridRegistry.Items.DIN_RACK_CASING)
                        .requires(ModdedBlocks.BATTERY)
                        .requires(DigitalgridRegistry.Items.CONTROL_CIRCUIT)
                        .unlockedBy("has_battery", has(ModdedBlocks.BATTERY))
                }
            }
        }
    }
}