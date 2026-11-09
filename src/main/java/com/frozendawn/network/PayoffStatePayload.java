package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The world's payoff flag and its Terminus turn, for the client. {@code markNew} is set once, when this player is first
 * granted the payoff pages, so the client can mark their entries unread.
 */
public record PayoffStatePayload(boolean fired, boolean terminusTurned, boolean markNew) implements CustomPacketPayload {
    public static final Type<PayoffStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "payoff_state"));
    public static final StreamCodec<ByteBuf, PayoffStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PayoffStatePayload::fired,
            ByteBufCodecs.BOOL, PayoffStatePayload::terminusTurned,
            ByteBufCodecs.BOOL, PayoffStatePayload::markNew,
            PayoffStatePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
