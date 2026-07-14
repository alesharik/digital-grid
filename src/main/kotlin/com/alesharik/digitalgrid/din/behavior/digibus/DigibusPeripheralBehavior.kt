package com.alesharik.digitalgrid.din.behavior.digibus

import dan200.computercraft.api.peripheral.IPeripheral
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag

class DigibusPeripheralBehavior(
    private val peripheral: IPeripheral,
): DigibusBehavior {
    var busId: Int = -1
        private set

    val type: String get() = peripheral.type

    val peripheralName: String get() = if (busId >= 0) "${peripheral.type}_$busId" else peripheral.type

    private var wire: DigibusWire? = null

    fun assignBusId(id: Int) {
        busId = id
        wire?.setPeripherals(mapOf(peripheralName to peripheral))
    }

    override fun getWire(context: DigibusBehavior.DigibusWireContext): DigibusWire {
        wire?.let { return it }
        context.markChanged()
        return DigibusWire(context.blockEntity).also {
            it.setPeripherals(mapOf(peripheralName to peripheral))
            wire = it
        }
    }

    override fun onDetach() {
        wire?.remove()
        wire = null
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        if (tag.contains("Id")) busId = tag.getInt("Id")
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        if (busId >= 0) tag.putInt("Id", busId)
    }
}
