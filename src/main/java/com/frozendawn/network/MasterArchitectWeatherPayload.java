package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client strength for the local storm surrounding a living Master Architect.
 */
public record MasterArchitectWeatherPayload(
        float strength,
        int auraTier,
        BlockPos hearthCenter,
        boolean anchored) implements CustomPacketPayload {

    public static final Type<MasterArchitectWeatherPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "master_architect_weather"));

    public static final StreamCodec<ByteBuf, MasterArchitectWeatherPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT,
                    MasterArchitectWeatherPayload::strength,
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectWeatherPayload::auraTier,
                    BlockPos.STREAM_CODEC,
                    MasterArchitectWeatherPayload::hearthCenter,
                    ByteBufCodecs.BOOL,
                    MasterArchitectWeatherPayload::anchored,
                    MasterArchitectWeatherPayload::new);

    public static MasterArchitectWeatherPayload inactive() {
        return new MasterArchitectWeatherPayload(0.0F, 0, BlockPos.ZERO, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
