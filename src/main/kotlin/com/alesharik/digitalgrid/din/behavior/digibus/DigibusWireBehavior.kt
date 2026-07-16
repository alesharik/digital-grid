package com.alesharik.digitalgrid.din.behavior.digibus

/**
 * Just pass-through wire
 */
class DigibusWireBehavior : DigibusBehavior {
    private var wire: DigibusWire? = null

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
    }
}
