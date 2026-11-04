package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A slot number and nothing more, or -1 to continue: the server decides what that means. */
public record ShepherdReplyPayload(int conversation, int slot) implements CustomPacketPayload {
    public static final Type<ShepherdReplyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shepherd_reply"));
    public static final StreamCodec<ByteBuf, ShepherdReplyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShepherdReplyPayload::conversation, ByteBufCodecs.VAR_INT, ShepherdReplyPayload::slot, ShepherdReplyPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
