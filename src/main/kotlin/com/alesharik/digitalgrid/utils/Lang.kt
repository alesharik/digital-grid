package com.alesharik.digitalgrid.utils

import com.alesharik.digitalgrid.Digitalgrid
import net.createmod.catnip.lang.LangBuilder

object Lang {
    fun builder() = LangBuilder(Digitalgrid.ID)

    fun translate(str: String) = builder().translate(str)

    fun text(str: String) = builder().text(str)
}
