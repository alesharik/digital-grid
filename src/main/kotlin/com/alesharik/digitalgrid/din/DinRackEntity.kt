package com.alesharik.digitalgrid.din

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.state.BlockState
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

    fun buildCircuit(ctx: CircuitContext) {}

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

    interface CircuitContext {
        val builder: IElectricEntity.CircuitBuilder

        /** Rack-internal +24V rail, shared by all modules of the rack. Created on first access. */
        val bus24V: FloatingNode

        /** Rack-internal return (minus) rail, shared by all modules of the rack. Created on first access. */
        val busMinus: FloatingNode

        fun terminalNode(idx: Int): FloatingNode
    }
}