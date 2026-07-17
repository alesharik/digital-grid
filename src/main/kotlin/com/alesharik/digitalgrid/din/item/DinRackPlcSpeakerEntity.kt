package com.alesharik.digitalgrid.din.item

import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.behavior.Behavior
import com.alesharik.digitalgrid.din.behavior.digibus.DigibusPeripheralBehavior
import com.alesharik.digitalgrid.utils.Lang
import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.render.RenderTypes
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.shared.network.client.SpeakerStopClientMessage
import dan200.computercraft.shared.network.server.ServerNetworking
import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral
import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition
import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition.of
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.ChatFormatting
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3.atCenterOf
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox
import java.util.stream.Stream

class DinRackPlcSpeakerEntity : DinRackEntity {
    override val shape: VoxelShape = SHAPE
    override val terminalBoundingBox: Array<TerminalBoundingBox> = emptyArray()
    override val width: DINUnit = DINUnit(4)

    private val peripheral = Peripheral()
    private val digibusBehavior = DigibusPeripheralBehavior(peripheral)
    override val behaviors: List<Behavior> = listOf(digibusBehavior, peripheral)

    override fun render(
        be: BlockState,
        en: DinRackEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val buffer = CachedBuffers.partial(PartialModels.DIN_PLC_SPEAKER, be)
        buffer.light<SuperByteBuffer>(light)
            .renderInto(ms, bufferSource.getBuffer(RenderTypes.entitySolidBlockMipped()))
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        Lang.translate("goggles.plc_speaker").style(ChatFormatting.GRAY).forGoggles(tooltip)
        Lang.translate("goggles.digibus.name").style(ChatFormatting.GRAY)
            .space().add(Lang.text(digibusBehavior.peripheralName).style(ChatFormatting.AQUA))
            .forGoggles(tooltip, 1)
        return true
    }

    private inner class Peripheral : SpeakerPeripheral(), Behavior {
        private var level: ServerLevel? = null
        private var pos: SpeakerPosition? = null

        override fun onAttach(ctx: Behavior.AttachContext) {
            if (ctx.level !is ServerLevel) return

            val level1 = ctx.level as ServerLevel
            level = level1
            pos = of(level1, atCenterOf(ctx.pos))
        }

        override fun serverTick(level: ServerLevel, be: BlockEntity) {
            update()
        }

        override fun getLevel(): ServerLevel =
            level ?: error("PLC speaker peripheral used before attach")

        override fun getPosition(): SpeakerPosition = pos ?: error("PLC speaker peripheral used before attach")

        override fun onDetach() {
            val level = this.level ?: return
            ServerNetworking.sendToAllPlayers(SpeakerStopClientMessage(peripheral.getSource()), level.server)
        }

        override fun equals(other: IPeripheral?): Boolean = other === this
    }

    companion object {
        private val SHAPE = Stream.of(
            Block.box(0.0, 3.0, 14.0, 4.0, 8.0, 15.0),
            Block.box(0.0, 10.0, 14.0, 4.0, 15.0, 15.0),
            Block.box(0.0, 4.0, 13.0, 4.0, 7.0, 14.0),
            Block.box(0.0, 11.0, 13.0, 4.0, 14.0, 14.0),
            Block.box(0.0, 4.0, 12.49999999999999, 4.0, 14.0, 12.99999999999999),
            Block.box(3.5, 4.0, 10.99999999999999, 4.0, 14.0, 12.49999999999999),
            Block.box(0.5, 13.5, 10.99999999999999, 3.5, 14.0, 12.49999999999999),
            Block.box(0.5, 4.0, 10.99999999999999, 3.5, 4.5, 12.49999999999999),
            Block.box(0.5, 7.5, 10.99999999999999, 3.5, 10.5, 12.49999999999999),
            Block.box(0.75, 4.75, 11.74999999999999, 3.25, 7.25, 12.49999999999999),
            Block.box(0.75, 10.75, 11.74999999999999, 3.25, 13.25, 12.49999999999999),
            Block.box(0.0, 4.0, 10.99999999999999, 0.5, 14.0, 12.49999999999999)
        ).reduce { v1: VoxelShape?, v2: VoxelShape? -> Shapes.join(v1, v2, BooleanOp.OR) }.get().optimize()
    }
}
