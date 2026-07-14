package com.alesharik.digitalgrid.din.behavior.digibus

import com.alesharik.digitalgrid.din.behavior.Behavior
import dan200.computercraft.api.network.wired.WiredNode
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.core.computer.ComputerSide
import dan200.computercraft.shared.computer.core.ServerComputer
import dan200.computercraft.shared.peripheral.modem.ModemState
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemElement
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemLocalPeripheral
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemPeripheral
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3

class DigibusModemBehavior: DigibusBehavior {
    private var wire: DigibusWire? = null
    private var internalWire: WiredNode? = null
    private var peripheral: WiredModemPeripheral? = null

    override fun getWire(context: DigibusBehavior.DigibusWireContext): DigibusWire {
        wire?.let { return it }

        context.markChanged()
        return DigibusWire(context.blockEntity).also {
            wire = it
        }
    }

    override fun onDetach() {
        wire?.remove()
        wire = null
        internalWire?.remove()
        internalWire = null
    }

    /** Attach the modem to [computer] so its programs can reach bus peripherals. */
    fun attachTo(computer: ServerComputer, side: ComputerSide, ctx: Behavior.AttachContext) {
        val me = ModemElement(ctx.level)
        val pe = Peripheral(ctx.blockEntity, me)
        me.link(pe)

        internalWire = me.node
        peripheral = pe

        ctx.markChanged()

        computer.setPeripheral(side, peripheral!!)
    }

    private class Peripheral(
        private val blockEntity: BlockEntity,
        modemElement: ModemElement
    ) : WiredModemPeripheral(ModemState(), modemElement, WiredModemLocalPeripheral { null }, blockEntity) {
        override fun getPosition(): Vec3 = blockEntity.blockPos.toVec3()
    }

    private class ModemElement(private val level: Level) : WiredModemElement() {
        private var peripheral: WiredModemPeripheral? = null

        fun link(peripheral: WiredModemPeripheral) {
            this.peripheral = peripheral
        }

        override fun getLevel(): Level = level

        override fun getPosition(): Vec3 = peripheral?.position ?: Vec3.ZERO

        override fun attachPeripheral(name: String, peripheral: IPeripheral) {
            this.peripheral?.attachPeripheral(name, peripheral)
        }

        override fun detachPeripheral(name: String) {
            peripheral?.detachPeripheral(name)
        }
    }
}