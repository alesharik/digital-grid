package com.alesharik.digitalgrid.utils.voxel

import net.minecraft.core.Direction
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/** Pose-stack Y rotation (around block center) matching [rotateDirection] for horizontal directions. */
fun Direction.rotationYDegrees(): Float = when (this) {
    Direction.NORTH -> 0f
    Direction.EAST -> -90f
    Direction.SOUTH -> 180f
    Direction.WEST -> 90f
    else -> 0f
}

/** Pose-stack Y rotation (around block center, inversed) matching [rotateDirection] for horizontal directions. */
fun Direction.rotationYDegreesInv(): Float = when (this) {
    Direction.NORTH -> 0f
    Direction.EAST -> 90f
    Direction.SOUTH -> 180f
    Direction.WEST -> -90f
    else -> 0f
}

fun VoxelShape.rotateDirection(direction: Direction): VoxelShape = when (direction) {
    Direction.NORTH -> this
    Direction.EAST -> this.rotateY(1)
    Direction.SOUTH -> this.rotateY(2)
    Direction.WEST -> this.rotateY(3)
    Direction.UP -> this.transform { minX, minY, minZ, maxX, maxY, maxZ ->
        Shapes.box(minX, 1 - maxZ, minY, maxX, 1 - minZ, maxY)
    }
    Direction.DOWN -> this.transform { minX, minY, minZ, maxX, maxY, maxZ ->
        Shapes.box(minX, minZ, 1 - maxY, maxX, maxZ, 1 - minY)
    }
}

private fun VoxelShape.rotateY(times: Int): VoxelShape {
    var result = this
    repeat(times) {
        result = result.transform { minX, minY, minZ, maxX, maxY, maxZ ->
            Shapes.box(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX)
        }
    }
    return result
}

private inline fun VoxelShape.transform(
    crossinline box: (Double, Double, Double, Double, Double, Double) -> VoxelShape,
): VoxelShape {
    var result = Shapes.empty()
    this.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
        result = Shapes.or(result, box(minX, minY, minZ, maxX, maxY, maxZ))
    }
    return result.optimize()
}