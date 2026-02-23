package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client packet carrying apocalypse state for rendering.
 * Sent every 100 ticks and on player join.
 */
public record ApocalypseDataPayload(
        int phase,
        float progress,
        float tempOffset,
        float sunScale,
        float sunBrightness,
        float skyLight
) implements CustomPacketPayload {

    public static final Type<ApocalypseDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "apocalypse_data"));

    public static final StreamCodec<ByteBuf, ApocalypseDataPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ApocalypseDataPayload::phase,
            ByteBufCodecs.FLOAT, ApocalypseDataPayload::progress,
            ByteBufCodecs.FLOAT, ApocalypseDataPayload::tempOffset,
            ByteBufCodecs.FLOAT, ApocalypseDataPayload::sunScale,
            ByteBufCodecs.FLOAT, ApocalypseDataPayload::sunBrightness,
            ByteBufCodecs.FLOAT, ApocalypseDataPayload::skyLight,
            ApocalypseDataPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
