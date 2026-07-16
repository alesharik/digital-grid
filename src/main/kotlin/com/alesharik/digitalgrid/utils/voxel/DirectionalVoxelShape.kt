package com.alesharik.digitalgrid.utils.voxel

import net.minecraft.core.Direction
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.*

class DirectionalVoxelShape(private val shape: VoxelShape) {
    private val cache: EnumMap<Direction, VoxelShape> = EnumMap(
        Direction.entries.associateWith { shape.rotateDirection(it) }
    )

    fun get(direction: Direction): VoxelShape = cache[direction]!!

    fun get(state: BlockState): VoxelShape {
        return get(state.getValue(DirectionalBlock.FACING))
    }

    fun getHorizontal(state: BlockState): VoxelShape {
        return get(state.getValue(HorizontalDirectionalBlock.FACING))
    }
}