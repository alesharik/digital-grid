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
 * The PLC's wired-modem access point onto the PLC bus. The computer attaches to a wired
 * modem ([modemElement]); the PLC's own pluggable components advertise named peripherals
 * from the PLC's bus [connector] node, so the computer sees them as remote peripherals —
 * a single node cannot expose its own peripherals to its own modem, hence the two nodes.
 * The rack additionally links [connector] to touching bus modules
 * (DinRackBlockEntity.refreshPlcBusLinks), extending the same network across the bus.
 *
 * Server-side only. Starts empty; components populate it later via [register].
 *
 * NOTE: built against CC internal `shared.*` classes and not yet runtime-verified
 * end-to-end — validate peripheral visibility when the first bus peripheral lands.
 */
class PlcModemBus(private val rack: BlockEntity) {
    private val position: Vec3 get() = Vec3.atCenterOf(rack.blockPos)

    /** The PLC's node on the PLC bus; also where component peripherals are advertised from. */
    val connector = PlcBusConnector(rack)

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
        modemElement.node.connectTo(connector.node)
    }

    /** Attach the modem to [computer] so its programs can reach bus peripherals. */
    fun attachTo(computer: ServerComputer, side: ComputerSide) {
        computer.setPeripheral(side, modemPeripheral)
    }

    fun register(name: String, peripheral: IPeripheral) {
        peripherals[name] = peripheral
        connector.setPeripherals(HashMap(peripherals))
    }

    fun unregister(name: String) {
        if (peripherals.remove(name) != null) {
            connector.setPeripherals(HashMap(peripherals))
        }
    }

    /** Tear down the internal network; call when the PLC leaves the rack. */
    fun remove() {
        modemElement.node.remove()
        connector.remove()
    }
}
