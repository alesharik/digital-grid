package com.alesharik.digitalgrid.din.item.plc

import dan200.computercraft.api.network.wired.WiredNode
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemElement
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3

/**
 * One module's node on the PLC bus — the piece of "modem cable" a bus-enabled module
 * contributes. The rack links nodes of directly touching modules; CC's wired network
 * then merges and splits segments as nodes connect and are removed.
 *
 * Peripherals advertised here are visible to every modem on the merged network except
 * ones on this same node (CC semantics: a node cannot see its own peripherals).
 *
 * Server-side only.
 */
class PlcBusConnector(private val rack: BlockEntity) {
    private val element = object : WiredModemElement() {
        override fun getLevel(): Level = rack.level ?: error("PLC bus connector accessed a null level")
        override fun getPosition(): Vec3 = Vec3.atCenterOf(rack.blockPos)
        // Nothing consumes peripherals on this node; attach/detach no-op.
        override fun attachPeripheral(name: String, peripheral: IPeripheral) {}
        override fun detachPeripheral(name: String) {}
    }

    val node: WiredNode get() = element.node

    /** Replace the set of peripherals this module advertises on the bus. */
    fun setPeripherals(peripherals: Map<String, IPeripheral>) {
        node.updatePeripherals(peripherals)
    }

    /** Idempotent; CC ignores already-present connections. */
    fun connectTo(other: PlcBusConnector) {
        node.connectTo(other.node)
    }

    /** Severs one link; CC ignores absent connections. */
    fun disconnectFrom(other: PlcBusConnector) {
        node.disconnectFrom(other.node)
    }

    /** Tear down: severs all links (splitting the network) and drops advertised peripherals. */
    fun remove() {
        node.remove()
    }
}
