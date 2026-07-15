package com.alesharik.digitalgrid

import com.alesharik.digitalgrid.utils.shaped
import com.simibubi.create.AllItems
import com.simibubi.create.api.data.recipe.PressingRecipeGen
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
            }
        }
    }
}