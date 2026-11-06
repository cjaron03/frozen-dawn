package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** She went down in this conversation. The stop panel holds until the server sends what she says on the way back. */
public record ShepherdCrashPayload(int conversation) implements CustomPacketPayload {
    public static final Type<ShepherdCrashPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shepherd_crash"));
    public static final StreamCodec<ByteBuf, ShepherdCrashPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, ShepherdCrashPayload::conversation, ShepherdCrashPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
