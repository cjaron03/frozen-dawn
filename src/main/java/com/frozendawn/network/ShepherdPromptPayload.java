package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** She has something to say: draw "Got a minute?" Which conversation it is stays on the server. */
public record ShepherdPromptPayload() implements CustomPacketPayload {
    public static final Type<ShepherdPromptPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shepherd_prompt"));
    public static final StreamCodec<ByteBuf, ShepherdPromptPayload> STREAM_CODEC = StreamCodec.unit(new ShepherdPromptPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
