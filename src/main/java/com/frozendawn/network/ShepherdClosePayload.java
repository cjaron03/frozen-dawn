package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Both ways: the player pressed Esc, or the server ended the conversation. */
public record ShepherdClosePayload() implements CustomPacketPayload {
    public static final Type<ShepherdClosePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shepherd_close"));
    public static final StreamCodec<ByteBuf, ShepherdClosePayload> STREAM_CODEC = StreamCodec.unit(new ShepherdClosePayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
