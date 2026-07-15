package com.alesharik.digitalgrid.utils

import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.ShapedRecipeBuilder
import net.minecraft.world.level.ItemLike

inline fun RecipeOutput.shaped(item: ItemLike, count: Int, crossinline f: ShapedRecipeBuilder.() -> ShapedRecipeBuilder) {
    val b = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, item, count)
    f(b).save(this)
}