package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client strength for the local storm surrounding a living Master Architect.
 */
public record MasterArchitectWeatherPayload(float strength) implements CustomPacketPayload {

    public static final Type<MasterArchitectWeatherPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "master_architect_weather"));

    public static final StreamCodec<ByteBuf, MasterArchitectWeatherPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT,
                    MasterArchitectWeatherPayload::strength,
                    MasterArchitectWeatherPayload::new);

    public static MasterArchitectWeatherPayload inactive() {
        return new MasterArchitectWeatherPayload(0.0F);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
