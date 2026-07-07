package com.alesharik.digitalgrid.din.item.plc

import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.DigitalgridRegistry
import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.utils.Lang
import com.alesharik.digitalgrid.utils.light.LightIndicator
import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.render.RenderTypes
import dan200.computercraft.core.computer.ComputerSide
import dan200.computercraft.shared.ModRegistry
import dan200.computercraft.shared.computer.core.ComputerFamily
import dan200.computercraft.shared.computer.core.ServerComputer
import dan200.computercraft.shared.computer.core.TerminalSize
import dan200.computercraft.shared.computer.inventory.ComputerMenuWithoutInventory
import dan200.computercraft.shared.network.container.ComputerContainerData
import dan200.computercraft.shared.platform.PlatformHelper
import dan200.computercraft.shared.util.NonNegativeId
import net.createmod.catnip.lang.LangBuilder
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.ChatFormatting
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox
import org.patryk3211.powergrid.electricity.sim.SwitchedWire
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode
import thedarkcolour.kotlinforforge.neoforge.kotlin.enumMapOf
import java.util.stream.Stream

class DinRackPlcEntity: DinRackEntity {
    override val shape: VoxelShape = SHAPE
    override val terminalBoundingBox: Array<TerminalBoundingBox> = emptyArray()
    override val width: DINUnit = DINUnit(3)

    private var load: SwitchedWire? = null
    private var busPlus: FloatingNode? = null
    private var busMinus: FloatingNode? = null

    /** True while the internal 24V bus supplies enough voltage to run. */
    private var powered = false

    /** Derived each server tick, synced to clients for the work light. */
    private var workState = WorkState.OFF

    /** Set from the computer thread via the `plc` peripheral; read on the server tick. */
    @Volatile
    private var actionLight = false

    /** Last [actionLight] value persisted, to request a save only when it changes. */
    private var persistedAction = false

    /** Server-only live computer; created lazily from the persisted id, closed on detach. */
    private var computer: ServerComputer? = null
    private var modemBus: PlcModemBus? = null
    private var context: DinRackEntity.ModuleContext? = null

    /** Cached computer id (also stored on the item's data component); -1 until assigned. */
    private var computerId = -1

    /** Pluggable sub-components (wireless, watchdog, …). Empty for now; populated later. */
    private val components = mutableListOf<DinRackPlcComponent>()

    override fun onAttach(ctx: DinRackEntity.ModuleContext) {
        context = ctx
    }

    override fun onDetach() {
        modemBus?.remove()
        modemBus = null
        computer?.close()
        computer = null
        context = null
    }

    override fun buildCircuit(ctx: DinRackEntity.CircuitContext) {
        // Model the controller as a resistive load across the rack's 24V rail, sized for the
        // configured nominal current (R = V / I). The switch lets the PLC drop its draw entirely
        // when it force-shuts-down on undervoltage.
        val resistance = (NOMINAL_VOLTAGE / DigitalgridConfig.PLC_CURRENT_DRAW.get()).toFloat()
        load = ctx.builder.connectSwitch(resistance, ctx.bus24V, ctx.busMinus, powered)
        busPlus = ctx.bus24V
        busMinus = ctx.busMinus
    }

    private fun railVoltage(): Double {
        val plus = busPlus ?: return 0.0
        val minus = busMinus ?: return 0.0
        return plus.voltage - minus.voltage
    }

    override fun electricalTick(): DinRackEntity.TickResult {
        val ctx = context ?: return DinRackEntity.TickResult.NONE
        val load = load ?: return DinRackEntity.TickResult.NONE
        val v = railVoltage()
        val minV = DigitalgridConfig.PLC_MIN_VOLTAGE.get()
        val wasPowered = powered
        // Undervoltage lockout with hysteresis: drop out below minV, recover only back at minV.
        powered = v.isFinite() && v >= if (wasPowered) minV * 0.9 else minV
        if (load.state != powered) load.state = powered

        val computer = ensureComputer(ctx)
        if (computer != null) {
            // CC's registry ticks the computer; keepAlive stops it from timing out and unloading.
            computer.keepAlive()
            if (powered && !computer.isOn) computer.turnOn()
            else if (!powered && computer.isOn) computer.shutdown()  // force shutdown on power loss
        }

        workState = when {
            !powered -> WorkState.OFF
            computer == null || computer.isOn -> WorkState.ON
            else -> WorkState.OFF
        }

        // Light state rides Power Grid's periodic node-state sync (~every 5 ticks) via the rack's
        // sync appender, so only a persistent change needs a save here.
        return if (actionLight != persistedAction) {
            persistedAction = actionLight
            DinRackEntity.TickResult.SAVE
        } else {
            DinRackEntity.TickResult.NONE
        }
    }

    private fun ensureComputer(ctx: DinRackEntity.ModuleContext): ServerComputer? {
        computer?.let { return it }
        val level = ctx.level as? ServerLevel ?: return null
        // getOrCreate reads the id from the stack's data component, or allocates a new one and
        // writes it back — so the id travels with the item when the module is later removed.
        val id = NonNegativeId.getOrCreate(
            level.server, ctx.stack, ModRegistry.DataComponents.COMPUTER_ID.get(), "computer"
        )
        ctx.markChanged()
        computerId = id
        val props = ServerComputer.properties(id, ComputerFamily.ADVANCED)
            .terminalSize(TerminalSize(TERM_WIDTH, TERM_HEIGHT))
        return ServerComputer(level, ctx.pos, props).also { c ->
            c.register()
            computer = c
            // Built-in controls (action light, reboot, id) available to the program as the `plc` peripheral.
            c.setPeripheral(ComputerSide.BOTTOM, PlcPeripheral(this))
            // Connect the computer to the internal modem bus and advertise any component peripherals.
            modemBus = PlcModemBus(ctx.blockEntity).also { bus ->
                bus.attachTo(c, ComputerSide.LEFT)
                components.forEach { comp ->
                    comp.collectPeripherals().forEach { (name, peripheral) -> bus.register(name, peripheral) }
                }
            }
        }
    }

    // --- called from the computer thread via PlcPeripheral (actionLight is @Volatile) ---

    internal fun luaSetActionLight(on: Boolean) {
        actionLight = on
    }

    internal fun luaActionLight(): Boolean = actionLight

    internal fun luaReboot() {
        computer?.reboot()
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        actionLight = tag.getBoolean("ActionLight")
        persistedAction = actionLight
        components.forEach { it.read(tag, registries, clientPacket) }
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        if (actionLight) tag.putBoolean("ActionLight", true)
        components.forEach { it.write(tag, registries, clientPacket) }
    }

    override fun writeSync(buffer: FriendlyByteBuf) {
        buffer.writeByte(workState.ordinal or (if (actionLight) ACTION_BIT else 0))
    }

    override fun readSync(buffer: FriendlyByteBuf) {
        val b = buffer.readByte().toInt()
        workState = WorkState.entries[b and WORK_MASK]
        actionLight = (b and ACTION_BIT) != 0
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
        val buffer = CachedBuffers.partial(PartialModels.DIN_PLC, be)
        buffer.light<SuperByteBuffer>(light)
            .renderInto(ms, bufferSource.getBuffer(RenderTypes.entitySolidBlockMipped()))
        WORK_LIGHTS[workState]?.render(be, ms, bufferSource)
        (if (actionLight) ACTION_LIGHT_ON else ACTION_LIGHT_OFF).render(be, ms, bufferSource)
        components.forEach { it.render(be, en, partialTicks, ms, bufferSource, light, overlay) }
    }

    override fun useItemOn(
        item: ItemStack,
        st: BlockState,
        lv: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): ItemInteractionResult {
        if (item.item != DigitalgridRegistry.Items.PLC_PROGRAMMER) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        }

        if (lv.isClientSide) return ItemInteractionResult.sidedSuccess(true)
        val ctx = context ?: return ItemInteractionResult.FAIL
        val computer = ensureComputer(ctx) ?: return ItemInteractionResult.FAIL
        PlatformHelper.get().openMenu(
            player,
            Component.translatable("gui.computercraft.view_computer"),
            { id, inventory, _ ->
                ComputerMenuWithoutInventory(ModRegistry.Menus.COMPUTER.get(), id, inventory, { true }, computer)
            },
            ComputerContainerData(computer, ItemStack.EMPTY),
        )
        return ItemInteractionResult.sidedSuccess(false)
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        Lang.builder().translate("goggles.plc").style(ChatFormatting.GRAY).forGoggles(tooltip)
        Lang.builder().translate("goggles.state").style(ChatFormatting.GRAY)
            .space().add(workState.text())
            .forGoggles(tooltip, 1)
        components.forEach { it.addToGoggleTooltip(tooltip, isPlayerSneaking) }
        return true
    }

    private enum class WorkState {
        OFF,
        ON;

        fun text(): LangBuilder = when (this) {
            OFF -> Lang.translate("goggles.plc.state.off").style(ChatFormatting.DARK_GRAY)
            ON -> Lang.translate("goggles.plc.state.on").style(ChatFormatting.GREEN)
        }
    }

    companion object {
        /** Nominal bus voltage the configured current draw is sized against. */
        private const val NOMINAL_VOLTAGE = 24.0
        private const val ACTION_BIT = 0x80
        private const val WORK_MASK = 0x7F

        /** Normal computer terminal dimensions. */
        private const val TERM_WIDTH = 51
        private const val TERM_HEIGHT = 19

        /** Work light: off when unpowered, green-blinking while booting, solid green when running. */
        private val WORK_LIGHTS = enumMapOf(
            WorkState.OFF to LightIndicator.off(PartialModels.DIN_PLC_WORK_LIGHT),
            WorkState.ON to LightIndicator.green(PartialModels.DIN_PLC_WORK_LIGHT),
        )

        /** Action light: program-controlled via the `plc` peripheral. */
        private val ACTION_LIGHT_ON = LightIndicator(PartialModels.DIN_PLC_ACTION_LIGHT, LightIndicator.ORANGE)
        private val ACTION_LIGHT_OFF = LightIndicator.off(PartialModels.DIN_PLC_ACTION_LIGHT)

        private val SHAPE = Stream.of(
            Block.box(0.0, 3.0, 14.0, 3.0, 8.0, 15.0),
            Block.box(0.0, 10.0, 14.0, 3.0, 15.0, 15.0),
            Block.box(0.0, 4.0, 13.0, 3.0, 7.0, 14.0),
            Block.box(0.0, 11.0, 13.0, 3.0, 14.0, 14.0),
            Block.box(0.0, 5.0, 11.0, 3.0, 14.0, 13.0),
            Block.box(0.0, 4.0, 11.0, 1.0, 5.0, 13.0),
            Block.box(1.0, 4.0, 12.0, 2.0, 5.0, 13.0),
            Block.box(2.0, 4.0, 11.0, 3.0, 5.0, 13.0)
        ).reduce({ v1, v2 -> Shapes.join(v1, v2, BooleanOp.OR) }).get().optimize()
    }
}