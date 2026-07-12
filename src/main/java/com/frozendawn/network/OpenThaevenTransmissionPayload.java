package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenThaevenTransmissionPayload(
        int sessionId,
        int sourceEntityId,
        int transmissionType,
        int durationTicks) implements CustomPacketPayload {

    public static final Type<OpenThaevenTransmissionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "open_thaeven_transmission"));

    public static final StreamCodec<ByteBuf, OpenThaevenTransmissionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, OpenThaevenTransmissionPayload::sessionId,
                    ByteBufCodecs.VAR_INT, OpenThaevenTransmissionPayload::sourceEntityId,
                    ByteBufCodecs.VAR_INT, OpenThaevenTransmissionPayload::transmissionType,
                    ByteBufCodecs.VAR_INT, OpenThaevenTransmissionPayload::durationTicks,
                    OpenThaevenTransmissionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
