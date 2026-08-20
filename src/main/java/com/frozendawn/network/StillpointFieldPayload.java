package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client mirror of the world-authoritative Stillpoint field and its latest ripple. */
public record StillpointFieldPayload(
        boolean present,
        boolean active,
        ResourceLocation dimension,
        BlockPos center,
        int radius,
        long chargeStartGameTime,
        int pulseSequence,
        double pulseX,
        double pulseY,
        double pulseZ,
        float pulseStrength) implements CustomPacketPayload {

    public static final Type<StillpointFieldPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "stillpoint_field"));

    public static final StreamCodec<ByteBuf, StillpointFieldPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                ByteBufCodecs.BOOL.encode(buffer, payload.present());
                ByteBufCodecs.BOOL.encode(buffer, payload.active());
                ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimension());
                BlockPos.STREAM_CODEC.encode(buffer, payload.center());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.radius());
                ByteBufCodecs.VAR_LONG.encode(buffer, payload.chargeStartGameTime());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.pulseSequence());
                buffer.writeDouble(payload.pulseX());
                buffer.writeDouble(payload.pulseY());
                buffer.writeDouble(payload.pulseZ());
                buffer.writeFloat(payload.pulseStrength());
            }, buffer -> new StillpointFieldPayload(
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ResourceLocation.STREAM_CODEC.decode(buffer),
                    BlockPos.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
