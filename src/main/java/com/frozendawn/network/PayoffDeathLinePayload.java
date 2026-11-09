package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Tells the dying player's client to draw the CH-18 line on its death screen. */
public record PayoffDeathLinePayload() implements CustomPacketPayload {
    public static final Type<PayoffDeathLinePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "payoff_death_line"));
    public static final StreamCodec<ByteBuf, PayoffDeathLinePayload> STREAM_CODEC = StreamCodec.unit(new PayoffDeathLinePayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
