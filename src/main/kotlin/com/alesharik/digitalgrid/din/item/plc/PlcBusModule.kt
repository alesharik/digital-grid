package com.alesharik.digitalgrid.din.item.plc

/**
 * Implemented by [com.alesharik.digitalgrid.din.DinRackEntity]s that participate in the
 * PLC bus. Directly touching bus modules (`a.u + a.width == b.u`, continuing across rack
 * edges) merge into one wired network; the rack does the linking in
 * `DinRackBlockEntity.refreshPlcBusLinks`.
 */
interface PlcBusModule {
    /**
     * This module's bus node, created on first call. Returns null on the client and while
     * the module is not attached to a rack — bus objects exist only server-side.
     */
    fun busConnector(): PlcBusConnector?
}
