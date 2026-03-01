package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server packet sent when the player first looks at The Watcher.
 * Triggers a hidden advancement.
 */
public record WatcherSeenPayload() implements CustomPacketPayload {

    public static final Type<WatcherSeenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "watcher_seen"));

    public static final StreamCodec<ByteBuf, WatcherSeenPayload> STREAM_CODEC =
            StreamCodec.unit(new WatcherSeenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
