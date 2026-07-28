package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Fresh client movement intent used by the server-owned Heart breakout. */
public record CognitiveResistancePayload(float resistance)
        implements CustomPacketPayload {
    public static final Type<CognitiveResistancePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "cognitive_resistance"));
    public static final StreamCodec<ByteBuf, CognitiveResistancePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT,
                    CognitiveResistancePayload::resistance,
                    CognitiveResistancePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
