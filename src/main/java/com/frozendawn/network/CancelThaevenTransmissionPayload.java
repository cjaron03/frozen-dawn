package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CancelThaevenTransmissionPayload(int sessionId) implements CustomPacketPayload {
    public static final Type<CancelThaevenTransmissionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "cancel_thaeven_transmission"));

    public static final StreamCodec<ByteBuf, CancelThaevenTransmissionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CancelThaevenTransmissionPayload::sessionId,
                    CancelThaevenTransmissionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
