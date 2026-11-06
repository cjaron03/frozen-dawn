package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Her hub. {@code waiting} means she is not following this player, so the button offers Follow, not Wait here. {@code lines} play over it first, after {@code logoTicks}, and the buttons wait for them. */
public record ShepherdHubPayload(int conversation, boolean waiting, boolean lamp, boolean heavy, List<ShepherdNodePayload.Line> lines, int logoTicks) implements CustomPacketPayload {
    public static final Type<ShepherdHubPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shepherd_hub"));
    public static final StreamCodec<FriendlyByteBuf, ShepherdHubPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShepherdHubPayload::conversation,
            ByteBufCodecs.BOOL, ShepherdHubPayload::waiting,
            ByteBufCodecs.BOOL, ShepherdHubPayload::lamp,
            ByteBufCodecs.BOOL, ShepherdHubPayload::heavy,
            ShepherdNodePayload.LINES, ShepherdHubPayload::lines,
            ByteBufCodecs.VAR_INT, ShepherdHubPayload::logoTicks,
            ShepherdHubPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
