package com.alesharik.digitalgrid.din.behavior.powergrid

import com.alesharik.digitalgrid.din.behavior.Behavior
import net.minecraft.network.FriendlyByteBuf
import org.patryk3211.powergrid.electricity.base.IElectricEntity
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode

interface PowerGridBehavior: Behavior {
    fun buildCircuit(ctx: CircuitContext) {}

    /** Server-side only, called once per tick. */
    fun electricalTick(): TickResult = TickResult.NONE

    /**
     * Extra client-sync payload piggybacked on Power Grid's periodic node state
     * sync. [writeSync] runs on the server, [readSync] on the client; both must
     * consume the exact same number of bytes.
     */
    fun writeSync(buffer: FriendlyByteBuf) {}

    fun readSync(buffer: FriendlyByteBuf) {}

    enum class TickResult {
        NONE,

        /** Persistent state changed; the rack marks itself for saving. */
        SAVE,

        /** Client-visible state changed; the rack additionally syncs to clients. */
        SAVE_AND_SYNC,
    }

    interface CircuitContext {
        val builder: IElectricEntity.CircuitBuilder

        /**
         * Rack-internal +24V rail, shared by all modules of the rack. Continues into adjacent
         * racks placed side by side with the same facing.
         */
        val bus24V: FloatingNode

        /**
         * Rack-internal return (minus) rail, shared by all modules of the rack. Continues into
         * adjacent racks placed side by side with the same facing.
         */
        val busMinus: FloatingNode

        fun terminalNode(idx: Int): FloatingNode
    }
}