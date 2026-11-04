package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Her hub. {@code waiting} means she is not following this player, so the button offers Follow, not Wait here. */
public record ShepherdHubPayload(int conversation, boolean waiting, boolean lamp, boolean heavy) implements CustomPacketPayload {
    public static final Type<ShepherdHubPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shepherd_hub"));
    public static final StreamCodec<ByteBuf, ShepherdHubPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShepherdHubPayload::conversation,
            ByteBufCodecs.BOOL, ShepherdHubPayload::waiting,
            ByteBufCodecs.BOOL, ShepherdHubPayload::lamp,
            ByteBufCodecs.BOOL, ShepherdHubPayload::heavy,
            ShepherdHubPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
