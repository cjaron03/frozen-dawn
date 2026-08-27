package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One server-timed pulse while a player deliberately holds use on Maeve. */
public record HeartMaeveErasePayload() implements CustomPacketPayload {
    public static final Type<HeartMaeveErasePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "heart_maeve_erase"));
    public static final StreamCodec<ByteBuf, HeartMaeveErasePayload> STREAM_CODEC =
            StreamCodec.unit(new HeartMaeveErasePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
