package com.alesharik.digitalgrid.client

import com.alesharik.digitalgrid.block.AssemblyTableBlock
import com.simibubi.create.AllSpecialTextures
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge

/**
 * Outlines the Assembly Table's crafting pad while the player points at it, mirroring how Power
 * Grid highlights wire terminals (its `TerminalHandler`) — same Catnip [Outliner], same checkered
 * face texture and line width.
 *
 * The global outliner is ticked and rendered by Ponder's client, so pushing the box once per
 * client tick is all that is needed here.
 *
 * Registered manually from client init rather than with `@EventBusSubscriber`: Kotlin For Forge
 * 5.3.0 registers game-bus subscribers through `net.neoforged.fml.Bindings`, which NeoForge
 * 21.1.235 no longer ships, and the resulting NoClassDefFoundError aborts mod construction
 * entirely. The mod-bus annotation on `Digitalgrid` is unaffected and stays as it is.
 */
object AssemblyPadHighlight {
    private val OUTLINE_SLOT = Any()
    private const val COLOR = 0xFFC24B

    /** Call once during client mod construction. */
    fun register() {
        NeoForge.EVENT_BUS.addListener(::tick)
    }

    private fun tick(event: ClientTickEvent.Post) {
        val client = Minecraft.getInstance()
        val level = client.level ?: return
        val hit = client.hitResult
        if (hit !is BlockHitResult || hit.type != HitResult.Type.BLOCK) return

        val state = level.getBlockState(hit.blockPos)
        if (state.block !is AssemblyTableBlock) return

        val facing = state.getValue(AssemblyTableBlock.FACING)
        val master = AssemblyTableBlock.masterPos(hit.blockPos, state)
        if (!AssemblyTableBlock.isOnPad(facing, master, hit.location)) return

        Outliner.getInstance()
            .chaseAABB(OUTLINE_SLOT, AssemblyTableBlock.padBox(facing).move(master))
            .colored(COLOR)
            .withFaceTexture(AllSpecialTextures.CUTOUT_CHECKERED)
            .lineWidth(0.020f)
    }
}
