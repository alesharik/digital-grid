package com.alesharik.digitalgrid.utils

import com.alesharik.digitalgrid.Digitalgrid
import net.createmod.catnip.lang.LangBuilder
import net.minecraft.network.chat.Component

object Lang {
    fun builder() = LangBuilder(Digitalgrid.ID)

    fun translate(str: String) = builder().translate(str)

    fun text(str: String) = builder().text(str)

    fun translateItem(str: String) = Component.translatable("item.${Digitalgrid.ID}.$str")
}
