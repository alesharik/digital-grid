package com.alesharik.digitalgrid.din.item

import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.DigitalgridRegistry
import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.item.plc.PlcBusConnector
import com.alesharik.digitalgrid.din.item.plc.PlcBusModule
import com.alesharik.digitalgrid.din.item.plc.PlcRelayPeripheral
import com.alesharik.digitalgrid.utils.Lang
import com.alesharik.digitalgrid.utils.light.LightIndicator
import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.render.RenderTypes
import dan200.computercraft.shared.util.NonNegativeId
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.ChatFormatting
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox
import org.patryk3211.powergrid.electricity.sim.SwitchedWire
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode
import java.util.stream.Stream

class DinRackPlcRelayEntity: DinRackEntity, PlcBusModule {
    override val shape: VoxelShape = SHAPE
    override val terminalBoundingBox: Array<TerminalBoundingBox> = TERMINALS
    override val width: DINUnit = DINUnit(2)

    private var context: DinRackEntity.ModuleContext? = null
    private var connector: PlcBusConnector? = null

    private var contact: SwitchedWire? = null
    private var coil: SwitchedWire? = null
    private var busPlus: FloatingNode? = null
    private var busMinus: FloatingNode? = null

    /** Set from the computer thread via the peripheral; applied on the server tick. */
    @Volatile
    private var commanded = false

    /** Last [commanded] value persisted, to request a save only when it changes. */
    private var persistedCommand = false

    /** Contact actually closed (coil energized and rail powered); synced for goggles. */
    @Volatile
    private var closed = false

    /** Contact is powered. Synced for light */
    @Volatile
    private var working = false

    override fun onAttach(ctx: DinRackEntity.ModuleContext) {
        context = ctx
    }

    override fun onDetach() {
        connector?.remove()
        connector = null
        context = null
    }

    override fun busConnector(): PlcBusConnector? {
        connector?.let { return it }
        val ctx = context ?: return null
        val level = ctx.level as? ServerLevel ?: return null
        // The id names this module's peripheral and travels on the item, so the name
        // follows the module when it is moved to another rack.
        val id = NonNegativeId.getOrCreate(
            level.server, ctx.stack, DigitalgridRegistry.DataComponents.MODULE_ID, ID_STORE
        )
        ctx.markChanged()
        return PlcBusConnector(ctx.blockEntity).also {
            it.setPeripherals(mapOf("plc_relay_$id" to PlcRelayPeripheral(this)))
            connector = it
        }
    }

    override fun buildCircuit(ctx: DinRackEntity.CircuitContext) {
        contact = ctx.builder.connectSwitch(CONTACT_RESISTANCE, ctx.terminalNode(0), ctx.terminalNode(1), closed)
        // Coil load across the rail, drawn only while commanded on (R = V / I at nominal).
        val coilResistance = (NOMINAL_VOLTAGE / DigitalgridConfig.PLC_RELAY_COIL_CURRENT.get()).toFloat()
        coil = ctx.builder.connectSwitch(coilResistance, ctx.bus24V, ctx.busMinus, commanded)
        // Module electronics as a fixed load across the rail, sized for the configured
        // quiescent current (R = V / I at nominal).
        val loadResistance = (NOMINAL_VOLTAGE / DigitalgridConfig.PLC_RELAY_CURRENT_DRAW.get()).toFloat()
        ctx.builder.connect(loadResistance, ctx.bus24V, ctx.busMinus)
        busPlus = ctx.bus24V
        busMinus = ctx.busMinus
    }

    override fun electricalTick(): DinRackEntity.TickResult {
        val contact = contact ?: return DinRackEntity.TickResult.NONE
        val coil = coil ?: return DinRackEntity.TickResult.NONE
        val plus = busPlus ?: return DinRackEntity.TickResult.NONE
        val minus = busMinus ?: return DinRackEntity.TickResult.NONE
        val v = plus.voltage - minus.voltage
        val cmd = commanded
        if (coil.state != cmd) coil.state = cmd
        // Pull in at PULL_IN_VOLTAGE, drop out below 0.9x — rail power loss opens the
        // contact regardless of the command (fail-safe).
        working = v.isFinite() && v >= if (closed) PULL_IN_VOLTAGE * 0.9 else PULL_IN_VOLTAGE
        closed = cmd && v.isFinite() && v >= if (closed) PULL_IN_VOLTAGE * 0.9 else PULL_IN_VOLTAGE
        if (contact.state != closed) contact.state = closed

        // Contact state rides Power Grid's periodic node-state sync via the rack's
        // sync appender, so only the persistent command needs a save here.
        return if (cmd != persistedCommand) {
            persistedCommand = cmd
            DinRackEntity.TickResult.SAVE
        } else {
            DinRackEntity.TickResult.NONE
        }
    }

    // --- called from the computer thread via PlcRelayPeripheral (commanded is @Volatile) ---

    internal fun luaSetState(on: Boolean) {
        commanded = on
    }

    internal fun luaGetState(): Boolean = commanded

    internal fun luaIsClosed(): Boolean = closed

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        commanded = tag.getBoolean("Commanded")
        persistedCommand = commanded
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        if (commanded) tag.putBoolean("Commanded", true)
    }

    override fun writeSync(buffer: FriendlyByteBuf) {
        buffer.writeByte((if (commanded) 1 else 0) or (if (closed) 2 else 0) or (if (working) 4 else 0))
    }

    override fun readSync(buffer: FriendlyByteBuf) {
        val b = buffer.readByte().toInt()
        commanded = (b and 1) != 0
        closed = (b and 2) != 0
        working = (b and 4) != 0
    }

    override fun render(
        be: BlockState,
        en: DinRackEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val buffer = CachedBuffers.partial(PartialModels.DIN_PLC_RELAY, be)
        buffer.light<SuperByteBuffer>(light)
            .renderInto(ms, bufferSource.getBuffer(RenderTypes.entitySolidBlockMipped()))
        if (working) {
            (if (closed) LIGHT_ON else LIGHT_OFF).render(be, ms, bufferSource)
        } else {
            LIGHT_NO_POWER.render(be, ms, bufferSource)
        }
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        Lang.builder().translate("goggles.plc_relay").style(ChatFormatting.GRAY).forGoggles(tooltip)
        Lang.builder().translate("goggles.state").style(ChatFormatting.GRAY)
            .space().add(
                if (closed) Lang.translate("goggles.plc_relay.state.closed").style(ChatFormatting.GREEN)
                else Lang.translate("goggles.plc_relay.state.open").style(ChatFormatting.DARK_GRAY)
            )
            .forGoggles(tooltip, 1)
        Lang.builder().translate("goggles.plc_relay.coil").style(ChatFormatting.GRAY)
            .space().add(
                if (commanded) Lang.translate("goggles.plc_relay.coil.on").style(ChatFormatting.GREEN)
                else Lang.translate("goggles.plc_relay.coil.off").style(ChatFormatting.DARK_GRAY)
            )
            .forGoggles(tooltip, 1)
        return true
    }

    companion object {
        /** Nominal bus voltage the configured coil current is sized against. */
        private const val NOMINAL_VOLTAGE = 24.0

        /** Minimum rail voltage for the coil to pull the contact in. */
        private const val PULL_IN_VOLTAGE = 16.0

        private const val CONTACT_RESISTANCE = 0.01f

        /** Key of the module-id sequence in CC's id store. */
        private const val ID_STORE = "digitalgrid_plc_relay"

        /** Action light: program-controlled via the `plc` peripheral. */
        private val LIGHT_ON = LightIndicator(PartialModels.DIN_PLC_RELAY_LIGHT, LightIndicator.GREEN)
        private val LIGHT_OFF = LightIndicator(PartialModels.DIN_PLC_RELAY_LIGHT, LightIndicator.RED)
        private val LIGHT_NO_POWER = LightIndicator.off(PartialModels.DIN_PLC_RELAY_LIGHT)

        private val SHAPE = Stream.of(
            Block.box(0.0, 3.0, 14.0, 2.0, 8.0, 15.0),
            Block.box(0.0, 10.0, 14.0, 2.0, 15.0, 15.0),
            Block.box(0.0, 4.0, 13.0, 2.0, 7.0, 14.0),
            Block.box(0.0, 11.0, 13.0, 2.0, 14.0, 14.0),
            Block.box(0.0, 4.0, 11.0, 2.0, 14.0, 13.0),
            Block.box(0.5, 14.0, 12.1, 1.5, 14.8, 12.9),
            Block.box(0.5, 3.2, 12.1, 1.5, 4.0, 12.9)
        ).reduce({ v1, v2 -> Shapes.join(v1, v2, BooleanOp.OR) }).get().optimize()

        private val TERMINALS = arrayOf<TerminalBoundingBox>(
            TerminalBoundingBox(IDecoratedTerminal.CONNECTOR, 0.5, 14.0, 12.1, 1.5, 14.8, 12.9)
                .withColor(IDecoratedTerminal.GRAY),
            TerminalBoundingBox(IDecoratedTerminal.CONNECTOR, 0.5, 3.2, 12.1, 1.5, 4.0, 12.9)
                .withColor(IDecoratedTerminal.GRAY),
        )
    }
}
