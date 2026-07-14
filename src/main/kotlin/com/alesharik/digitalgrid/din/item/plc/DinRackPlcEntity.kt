package com.alesharik.digitalgrid.din.item.plc

import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.DigitalgridRegistry
import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.behavior.Behavior
import com.alesharik.digitalgrid.din.behavior.digibus.DigibusModemBehavior
import com.alesharik.digitalgrid.din.behavior.powergrid.PowerGridBehavior
import com.alesharik.digitalgrid.din.behavior.powergrid.WorkDrawBehavior
import com.alesharik.digitalgrid.utils.Lang
import com.alesharik.digitalgrid.utils.light.LightIndicator
import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.render.RenderTypes
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.peripheral.IPeripheral
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
import thedarkcolour.kotlinforforge.neoforge.kotlin.enumMapOf
import java.util.stream.Stream

class DinRackPlcEntity: DinRackEntity {
    override val shape: VoxelShape = SHAPE
    override val terminalBoundingBox: Array<TerminalBoundingBox> = emptyArray()
    override val width: DINUnit = DINUnit(3)

    private val modemBehavior = DigibusModemBehavior()
    private val workDrawBehavior by lazy { WorkDrawBehavior.forBus(DigitalgridConfig.CONFIG.plc.currentDraw) }
    private val computerBehavior by lazy { ComputerBehavior(workDrawBehavior, modemBehavior) }

    override val behaviors: List<Behavior> by lazy {
        listOf(
            workDrawBehavior,
            modemBehavior,
            computerBehavior
        )
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
        WORK_LIGHTS[computerBehavior.workState]?.render(be, ms, bufferSource)
        (if (computerBehavior.actionLight) ACTION_LIGHT_ON else ACTION_LIGHT_OFF).render(be, ms, bufferSource)
        behaviors.filterIsInstance<DinRackPlcComponent>().forEach { it.render(be, en, partialTicks, ms, bufferSource, light, overlay) }
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
        val computer = computerBehavior.ensureComputer() ?: return ItemInteractionResult.FAIL
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
        Lang.translate("goggles.plc").style(ChatFormatting.GRAY).forGoggles(tooltip)
        Lang.translate("goggles.state").style(ChatFormatting.GRAY)
            .space().add(computerBehavior.workState.text())
            .forGoggles(tooltip, 1)
        behaviors.filterIsInstance<DinRackPlcComponent>().forEach { it.addToGoggleTooltip(tooltip, isPlayerSneaking) }
        return true
    }

    private class ComputerBehavior(
        private val workDrawBehavior: WorkDrawBehavior,
        private val modemBehavior: DigibusModemBehavior,
    ): PowerGridBehavior {
        /** Derived each server tick, synced to clients for the work light. */
        var workState = WorkState.OFF
            private set

        /** Set from the computer thread via the `plc` peripheral; read on the server tick. */
        @Volatile
        var actionLight = false
            private set
        /** Last [actionLight] value persisted, to request a save only when it changes. */
        private var persistedAction = false

        /** Server-only live computer; created lazily from the persisted id, closed on detach. */
        private var computer: ServerComputer? = null
        private var context: Behavior.AttachContext? = null

        /** Cached computer id (also stored on the item's data component); -1 until assigned. */
        private var computerId = -1

        override fun onAttach(ctx: Behavior.AttachContext) {
            context = ctx
        }

        override fun onDetach() {
            computer?.close()
            computer = null
            context = null
        }

        override fun electricalTick(): PowerGridBehavior.TickResult {
            val computer = ensureComputer()
            if (computer != null) {
                // CC's registry ticks the computer; keepAlive stops it from timing out and unloading.
                computer.keepAlive()
                if (workDrawBehavior.powered && !computer.isOn) computer.turnOn()
                else if (!workDrawBehavior.powered && computer.isOn) computer.shutdown()  // force shutdown on power loss
            }

            workState = when {
                !workDrawBehavior.powered -> WorkState.OFF
                computer == null || computer.isOn -> WorkState.ON
                else -> WorkState.OFF
            }

            // Light state rides Power Grid's periodic node-state sync (~every 5 ticks) via the rack's
            // sync appender, so only a persistent change needs a save here.
            return if (actionLight != persistedAction) {
                persistedAction = actionLight
                PowerGridBehavior.TickResult.SAVE
            } else {
                PowerGridBehavior.TickResult.NONE
            }
        }

        fun ensureComputer(): ServerComputer? {
            computer?.let { return it }
            val ctx = context ?: return null
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
                c.setPeripheral(ComputerSide.BACK, PlcPeripheral())
                // Connect the computer to the PLC bus; component peripherals are already advertised there.
                modemBehavior.attachTo(c, ComputerSide.BOTTOM, ctx)

//                val sideStack = LinkedList(ComputerSide.entries).apply {
//                    remove(ComputerSide.BACK)
//                    remove(ComputerSide.BOTTOM)
//                }
//
//
            }
        }

        // --- called from the computer thread via PlcPeripheral (actionLight is @Volatile) ---

        override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
            actionLight = tag.getBoolean("ActionLight")
            persistedAction = actionLight
        }

        override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
            if (actionLight) tag.putBoolean("ActionLight", true)
        }

        override fun writeSync(buffer: FriendlyByteBuf) {
            buffer.writeByte(workState.ordinal or (if (actionLight) ACTION_BIT else 0))
        }

        override fun readSync(buffer: FriendlyByteBuf) {
            val b = buffer.readByte().toInt()
            workState = WorkState.entries[b and WORK_MASK]
            actionLight = (b and ACTION_BIT) != 0
        }

        inner class PlcPeripheral : IPeripheral {
            override fun getType(): String = "plc"

            /** Turn the action light on or off. */
            @LuaFunction
            fun setActionLight(on: Boolean) {
                actionLight = on
            }

            @LuaFunction
            fun getActionLight(): Boolean = actionLight

            /** Reboot this controller's computer. */
            @LuaFunction(mainThread = true)
            fun reboot() {
                computer?.reboot()
            }

            override fun equals(other: IPeripheral?): Boolean = other === this
        }
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