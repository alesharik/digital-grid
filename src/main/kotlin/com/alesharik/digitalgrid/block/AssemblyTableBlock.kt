package com.alesharik.digitalgrid.block

import com.alesharik.digitalgrid.DigitalgridRegistry
import com.alesharik.digitalgrid.block.AssemblyTableBlock.Companion.FACING
import com.alesharik.digitalgrid.block.AssemblyTableBlock.Companion.RIGHT
import com.alesharik.digitalgrid.block.AssemblyTableBlock.Companion.UPPER
import com.alesharik.digitalgrid.utils.voxel.rotateDirection
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.max
import kotlin.math.min

/**
 * Assembly Table — a 2 (wide) x 2 (tall) x 1 (deep) multiblock.
 *
 * The structure is four cells of this same block, told apart by [RIGHT] and [UPPER]. The
 * master cell (RIGHT=false, UPPER=false, front-left-bottom) is the placement origin and the
 * only cell with a [AssemblyTableBlockEntity]. The whole model is drawn once by
 * [AssemblyTableBlockEntityRenderer] on the master; every cell renders nothing itself.
 *
 * Width extends to the block's right ([Direction.getClockWise] of [FACING]); height extends up.
 */
class AssemblyTableBlock(props: Properties) : Block(props), EntityBlock {

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(RIGHT, false)
                .setValue(UPPER, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, RIGHT, UPPER)
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.ENTITYBLOCK_ANIMATED

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val right = if (state.getValue(RIGHT)) 1 else 0
        val upper = if (state.getValue(UPPER)) 1 else 0
        return BASE_SHAPES[right][upper].rotateDirection(state.getValue(FACING))
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? =
        if (isMaster(state)) DigitalgridRegistry.BlockEntities.ASSEMBLY_TABLE.create(pos, state) else null

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        val facing = ctx.horizontalDirection.opposite
        val master = ctx.clickedPos
        val width = facing.clockWise
        val others = listOf(
            master.relative(width),         // bottom-right
            master.above(),                 // top-left
            master.above().relative(width)  // top-right
        )
        val level = ctx.level
        for (p in others) {
            if (level.isOutsideBuildHeight(p)) return null
            if (!level.getBlockState(p).canBeReplaced(ctx)) return null
        }
        return defaultBlockState()
            .setValue(FACING, facing)
            .setValue(RIGHT, false)
            .setValue(UPPER, false)
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (level.isClientSide) return
        val facing = state.getValue(FACING)
        val width = facing.clockWise
        level.setBlock(pos.relative(width), partState(facing, right = true, upper = false), UPDATE_ALL)
        level.setBlock(pos.above(), partState(facing, right = false, upper = true), UPDATE_ALL)
        level.setBlock(pos.above().relative(width), partState(facing, right = true, upper = true), UPDATE_ALL)
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (!level.isClientSide) {
            val master = masterPos(pos, state)
            clearOtherCells(level, master, state.getValue(FACING), except = pos)
            if (!player.isCreative) {
                popResource(level, master, ItemStack(this))
            }
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        // Any cell may be clicked, but only a hit on the crafting pad opens the GUI, and the menu
        // always belongs to the master cell (the only one with a block entity).
        val master = masterPos(pos, state)
        if (!isOnPad(state.getValue(FACING), master, hit.location)) return InteractionResult.PASS
        if (level.isClientSide) return InteractionResult.SUCCESS

        val blockEntity = level.getBlockEntity(master) as? AssemblyTableBlockEntity
            ?: return InteractionResult.PASS

        player.openMenu(blockEntity, master)
        return InteractionResult.CONSUME
    }

    /**
     * The pad opens the GUI regardless of what the player is holding, like a vanilla crafting
     * table. Returning PASS_TO_DEFAULT_BLOCK_INTERACTION off the pad lets the held item behave
     * normally (placement etc.). Sneaking already skips this path in vanilla, so holding shift
     * still lets you build against the table.
     */
    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
    ): ItemInteractionResult {
        val master = masterPos(pos, state)
        if (!isOnPad(state.getValue(FACING), master, hit.location)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS

        val blockEntity = level.getBlockEntity(master) as? AssemblyTableBlockEntity
            ?: return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION

        player.openMenu(blockEntity, master)
        return ItemInteractionResult.CONSUME
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!level.isClientSide && !state.`is`(newState.block)) {
            clearOtherCells(level, masterPos(pos, state), state.getValue(FACING), except = pos)
        }
        super.onRemove(state, level, pos, newState, moved)
    }

    /** Set every cell of the structure except [except] to air, suppressing their drops. */
    private fun clearOtherCells(level: Level, master: BlockPos, facing: Direction, except: BlockPos) {
        val width = facing.clockWise
        val cells = listOf(master, master.relative(width), master.above(), master.above().relative(width))
        for (p in cells) {
            if (p == except) continue
            if (level.getBlockState(p).block == this) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), UPDATE_ALL or UPDATE_SUPPRESS_DROPS)
            }
        }
    }

    private fun partState(facing: Direction, right: Boolean, upper: Boolean): BlockState =
        defaultBlockState()
            .setValue(FACING, facing)
            .setValue(RIGHT, right)
            .setValue(UPPER, upper)

    companion object {
        val FACING: DirectionProperty = HorizontalDirectionalBlock.FACING
        val RIGHT: BooleanProperty = BooleanProperty.create("right")
        val UPPER: BooleanProperty = BooleanProperty.create("upper")

        /** Raw model boxes in pixel coords: px1,py1,pz1,px2,py2,pz2 */
        private val MODEL_BOXES: Array<DoubleArray> = arrayOf(
            doubleArrayOf(-12.0, 17.0, 5.0, 1.0, 26.0, 6.0),
            doubleArrayOf(-16.0, 14.0, 0.0, -6.0, 25.0, 1.0),
            doubleArrayOf(3.0, 28.0, 1.0, 6.0, 28.5, 3.0),
            doubleArrayOf(-6.0, 31.0, 1.0, 16.0, 32.0, 3.0),
            doubleArrayOf(-5.0, 27.0, 1.0, 15.0, 28.0, 3.0),
            doubleArrayOf(-9.0, 22.0, 1.0, -8.0, 23.0, 6.3),
            doubleArrayOf(-13.0, 22.0, 5.3, -4.0, 23.0, 6.3),
            doubleArrayOf(-16.0, 24.0, 1.0, -6.0, 25.0, 3.0),
            doubleArrayOf(15.0, 16.0, 1.0, 16.0, 31.0, 3.0),
            doubleArrayOf(-16.0, 16.0, 1.0, -15.0, 24.0, 3.0),
            doubleArrayOf(-6.0, 24.0, 1.0, -5.0, 31.0, 3.0),
            doubleArrayOf(15.7, 28.3, 1.0, 16.4, 31.0, 3.0),
            doubleArrayOf(11.2, 28.0, 1.0, 12.0, 30.7, 3.0),
            doubleArrayOf(10.0, 28.0, 1.0, 11.0, 30.9, 3.0),
            doubleArrayOf(7.0, 28.0, 1.0, 8.0, 30.5, 3.0),
            doubleArrayOf(0.0, 9.0, 0.0, 16.0, 10.0, 14.0),
            doubleArrayOf(0.0, 3.0, 0.0, 16.0, 4.0, 14.0),
            doubleArrayOf(-15.0, 3.0, 0.0, -4.0, 4.0, 14.0),
            doubleArrayOf(-16.0, 15.0, 1.0, 16.0, 16.0, 14.0),
            doubleArrayOf(-14.0, 4.0, 0.0, -11.0, 6.0, 1.0),
            doubleArrayOf(-12.0, 4.0, -9.0, -9.0, 6.0, -8.0),
            doubleArrayOf(9.0, 10.0, 28.0, 15.0, 11.0, 29.0),
            doubleArrayOf(4.5, 14.6, -3.0, 12.5, 21.6, -2.0),
            doubleArrayOf(-14.0, 4.0, -3.0, -12.0, 10.0, -2.0),
            doubleArrayOf(15.0, 0.0, 0.0, 16.0, 3.0, 1.0),
            doubleArrayOf(15.0, 0.0, 13.0, 16.0, 3.0, 14.0),
            doubleArrayOf(0.0, 0.0, 0.0, 1.0, 3.0, 1.0),
            doubleArrayOf(0.0, 0.0, 13.0, 1.0, 3.0, 14.0),
            doubleArrayOf(15.0, 4.0, 0.0, 16.0, 9.0, 1.0),
            doubleArrayOf(15.0, 4.0, 13.0, 16.0, 9.0, 14.0),
            doubleArrayOf(0.0, 4.0, 0.0, 1.0, 9.0, 1.0),
            doubleArrayOf(0.0, 4.0, 13.0, 1.0, 9.0, 14.0),
            doubleArrayOf(15.0, 10.0, 0.0, 16.0, 14.0, 1.0),
            doubleArrayOf(15.0, 10.0, 13.0, 16.0, 15.0, 14.0),
            doubleArrayOf(0.0, 10.0, 0.0, 1.0, 14.0, 1.0),
            doubleArrayOf(0.0, 10.0, 13.0, 1.0, 15.0, 14.0),
            doubleArrayOf(-4.0, 0.0, 13.0, -3.0, 15.0, 14.0),
            doubleArrayOf(-4.0, 0.0, 0.0, -3.0, 14.0, 1.0),
            doubleArrayOf(-16.0, 0.0, 13.0, -15.0, 15.0, 14.0),
            doubleArrayOf(-16.0, 0.0, 0.0, -15.0, 14.0, 1.0),
            doubleArrayOf(-6.0, 14.0, 0.0, 16.0, 32.0, 1.0),
            doubleArrayOf(-9.0, 16.0, 1.0, -8.0, 24.0, 2.0),
            doubleArrayOf(-10.0, 16.0, 1.0, -7.0, 17.0, 3.0),
            doubleArrayOf(-15.0, 4.0, 0.0, -8.0, 14.0, 12.0),
            doubleArrayOf(-11.0, 4.0, 12.0, -8.0, 14.0, 12.2),
            doubleArrayOf(-15.0, 4.0, 12.0, -11.0, 9.0, 12.2),
            doubleArrayOf(-15.0, 10.0, 12.0, -11.0, 14.0, 12.2),
            doubleArrayOf(-14.0, 11.0, 12.2, -10.0, 12.0, 12.4),
            doubleArrayOf(-15.0, 9.0, 12.0, -14.0, 10.0, 12.2),
            doubleArrayOf(3.0, 16.0, 9.0, 4.0, 16.5, 11.0),
            doubleArrayOf(-8.9, 16.0, 7.3, 2.4, 16.2, 13.3),
        )

        private val MODEL_ROTATIONS: Map<Int, DoubleArray> = mapOf(
            0  to doubleArrayOf(22.5, 1.0, -1.0, 17.0, 5.0),
            5  to doubleArrayOf(22.5, 1.0, -8.0, 21.0, 1.0),
            6  to doubleArrayOf(22.5, 1.0, -6.0, 21.0, -1.7),
            11 to doubleArrayOf(-22.5, 2.0, 14.4, 34.3, 1.0),
            19 to doubleArrayOf(22.5, 1.0, 11.5, 4.0, -12.0),
            20 to doubleArrayOf(45.0, 1.0, 13.5, 4.0, -21.0),
            21 to doubleArrayOf(-45.0, 1.0, 37.5, 10.0, 16.0),
            22 to doubleArrayOf(-22.5, 0.0, 33.0, 33.6, -3.0),
            23 to doubleArrayOf(22.5, 1.0, 10.5, 4.0, -15.0),
        )

        /**
         * Precomputed per-cell VoxelShapes in NORTH-facing (base) orientation.
         * Indexed as BASE_SHAPES[right][upper], where 0=false, 1=true.
         * Rotated model elements (indices listed in MODEL_ROTATIONS) are excluded from the shape.
         */
        val BASE_SHAPES: Array<Array<VoxelShape>> = run {
            // acc[col][row], col = right index, row = upper index
            val acc = Array(2) { Array(2) { Shapes.empty() } }
            for (index in MODEL_BOXES.indices) {
                if (index in MODEL_ROTATIONS) continue // rotated parts are excluded from collision
                val box = MODEL_BOXES[index]
                val px1 = box[0]; val py1 = box[1]; val pz1 = box[2]
                val px2 = box[3]; val py2 = box[4]; val pz2 = box[5]
                // Transform to master-relative pixel space
                val x1 = 16.0 - px2; val x2 = 16.0 - px1
                val y1 = py1;         val y2 = py2
                val z1 = 16.0 - pz2; val z2 = 16.0 - pz1
                for (col in 0..1) {
                    for (row in 0..1) {
                        val cx1 = max(x1, col * 16.0);       val cx2 = min(x2, col * 16.0 + 16.0)
                        val cy1 = max(y1, row * 16.0);       val cy2 = min(y2, row * 16.0 + 16.0)
                        val cz1 = max(z1, 0.0);              val cz2 = min(z2, 16.0)
                        if (cx1 < cx2 && cy1 < cy2 && cz1 < cz2) {
                            val b = Shapes.box(
                                (cx1 - col * 16.0) / 16.0, (cy1 - row * 16.0) / 16.0, cz1 / 16.0,
                                (cx2 - col * 16.0) / 16.0, (cy2 - row * 16.0) / 16.0, cz2 / 16.0,
                            )
                            acc[col][row] = Shapes.or(acc[col][row], b)
                        }
                    }
                }
            }
            Array(2) { col -> Array(2) { row -> acc[col][row].optimize() } }
        }

        /**
         * The crafting pad — the framed slot panel painted on the tabletop's top face (model
         * element 18). Authored in north-facing, master-relative rack space in the same pixel
         * coordinates as [MODEL_BOXES] after the transform applied in [BASE_SHAPES], so it lies
         * entirely within the master cell.
         */
        private val PAD_SHAPE: VoxelShape =
            Shapes.box(1.5 / 16.0, 15.0 / 16.0, 3.0 / 16.0, 11.0 / 16.0, 16.0 / 16.0, 10.0 / 16.0)

        /**
         * [PAD_SHAPE] rotated into world orientation for [facing]. Goes through the same
         * [rotateDirection] the collision shapes use, so the pad can never drift out of agreement
         * with them.
         */
        fun padBox(facing: Direction): AABB = PAD_SHAPE.rotateDirection(facing).bounds()

        /**
         * True when world-space [location] lies on the crafting pad of the structure whose master
         * cell is [master]. Shared by this block's interaction handlers and by the client-side
         * highlight, so the clickable region and the drawn region cannot disagree.
         */
        fun isOnPad(facing: Direction, master: BlockPos, location: Vec3): Boolean =
            padBox(facing).inflate(1.0 / 32.0)
                .contains(location.subtract(master.x.toDouble(), master.y.toDouble(), master.z.toDouble()))

        fun isMaster(state: BlockState): Boolean =
            !state.getValue(RIGHT) && !state.getValue(UPPER)

        /** World position of the master cell for any cell [pos]/[state] of the structure. */
        fun masterPos(pos: BlockPos, state: BlockState): BlockPos {
            val width = state.getValue(FACING).clockWise
            var p = pos
            if (state.getValue(RIGHT)) p = p.relative(width.opposite)
            if (state.getValue(UPPER)) p = p.below()
            return p
        }
    }
}
