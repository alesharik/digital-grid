package com.alesharik.digitalgrid

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.data.PackOutput
import net.minecraft.data.tags.ItemTagsProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.common.data.BlockTagsProvider
import net.neoforged.neoforge.common.data.ExistingFileHelper
import net.neoforged.neoforge.data.event.GatherDataEvent
import java.util.concurrent.CompletableFuture

object DigitalgridTags {
    fun gatherData(event: GatherDataEvent) {
        event.generator.apply {
            val blocks = addProvider(event.includeServer(), Blocks(packOutput, event.lookupProvider, event.existingFileHelper))
            addProvider(event.includeServer(), Items(packOutput, event.lookupProvider, blocks.contentsGetter(), event.existingFileHelper))
        }
    }

    class Blocks(out: PackOutput, lookupProvider: CompletableFuture<HolderLookup.Provider>, fh: ExistingFileHelper): BlockTagsProvider(out, lookupProvider,
        Digitalgrid.ID, fh
    ) {
        override fun addTags(p0: HolderLookup.Provider) {}
    }

    class Items(
        out: PackOutput,
        lookupProvider: CompletableFuture<HolderLookup.Provider>,
        bp: CompletableFuture<TagLookup<Block>>,
        fh: ExistingFileHelper,
    ) : ItemTagsProvider(
        out, lookupProvider, bp,
        Digitalgrid.ID, fh
    ) {
        override fun addTags(p: HolderLookup.Provider) {
            tag(PLASTICS)
                .add(DigitalgridRegistry.Items.PLASTIC)
                .addOptionalTag(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots/plastic")))
        }

        companion object {
            @JvmStatic
            val PLASTICS: TagKey<Item> =
                TagKey.create(
                    Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(Digitalgrid.ID, "plastics")
                )
        }
    }
}