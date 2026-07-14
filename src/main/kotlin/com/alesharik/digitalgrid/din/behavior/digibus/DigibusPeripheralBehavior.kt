package com.alesharik.digitalgrid.din.behavior.digibus

import dan200.computercraft.api.peripheral.IPeripheral

class DigibusPeripheralBehavior(
    private val peripheral: IPeripheral,
): DigibusBehavior {
    private var wire: DigibusWire? = null

    override fun getWire(context: DigibusBehavior.DigibusWireContext): DigibusWire {
        wire?.let { return it }
        context.markChanged()
        return DigibusWire(context.blockEntity).also {
            it.setPeripherals(mapOf(peripheral.type to peripheral))
            wire = it
        }
    }

    override fun onDetach() {
        wire?.remove()
        wire = null
    }
}