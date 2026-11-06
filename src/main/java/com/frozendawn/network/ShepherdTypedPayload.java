package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.miller.TypedCheck;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A line for her to hear. It goes to the server that runs the world and nowhere else. */
public record ShepherdTypedPayload(int conversation, String text) implements CustomPacketPayload {
    public static final Type<ShepherdTypedPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shepherd_typed"));
    public static final StreamCodec<ByteBuf, ShepherdTypedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShepherdTypedPayload::conversation, ByteBufCodecs.stringUtf8(TypedCheck.MAX_CHARS), ShepherdTypedPayload::text, ShepherdTypedPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
