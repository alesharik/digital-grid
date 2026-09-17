package com.alesharik.digitalgrid.recipe

import com.alesharik.digitalgrid.Digitalgrid
import com.alesharik.digitalgrid.DigitalgridRegistry
import com.alesharik.digitalgrid.DigitalgridTags
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
 * Attach recipe for the Assembly Table: with the PLC in the main slot and all [ingredients]
 * present across the material slots, the component [component] is attached to the PLC.
 *
 * Authored as data; the ingredient list here is the source of truth (NOT `PlcComponentType.items`).
 */
class AssemblyAttachRecipe(
    val component: ResourceLocation,
    val ingredients: List<Ingredient>,
) : Recipe<AssemblyTableRecipeInput> {

    override fun matches(input: AssemblyTableRecipeInput, level: Level): Boolean {
        if (!input.main.`is`(DigitalgridTags.Items.ASSEMBLABLE)) return false
        val available = HashMap<Item, Int>()
        for (stack in input.materials) {
            if (!stack.isEmpty) available.merge(stack.item, stack.count, Int::plus)
        }
        return AssemblyMatching.canCover(
            AssemblyMatching.requirements(ingredients), available, HashMap()
        )
    }

    override fun assemble(input: AssemblyTableRecipeInput, registries: HolderLookup.Provider): ItemStack =
        input.main.copy()

    override fun canCraftInDimensions(width: Int, height: Int): Boolean = true

    // DIN_RACK_PLC is a `by`-delegate val, so it resolves directly to `Item` (no `.get()`) —
    // confirmed by existing usage `Items.DIN_RACK_PLC.defaultInstance` in DigitalgridRegistry.
    override fun getResultItem(registries: HolderLookup.Provider): ItemStack =
        DigitalgridRegistry.Items.DIN_RACK_PLC.defaultInstance

    override fun isSpecial(): Boolean = true

    override fun getIngredients(): NonNullList<Ingredient> =
        NonNullList.create<Ingredient>().apply { addAll(ingredients) }

    override fun getSerializer(): RecipeSerializer<*> = AssemblyAttachSerializer

    override fun getType(): RecipeType<*> = ASSEMBLY_ATTACH_TYPE

    companion object {
        val CODEC: MapCodec<AssemblyAttachRecipe> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                ResourceLocation.CODEC.fieldOf("component").forGetter { it.component },
                Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").forGetter { it.ingredients },
            ).apply(instance, ::AssemblyAttachRecipe)
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, AssemblyAttachRecipe> = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, { it.component },
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list(255)), { it.ingredients },
            ::AssemblyAttachRecipe,
        )
    }
}

object AssemblyAttachSerializer : RecipeSerializer<AssemblyAttachRecipe> {
    override fun codec(): MapCodec<AssemblyAttachRecipe> = AssemblyAttachRecipe.CODEC
    override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, AssemblyAttachRecipe> =
        AssemblyAttachRecipe.STREAM_CODEC
}

val ASSEMBLY_ATTACH_TYPE: RecipeType<AssemblyAttachRecipe> =
    RecipeType.simple(ResourceLocation.fromNamespaceAndPath(Digitalgrid.ID, "assembly_attach"))
