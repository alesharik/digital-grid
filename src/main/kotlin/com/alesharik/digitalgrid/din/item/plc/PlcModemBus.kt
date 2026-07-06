package com.alesharik.digitalgrid.din.item.plc

import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.core.computer.ComputerSide
import dan200.computercraft.shared.computer.core.ServerComputer
import dan200.computercraft.shared.peripheral.modem.ModemState
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemElement
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemLocalPeripheral
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemPeripheral
import dan200.computercraft.shared.platform.ComponentAccess
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3

/**
 * The PLC's internal wired-modem bus. The computer attaches to a wired modem ([modemElement]); pluggable
 * components advertise named peripherals from a **second, connected** node ([hostElement]), so the
 * computer sees them as remote peripherals — exactly like a computer wired to a modem with cabled
 * peripherals. A single node cannot expose its own peripherals to its own modem, hence the two nodes.
 *
 * Server-side only. Starts empty; components populate it later via [register].
 *
 * NOTE: built against CC internal `shared.*` classes and not yet runtime-verified end-to-end — validate
 * peripheral visibility when the first [DinRackPlcComponent] peripheral lands.
 */
class PlcModemBus(private val rack: BlockEntity) {
    private val position: Vec3 get() = Vec3.atCenterOf(rack.blockPos)

    /** Node the computer's modem lives on; forwards network peripheral changes to the modem peripheral. */
    private val modemElement = object : WiredModemElement() {
        override fun getLevel(): Level = rack.level ?: error("PLC modem accessed a null level")
        override fun getPosition(): Vec3 = position
        override fun attachPeripheral(name: String, peripheral: IPeripheral) {
            modemPeripheral.attachPeripheral(name, peripheral)
        }
        override fun detachPeripheral(name: String) {
            modemPeripheral.detachPeripheral(name)
        }
    }

    /** Node the component peripherals are advertised from. Nothing consumes here, so attach/detach no-op. */
    private val hostElement = object : WiredModemElement() {
        override fun getLevel(): Level = rack.level ?: error("PLC modem accessed a null level")
        override fun getPosition(): Vec3 = position
        override fun attachPeripheral(name: String, peripheral: IPeripheral) {}
        override fun detachPeripheral(name: String) {}
    }

    private val modemPeripheral: WiredModemPeripheral = object : WiredModemPeripheral(
        ModemState(),
        modemElement,
        // No local (own-block) peripheral: this is a purely internal bus.
        WiredModemLocalPeripheral(ComponentAccess { null }),
        rack,
    ) {
        // getNetwork() is already implemented by WiredModemPeripheral (returns modemElement's node);
        // only the modem's world position is left abstract.
        override fun getPosition(): Vec3 = position
    }

    private val peripherals = HashMap<String, IPeripheral>()

    init {
        modemElement.node.connectTo(hostElement.node)
    }

    /** Attach the modem to [computer] so its programs can reach bus peripherals. */
    fun attachTo(computer: ServerComputer, side: ComputerSide) {
        computer.setPeripheral(side, modemPeripheral)
    }

    fun register(name: String, peripheral: IPeripheral) {
        peripherals[name] = peripheral
        hostElement.node.updatePeripherals(HashMap(peripherals))
    }

    fun unregister(name: String) {
        if (peripherals.remove(name) != null) {
            hostElement.node.updatePeripherals(HashMap(peripherals))
        }
    }

    /** Tear down the internal network; call when the PLC leaves the rack. */
    fun remove() {
        modemElement.node.remove()
        hostElement.node.remove()
    }
}
