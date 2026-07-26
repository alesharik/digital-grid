package com.alesharik.digitalgrid.utils

import com.simibubi.create.api.data.recipe.PressingRecipeGen
import com.simibubi.create.api.data.recipe.SequencedAssemblyRecipeGen
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe
import com.simibubi.create.content.kinetics.press.PressingRecipe
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeBuilder
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.ShapedRecipeBuilder
import net.minecraft.data.recipes.ShapelessRecipeBuilder
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.level.ItemLike
import java.util.concurrent.CompletableFuture

inline fun RecipeOutput.shaped(
    item: ItemLike,
    count: Int,
    crossinline f: ShapedRecipeBuilder.() -> ShapedRecipeBuilder
) {
    val b = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, item, count)
    f(b).save(this)
}

inline fun RecipeOutput.shapeless(
    item: ItemLike,
    count: Int,
    crossinline f: ShapelessRecipeBuilder.() -> ShapelessRecipeBuilder
) {
    val b = ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, item, count)
    f(b).save(this)
}

open class PressingRecipeGenKt(out: PackOutput, registries: CompletableFuture<HolderLookup.Provider>, defaultNamespace: String) :
    PressingRecipeGen(
        out,
        registries,
        defaultNamespace,
    ) {

    fun create(name: String, f: StandardProcessingRecipe.Builder<PressingRecipe>.() -> StandardProcessingRecipe.Builder<PressingRecipe>): GeneratedRecipe =
        create(name, { b -> b.f() })
}

open class SequencedAssemblyRecipeGenKt(
    out: PackOutput,
    registries: CompletableFuture<HolderLookup.Provider>,
    defaultNamespace: String
) :
    SequencedAssemblyRecipeGen(out, registries, defaultNamespace) {
    fun create(name: String, f: SequencedAssemblyRecipeBuilder.() -> SequencedAssemblyRecipeBuilder): GeneratedRecipe =
        create(name, { b -> b.f() })

    fun SequencedAssemblyRecipeBuilder.addDeployerStep(item: ItemLike): SequencedAssemblyRecipeBuilder =
        addStep(
            ItemApplicationRecipe.Factory { params -> DeployerApplicationRecipe(params) },
            { it.require(item) },
        )

    fun SequencedAssemblyRecipeBuilder.addDeployerStep(item: TagKey<Item>): SequencedAssemblyRecipeBuilder =
        addStep(
            ItemApplicationRecipe.Factory { params -> DeployerApplicationRecipe(params) },
            { it.require(item) },
        )

    fun SequencedAssemblyRecipeBuilder.addPressStep(): SequencedAssemblyRecipeBuilder =
        addStep(StandardProcessingRecipe.Factory { params -> PressingRecipe(params) }, { it })
}