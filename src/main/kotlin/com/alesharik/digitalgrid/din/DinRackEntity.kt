package com.alesharik.digitalgrid.din

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.base.IElectricEntity
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode

interface DinRackEntity {
    val shape: VoxelShape
    val terminalBoundingBox: Array<TerminalBoundingBox>
    val width: DINUnit

    fun render(
        be: BlockState,
        en: DinRackEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    )

    fun useItemOn(
        item: ItemStack,
        st: BlockState,
        lv: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): ItemInteractionResult = ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION

    fun buildCircuit(ctx: CircuitContext) {}

    /**
     * Called by the rack when this module instance joins a placement — after creation and NBT
     * read, on both server and client, and again whenever the rack rebuilds its module list
     * (chunk load, client sync). [ctx] reads [Level]/[BlockPos] live from the owning rack, so a
     * module may cache it and use it later (e.g. from [electricalTick]).
     */
    fun onAttach(ctx: ModuleContext) {}

    /** Called when this module instance leaves a placement (removed by a player) or the rack unloads. */
    fun onDetach() {}

    /** Server-side only, called once per tick. */
    fun electricalTick(): TickResult = TickResult.NONE

    enum class TickResult {
        NONE,

        /** Persistent state changed; the rack marks itself for saving. */
        SAVE,

        /** Client-visible state changed; the rack additionally syncs to clients. */
        SAVE_AND_SYNC,
    }

    fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {}

    fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {}

    /**
     * Extra client-sync payload piggybacked on Power Grid's periodic node state
     * sync. [writeSync] runs on the server, [readSync] on the client; both must
     * consume the exact same number of bytes.
     */
    fun writeSync(buffer: FriendlyByteBuf) {}

    fun readSync(buffer: FriendlyByteBuf) {}

    /** Client-side: goggle tooltip lines for this module. Return true if any were added. */
    fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean = false

    /** Handle to the owning rack, handed to a module in [onAttach]. Values are read live. */
    interface ModuleContext {
        val level: Level
        val pos: BlockPos

        /** The owning rack block entity (e.g. as the host for a ComputerCraft modem). */
        val blockEntity: BlockEntity

        /** The backing item stack of this placement — persisted in rack NBT and returned to the player on removal. */
        val stack: ItemStack

        /** Mark the rack for saving (persist a mutated [stack] or module NBT). */
        fun markChanged()

        /** Push a full block-entity sync to clients. */
        fun requestSync()
    }

    interface CircuitContext {
        val builder: IElectricEntity.CircuitBuilder

        /** Rack-internal +24V rail, shared by all modules of the rack. Created on first access. */
        val bus24V: FloatingNode

        /** Rack-internal return (minus) rail, shared by all modules of the rack. Created on first access. */
        val busMinus: FloatingNode

        fun terminalNode(idx: Int): FloatingNode
    }
}