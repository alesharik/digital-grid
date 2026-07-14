package com.alesharik.digitalgrid.din.rack

import com.alesharik.digitalgrid.din.DinRackEntity
import net.minecraft.world.item.Item

class DinRackItem(
    props: Properties,
    private val entityClass: Class<out DinRackEntity>,
) : Item(props) {
    /** [entityClass] must have a public no-arg constructor. */
    fun createEntity(): DinRackEntity = entityClass.getDeclaredConstructor().newInstance()
}