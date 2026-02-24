package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client packet carrying the player's calculated temperature.
 * Sent every 40 ticks (~2 seconds) per player.
 */
public record TemperaturePayload(float temperature) implements CustomPacketPayload {

    public static final Type<TemperaturePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "temperature"));

    public static final StreamCodec<ByteBuf, TemperaturePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, TemperaturePayload::temperature,
            TemperaturePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
