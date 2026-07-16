package com.alesharik.digitalgrid.din.rack

import com.alesharik.digitalgrid.din.DinRackEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

open class DinRackItem(
    props: Properties,
    private val entityClass: Class<out DinRackEntity>,
) : Item(props) {
    /** [entityClass] must have a public no-arg constructor. */
    open fun createEntity(stack: ItemStack): DinRackEntity =
        entityClass.getDeclaredConstructor().newInstance()
}
