package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ThaevenTransmissionResultPayload(int sessionId, boolean completed)
        implements CustomPacketPayload {
    public static final Type<ThaevenTransmissionResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "thaeven_transmission_result"));

    public static final StreamCodec<ByteBuf, ThaevenTransmissionResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ThaevenTransmissionResultPayload::sessionId,
                    ByteBufCodecs.BOOL, ThaevenTransmissionResultPayload::completed,
                    ThaevenTransmissionResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
