package com.alesharik.digitalgrid.recipe

import com.alesharik.digitalgrid.Digitalgrid
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level

/**
 * Craft recipe for the Assembly Table: with all [ingredients] present across the material
 * slots and the main slot empty, the [result] is produced. Authored as data; source of truth.
 */
class AssemblyCraftRecipe(
    val ingredients: List<CountedIngredient>,
    val result: ItemStack,
) : Recipe<AssemblyTableRecipeInput> {

    override fun matches(input: AssemblyTableRecipeInput, level: Level): Boolean {
        val available = HashMap<Item, Int>()
        for (stack in input.materials) {
            if (!stack.isEmpty) available.merge(stack.item, stack.count, Int::plus)
        }
        return AssemblyMatching.canCover(
            AssemblyMatching.requirements(ingredients), available, HashMap()
        )
    }

    override fun assemble(input: AssemblyTableRecipeInput, registries: HolderLookup.Provider): ItemStack =
        result.copy()

    override fun canCraftInDimensions(width: Int, height: Int): Boolean = true

    override fun getResultItem(registries: HolderLookup.Provider): ItemStack = result

    override fun isSpecial(): Boolean = true

    override fun getIngredients(): NonNullList<Ingredient> =
        NonNullList.create<Ingredient>().apply { ingredients.forEach { add(it.ingredient) } }

    override fun getSerializer(): RecipeSerializer<*> = AssemblyCraftSerializer

    override fun getType(): RecipeType<*> = ASSEMBLY_CRAFT_TYPE

    companion object {
        val CODEC: MapCodec<AssemblyCraftRecipe> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                CountedIngredient.CODEC.listOf().fieldOf("ingredients").forGetter { it.ingredients },
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter { it.result },
            ).apply(instance, ::AssemblyCraftRecipe)
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, AssemblyCraftRecipe> = StreamCodec.composite(
            CountedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list(255)), { it.ingredients },
            ItemStack.STREAM_CODEC, { it.result },
            ::AssemblyCraftRecipe,
        )
    }
}

object AssemblyCraftSerializer : RecipeSerializer<AssemblyCraftRecipe> {
    override fun codec(): MapCodec<AssemblyCraftRecipe> = AssemblyCraftRecipe.CODEC
    override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, AssemblyCraftRecipe> =
        AssemblyCraftRecipe.STREAM_CODEC
}

val ASSEMBLY_CRAFT_TYPE: RecipeType<AssemblyCraftRecipe> =
    RecipeType.simple(ResourceLocation.fromNamespaceAndPath(Digitalgrid.ID, "assembly_craft"))
