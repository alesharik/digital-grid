package com.alesharik.digitalgrid.din.item.plc

import com.alesharik.digitalgrid.Digitalgrid
import dan200.computercraft.api.component.ComputerComponent

interface DinRackPlcComputerComponent {
    var actionLight: Boolean

    val railVoltage: Float

    companion object {
        val COMPONENT = ComputerComponent.create<DinRackPlcComputerComponent>(Digitalgrid.ID, "din_rack_plc")
    }
}