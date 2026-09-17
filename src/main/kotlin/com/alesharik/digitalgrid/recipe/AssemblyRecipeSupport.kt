package com.alesharik.digitalgrid.recipe

import kotlin.jvm.JvmName
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeInput

/**
 * Input for a Assembly Table recipe: the main slot (index 0) plus the material slots (1..n).
 * Built by `AssemblyTableMenu` from its inventory.
 */
class AssemblyTableRecipeInput(
    val main: ItemStack,
    val materials: List<ItemStack>,
) : RecipeInput {
    override fun size(): Int = 1 + materials.size

    override fun getItem(index: Int): ItemStack = when (index) {
        0 -> main
        in 1..materials.size -> materials[index - 1]
        else -> throw IllegalArgumentException("No item for index $index")
    }
}

/**
 * An [Ingredient] paired with how many matching items it consumes. Attach recipes use count 1;
 * craft recipes may use any count. A single matching algorithm ([AssemblyMatching]) serves both.
 */
data class CountedIngredient(
    val ingredient: Ingredient,
    val count: Int,
) {
    companion object {
        val CODEC: Codec<CountedIngredient> = RecordCodecBuilder.create { instance ->
            instance.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter { it.ingredient },
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter { it.count },
            ).apply(instance, ::CountedIngredient)
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CountedIngredient> = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, { it.ingredient },
            ByteBufCodecs.VAR_INT, { it.count },
            ::CountedIngredient,
        )
    }
}

/**
 * Reservation-based ingredient matching shared by attach and craft. [canCover] returns true and
 * updates [reserved] iff every requirement can be covered by [available] minus what is already
 * reserved, assigning item counts greedily. [available] is read-only; [reserved] is mutated only on
 * success (so a failed check leaves it untouched), letting several recipes reserve against one pool.
 */
object AssemblyMatching {
    fun canCover(
        requirements: List<Pair<Ingredient, Int>>,
        available: Map<Item, Int>,
        reserved: MutableMap<Item, Int>,
    ): Boolean {
        val pending = HashMap(reserved)
        for ((ingredient, need) in requirements) {
            var remaining = need
            for ((item, total) in available) {
                if (remaining <= 0) break
                val free = total - (pending[item] ?: 0)
                if (free <= 0) continue
                if (!ingredient.test(ItemStack(item))) continue
                val take = minOf(remaining, free)
                pending.merge(item, take, Int::plus)
                remaining -= take
            }
            if (remaining > 0) return false
        }
        reserved.clear()
        reserved.putAll(pending)
        return true
    }

    @JvmName("requirementsFromIngredients")
    fun requirements(ingredients: List<Ingredient>): List<Pair<Ingredient, Int>> =
        ingredients.map { it to 1 }

    @JvmName("requirementsFromCounted")
    fun requirements(ingredients: List<CountedIngredient>): List<Pair<Ingredient, Int>> =
        ingredients.map { it.ingredient to it.count }
}
