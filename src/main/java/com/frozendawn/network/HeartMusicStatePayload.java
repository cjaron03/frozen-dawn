package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Persistent server authority for the Heart-owned score and erased-world quiet. */
public record HeartMusicStatePayload(boolean active, boolean erased)
        implements CustomPacketPayload {
    public static final Type<HeartMusicStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "heart_music_state"));
    public static final StreamCodec<ByteBuf, HeartMusicStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    HeartMusicStatePayload::active,
                    ByteBufCodecs.BOOL,
                    HeartMusicStatePayload::erased,
                    HeartMusicStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
