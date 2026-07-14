package com.alesharik.digitalgrid.din.rack

import com.alesharik.digitalgrid.din.DinRackEntity
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation

/**
 * Geometry-only stand-in, kept by a rack, for a module owned by the -u neighbor rack that
 * overhangs into this rack. Persisted in this rack's own NBT so the exposed terminal (and
 * thus Power Grid sync node) count never depends on whether the owner's block entity is
 * loaded. The [entity] is reconstructed from [item] and used solely for shape, occupancy
 * and terminal boxes — never attached, ticked, circuit-built or rendered.
 */
class OverhangGhost private constructor(
    /** Owner's placement u minus [DinRackBlockEntity.RACK_WIDTH]; always in -15..-1. */
    val offset: Int,
    val item: DinRackItem,
    val entity: DinRackEntity,
) {
    /** Units [0, occupiedWidth) of the holding rack are covered by the overhang. */
    val occupiedWidth: Int
        get() = offset + entity.width.value

    /**
     * Module-local terminal indices whose boxes reach into the holding rack's block.
     * Outline coordinates are block units; one DIN unit is 1/16 block.
     */
    val proxiedTerminals: List<Int> = entity.terminalBoundingBox.indices.filter {
        entity.terminalBoundingBox[it].outline.maxX + offset / 16.0 > 0.0
    }

    fun write(): CompoundTag {
        val tag = CompoundTag()
        tag.putString("Item", BuiltInRegistries.ITEM.getKey(item).toString())
        tag.putInt("Offset", offset)
        return tag
    }

    companion object {
        fun of(offset: Int, item: DinRackItem): OverhangGhost? {
            if (offset >= 0 || offset <= -DinRackBlockEntity.RACK_WIDTH) return null
            val entity = item.createEntity()
            if (offset + entity.width.value <= 0) return null
            return OverhangGhost(offset, item, entity)
        }

        fun read(tag: CompoundTag): OverhangGhost? {
            val id = ResourceLocation.tryParse(tag.getString("Item")) ?: return null
            val item = BuiltInRegistries.ITEM.get(id) as? DinRackItem ?: return null
            return of(tag.getInt("Offset"), item)
        }
    }
}
