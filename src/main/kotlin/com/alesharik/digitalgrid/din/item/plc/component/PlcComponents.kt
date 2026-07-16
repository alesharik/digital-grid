package com.alesharik.digitalgrid.din.item.plc.component

import com.mojang.serialization.Codec
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

data class PlcComponents(val ids: List<ResourceLocation>) {
    operator fun contains(id: ResourceLocation) = id in ids

    companion object {
        val EMPTY = PlcComponents(emptyList())

        val CODEC: Codec<PlcComponents> =
            ResourceLocation.CODEC.listOf().xmap(::PlcComponents, PlcComponents::ids)

        val STREAM_CODEC: StreamCodec<ByteBuf, PlcComponents> =
            // Bounded: data components are decoded from untrusted creative-client packets.
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(16)).map(::PlcComponents, PlcComponents::ids)
    }
}
