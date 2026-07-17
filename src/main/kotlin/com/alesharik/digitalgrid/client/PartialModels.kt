package com.alesharik.digitalgrid.client

import com.alesharik.digitalgrid.Digitalgrid
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import net.minecraft.resources.ResourceLocation

object PartialModels {
    val DIN_PATCH = dinModel("patch")
    val DIN_CASING = dinModel("casing")
    val DIN_CASING_DIGIBUS = dinModel("casing_digibus")
    val DIN_BATTERY = dinModel("battery")
    val DIN_BATTERY_LIGHT = dinModel("battery_light")
    val DIN_POWER_SUPPLY = dinModel("power_supply")
    val DIN_POWER_SUPPLY_LIGHT = dinModel("power_supply_light")
    val DIN_PLC = dinModel("plc")
    val DIN_PLC_WORK_LIGHT = dinModel("plc_work_light")
    val DIN_PLC_ACTION_LIGHT = dinModel("plc_action_light")
    val DIN_PLC_IO = dinModel("plc_io")
    val DIN_PLC_IO_LIGHTS = Array(4) { dinModel("plc_io_light_$it") }
    val DIN_PLC_RELAY = dinModel("plc_relay")
    val DIN_PLC_RELAY_LIGHT = dinModel("plc_relay_light")
    val DIN_PLC_ENDER_MODEM = dinModel("plc_ender_modem")
    val DIN_PLC_WIRELESS_MODEM = dinModel("plc_wireless_modem")
    val DIN_PLC_SPEAKER = dinModel("plc_speaker")

    val WATCHDOG_TIMER_LIGHT_WORK = blockModel("watchdog_timer_light_work")
    val WATCHDOG_TIMER_LIGHT_ACTIVITY = blockModel("watchdog_timer_light_activity")

    // Forces the object initializer to run; must be called during client mod
    // construction so all partials exist before ModelEvent.RegisterAdditional.
    fun init() {}

    private fun dinModel(path: String) = model("din/$path")

    private fun blockModel(path: String) = model("block/$path")

    private fun model(path: String): PartialModel =
        PartialModel.of(ResourceLocation.fromNamespaceAndPath(Digitalgrid.ID, path))
}