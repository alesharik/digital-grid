package com.alesharik.digitalgrid.din.item

import com.alesharik.digitalgrid.DigitalgridConfig
import com.alesharik.digitalgrid.client.PartialModels
import com.alesharik.digitalgrid.din.DINUnit
import com.alesharik.digitalgrid.din.DinRackEntity
import com.alesharik.digitalgrid.din.behavior.Behavior
import com.alesharik.digitalgrid.din.behavior.digibus.DigibusPeripheralBehavior
import com.alesharik.digitalgrid.din.behavior.powergrid.SwitchableWorkDrawBehavior
import com.alesharik.digitalgrid.din.behavior.powergrid.WorkDrawBehavior
import com.alesharik.digitalgrid.utils.Lang
import com.alesharik.digitalgrid.utils.light.LightIndicator
import com.google.errorprone.annotations.concurrent.GuardedBy
import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.render.RenderTypes
import dan200.computercraft.api.filesystem.Mount
import dan200.computercraft.api.filesystem.WritableMount
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.media.IMedia
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.shared.ModRegistry
import dan200.computercraft.shared.platform.PlatformHelper
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.ChatFormatting
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStack.EMPTY
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox
import thedarkcolour.kotlinforforge.neoforge.kotlin.enumMapOf
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Stream
import kotlin.concurrent.Volatile
import net.minecraft.core.BlockPos as MinecraftBlockPos

class DinRackPlcDriveEntity : DinRackEntity {
    override val shape: VoxelShape = SHAPE
    override val terminalBoundingBox: Array<TerminalBoundingBox> = emptyArray()
    override val width: DINUnit = DINUnit(3)

    private val peripheral = DrivePeripheral()
    private val digibusBehavior = DigibusPeripheralBehavior(peripheral)

    private val workDrawBehavior by lazy { WorkDrawBehavior.forBus(DigitalgridConfig.CONFIG.plcDrive.currentDraw) }
    private val workDrawDriveBehavior by lazy { SwitchableWorkDrawBehavior.forBus(DigitalgridConfig.CONFIG.plcDrive.diskCurrentDraw) }

    override val behaviors: List<Behavior> by lazy { listOf(digibusBehavior, peripheral, workDrawBehavior, workDrawDriveBehavior) }

    @Volatile
    private var lightState: LightState = LightState.EMPTY

    override fun render(
        be: BlockState,
        en: DinRackEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val buffer = CachedBuffers.partial(PartialModels.DIN_PLC_DRIVE, be)
        buffer.light<SuperByteBuffer>(light)
            .renderInto(ms, bufferSource.getBuffer(RenderTypes.entitySolidBlockMipped()))
        LIGHT_STATES[lightState]?.render(be, ms, bufferSource)
        if (peripheral.isDiskPresent()) {
            val diskBuffer = CachedBuffers.partial(PartialModels.DIN_PLC_DRIVE_DISK, be)
            diskBuffer.light<SuperByteBuffer>(light)
                .renderInto(ms, bufferSource.getBuffer(RenderTypes.entitySolidBlockMipped()))
        }
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        Lang.translate("goggles.plc_drive").style(ChatFormatting.GRAY).forGoggles(tooltip)
        Lang.translate("goggles.digibus.name").style(ChatFormatting.GRAY)
            .space().add(Lang.text(digibusBehavior.peripheralName).style(ChatFormatting.AQUA))
            .forGoggles(tooltip, 1)

        val diskLabel = peripheral.getDiskLabelForTooltip()
        Lang.translate("goggles.plc_drive.label").space().apply {
            if (diskLabel != null) {
                add(Lang.text(diskLabel).style(ChatFormatting.AQUA))
                    .style(ChatFormatting.GRAY)
            } else {
                add(Lang.translate("goggles.plc_drive.empty").style(ChatFormatting.DARK_GRAY))
            }
        }.forGoggles(tooltip, 1)

        return true
    }

    override fun useItemOn(
        item: ItemStack,
        st: BlockState,
        lv: Level,
        pos: MinecraftBlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): ItemInteractionResult {
        return peripheral.handleInsert(item, lv, pos, player)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: MinecraftBlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult {
        return peripheral.handleEject(level, pos, player)
    }

    private class MountInfo {
        var mountPath: String? = null
    }

    private enum class MountResult {
        NO_MEDIA, NOT_ALLOWED, CHANGED
    }

    private enum class LightState {
        EMPTY, GREEN, RED
    }

    /**
     * The drive peripheral: holds all disk state, mount logic, and Lua methods.
     * Ported from CC-Tweaked's DiskDriveBlockEntity + DiskDrivePeripheral.
     */
    private inner class DrivePeripheral : IPeripheral, Behavior {
        @GuardedBy("this")
        private var ctx: Behavior.AttachContext? = null

        @GuardedBy("this")
        private val computers = HashMap<IComputerAccess, MountInfo>()

        /** Source of truth: persisted and synced. */
        @GuardedBy("this")
        private var diskStack: ItemStack = EMPTY

        /** The stack [currentMedia] and [mount] were derived from. */
        @GuardedBy("this")
        private var mediaStack: ItemStack = EMPTY

        @GuardedBy("this")
        private var currentMedia: IMedia? = null

        @GuardedBy("this")
        private var mount: Mount? = null

        private val ejectQueued = AtomicBoolean(false)
        private val stackDirty = AtomicBoolean(false)

        // IPeripheral implementation
        override fun getType(): String = "drive"

        // Behavior hooks
        override fun onAttach(ctx: Behavior.AttachContext) {
            synchronized(this) {
                this.ctx = ctx
            }
            updateMedia()
        }

        override fun onDetach(removed: Boolean) {
            synchronized(this) {
                computers.clear()
                if (removed) {
                    // Real removal (player pull, block break, wrench rotation) — drop the disk
                    // into the world. Chunk unload / client re-sync (removed == false) must not
                    // touch the world; the disk stays put in NBT.
                    val level = ctx?.level
                    val pos = ctx?.pos
                    if (level != null && pos != null && !level.isClientSide && !diskStack.isEmpty) {
                        Block.popResource(level, pos, diskStack)
                    }
                    // Idempotent: a second onDetach(false) call (from invalidate()) sees an
                    // already-empty diskStack and does nothing.
                    diskStack = EMPTY
                    mediaStack = EMPTY
                }
                ctx = null
            }
        }

        override fun serverTick(level: ServerLevel, be: BlockEntity) {
            if (stackDirty.getAndSet(false)) {
                ctx?.markChanged()
                ctx?.requestSync()
            }
            if (ejectQueued.getAndSet(false)) {
                ejectContents()
            }
        }

        override fun attach(computer: IComputerAccess) {
            synchronized(this) {
                val info = MountInfo()
                computers[computer] = info
                val serverLevel = ctx?.level as? ServerLevel ?: return
                if (!diskStack.isEmpty) {
                    // Runs on the computer thread — must not touch the block entity directly.
                    mountDisk(computer, info, getOrCreateMount(serverLevel, immediate = false))
                }
            }
        }

        // Persistence — write/read run for both world save and the client sync packet.
        override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
            val stack = synchronized(this) { diskStack }
            if (!stack.isEmpty) tag.put("Disk", stack.save(registries))
        }

        override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
            val stack = if (tag.contains("Disk")) ItemStack.parseOptional(registries, tag.getCompound("Disk")) else EMPTY
            synchronized(this) { diskStack = stack }
            // media/mount/light are recomputed in onAttach (which runs after read and has the level).
        }

        override fun detach(computer: IComputerAccess) {
            synchronized(this) {
                computers.remove(computer)?.let {
                    unmountDisk(computer, it)
                }
            }
        }

        override fun equals(other: IPeripheral?): Boolean = other === this

        override fun getTarget(): Any = this@DinRackPlcDriveEntity

        // Lua methods (ported from DiskDrivePeripheral)
        @LuaFunction
        fun isDiskPresent(): Boolean {
            return synchronized(this) { !diskStack.isEmpty }
        }

        @LuaFunction
        fun getDiskLabel(): Array<Any?>? {
            val (media, stack) = synchronized(this) { currentMedia to diskStack }
            val level = ctx?.level as? ServerLevel ?: return null
            return media?.let { arrayOf(it.getLabel(level.registryAccess(), stack)) }
        }

        @LuaFunction(mainThread = true)
        fun setDiskLabel(label: String?) {
            val result = synchronized(this) {
                val media = currentMedia ?: return@synchronized MountResult.NO_MEDIA
                val stack = diskStack.copy()
                if (!media.setLabel(stack, label)) {
                    return@synchronized MountResult.NOT_ALLOWED
                }
                // Updating both (not just diskStack) is deliberate: a label change must not
                // make the next updateMedia() see a difference and unmount the disk.
                diskStack = stack
                mediaStack = stack
                MountResult.CHANGED
            }
            when (result) {
                MountResult.NOT_ALLOWED -> throw LuaException("Disk label cannot be changed")
                MountResult.CHANGED -> {
                    ctx?.markChanged()
                    ctx?.requestSync()
                }
                MountResult.NO_MEDIA -> {}
            }
        }

        @LuaFunction
        fun hasData(computer: IComputerAccess): Boolean {
            return synchronized(this) { computers.containsKey(computer) && mount != null }
        }

        @LuaFunction
        fun getMountPath(computer: IComputerAccess): String? {
            synchronized(this) {
                val info = computers[computer]
                return info?.mountPath
            }
        }

        @LuaFunction
        fun hasAudio(): Boolean = false

        @LuaFunction
        fun getAudioTitle(): Any? = null

        @LuaFunction
        fun playAudio() {
            throw LuaException("Audio is not supported")
        }

        @LuaFunction
        fun stopAudio() {
            throw LuaException("Audio is not supported")
        }

        @LuaFunction
        fun ejectDisk() {
            ejectQueued.set(true)
        }

        @LuaFunction
        fun getDiskID(): Array<Any?>? {
            // Disk ID is stored on the item as CC's DISK_ID DataComponent (NonNegativeId).
            val stack = synchronized(this) { diskStack }
            val id = stack.get(ModRegistry.DataComponents.DISK_ID.get()) ?: return null
            return arrayOf(id.id())
        }

        // Media/mount logic (ported from DiskDriveBlockEntity)
        private fun updateMedia() {
            synchronized(this) {
                if (ItemStack.isSameItemSameComponents(diskStack, mediaStack)) {
                    return
                }

                val newMedia = if (diskStack.isEmpty) null else PlatformHelper.get().getMedia(diskStack)

                // Update light state
                lightState = if (diskStack.isEmpty) {
                    LightState.EMPTY
                } else if (newMedia != null) {
                    LightState.GREEN
                } else {
                    LightState.RED
                }
                workDrawDriveBehavior.enabled = !diskStack.isEmpty

                // Unmount old disk
                if (!mediaStack.isEmpty) {
                    for (computer in computers.entries) {
                        unmountDisk(computer.key, computer.value)
                    }
                }

                // Use new media
                mount = null
                mediaStack = diskStack
                currentMedia = newMedia

                // Mount new disk if computers are attached
                if (!diskStack.isEmpty && computers.isNotEmpty()) {
                    val serverLevel = ctx?.level as? ServerLevel ?: return
                    val newMount = getOrCreateMount(serverLevel, immediate = true)
                    for ((key, value) in computers) {
                        mountDisk(key, value, newMount)
                    }
                }
            }
        }

        /**
         * [immediate] is true when called from [updateMedia] on the main thread (safe to persist
         * right away); false when called from [attach], which runs on the computer thread and
         * must defer persistence to [serverTick] via [stackDirty].
         */
        @GuardedBy("this")
        private fun getOrCreateMount(level: ServerLevel, immediate: Boolean): Mount? {
            val media = currentMedia ?: return null
            if (mount != null) return mount

            // media.createDataMount MUTATES the passed-in stack to assign a disk ID (via
            // NonNegativeId.getOrCreate) — the mutated copy must be written back, or a fresh
            // floppy gets a new disk ID on every mount and getDiskID() always returns nil.
            val stack = mediaStack.copy()
            mount = media.createDataMount(stack, level)
            if (!ItemStack.isSameItemSameComponents(stack, mediaStack)) {
                diskStack = stack
                mediaStack = stack
                if (immediate) {
                    ctx?.markChanged()
                    ctx?.requestSync()
                } else {
                    stackDirty.set(true)
                }
            }
            return mount
        }

        private fun mountDisk(computer: IComputerAccess, info: MountInfo, mnt: Mount?) {
            if (mnt != null) {
                var n = 1
                while (info.mountPath == null && n <= 11) { // Try disk, disk2, ..., disk11
                    info.mountPath = if (mnt is WritableMount) {
                        computer.mountWritable(if (n == 1) "disk" else "disk$n", mnt as WritableMount)
                    } else {
                        computer.mount(if (n == 1) "disk" else "disk$n", mnt)
                    }
                    n++
                }
            }
            computer.queueEvent("disk", computer.getAttachmentName())
        }

        private fun unmountDisk(computer: IComputerAccess, info: MountInfo) {
            if (info.mountPath != null) {
                computer.unmount(info.mountPath)
                info.mountPath = null
            }
            computer.queueEvent("disk_eject", computer.getAttachmentName())
        }

        private fun ejectContents() {
            val context = ctx ?: return
            val level = context.level
            if (level.isClientSide) return

            val stack = synchronized(this) { diskStack }
            if (stack.isEmpty) return

            synchronized(this) { diskStack = EMPTY }
            updateMedia()
            context.markChanged()
            context.requestSync()

            // Drop item at the drive position
            Block.popResource(level, context.pos, stack)
            level.levelEvent(net.minecraft.world.level.block.LevelEvent.SOUND_DISPENSER_DISPENSE, context.pos, 0)
        }

        // Tooltip helper (client-side; goggle tooltips render on the client, so there is no
        // ServerLevel to cast to here).
        fun getDiskLabelForTooltip(): String? {
            val (media, stack) = synchronized(this) { currentMedia to diskStack }
            val registries = ctx?.level?.registryAccess() ?: return null
            return media?.getLabel(registries, stack)
        }

        // Interaction handlers
        fun handleInsert(item: ItemStack, lv: Level, pos: MinecraftBlockPos, player: Player): ItemInteractionResult {
            if (synchronized(this) { !diskStack.isEmpty }) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION // Already has disk
            }

            val testMedia = PlatformHelper.get().getMedia(item)
                ?: return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION // Not a valid disk
            if (testMedia.getAudio(lv.registryAccess(), item) != null) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION // Does not support audio disks
            }

            // Client predicts success; disk state is mutated server-side only.
            if (lv.isClientSide) return ItemInteractionResult.sidedSuccess(true)

            // Insert only when empty
            synchronized(this) { diskStack = item.copyWithCount(1) }
            updateMedia()
            ctx?.markChanged()
            ctx?.requestSync()
            if (!player.hasInfiniteMaterials()) {
                item.shrink(1)
            }

            lv.playSound(
                null, pos, net.minecraft.sounds.SoundEvents.DISPENSER_DISPENSE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f
            )
            return ItemInteractionResult.sidedSuccess(false)
        }

        fun handleEject(lv: Level, pos: MinecraftBlockPos, player: Player): InteractionResult {
            if (lv.isClientSide) {
                return InteractionResult.sidedSuccess(true)
            }

            val stack = synchronized(this) { diskStack }
            if (stack.isEmpty) {
                return InteractionResult.PASS
            }

            // Give disk to player
            player.inventory.placeItemBackInInventory(stack.copy())
            synchronized(this) { diskStack = EMPTY }
            updateMedia()
            ctx?.markChanged()
            ctx?.requestSync()

            lv.playSound(
                null, pos, net.minecraft.sounds.SoundEvents.DISPENSER_DISPENSE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f
            )
            return InteractionResult.sidedSuccess(false)
        }
    }

    companion object {
        // Placeholder shape (reused from relay)
        private val SHAPE = Stream.of(
            Block.box(0.0, 3.0, 14.0, 3.0, 8.0, 15.0),
            Block.box(0.0, 10.0, 14.0, 3.0, 15.0, 15.0),
            Block.box(0.0, 4.0, 13.0, 3.0, 7.0, 14.0),
            Block.box(0.0, 11.0, 13.0, 3.0, 14.0, 14.0),
            Stream.of(
                Block.box(0.0, 4.0, 11.0, 1.0, 14.0, 12.0),
                Block.box(1.0, 4.0, 11.0, 2.0, 7.0, 12.0),
                Block.box(2.0, 4.0, 11.0, 3.0, 14.0, 12.0),
                Block.box(0.0, 4.0, 12.0, 3.0, 14.0, 13.0),
                Block.box(1.0, 13.0, 11.0, 2.0, 14.0, 12.0)
            ).reduce { v1, v2 -> Shapes.join(v1, v2, BooleanOp.OR)}.get()
        ).reduce { v1, v2 -> Shapes.join(v1, v2, BooleanOp.OR) }.get().optimize()

        private val LIGHT_STATES = enumMapOf(
            LightState.EMPTY to LightIndicator.off(PartialModels.DIN_PLC_DRIVE_LIGHT),
            LightState.GREEN to LightIndicator.green(PartialModels.DIN_PLC_DRIVE_LIGHT),
            LightState.RED to LightIndicator.red(PartialModels.DIN_PLC_DRIVE_LIGHT)
        )
    }
}
