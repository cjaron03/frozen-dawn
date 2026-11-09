package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One of CH-37's loop beats, by ordinal, for the overlay of the player who triggered it. */
public record ThaevenLoopPayload(int beat) implements CustomPacketPayload {
    public static final Type<ThaevenLoopPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "thaeven_loop"));
    public static final StreamCodec<ByteBuf, ThaevenLoopPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ThaevenLoopPayload::new, ThaevenLoopPayload::beat);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
