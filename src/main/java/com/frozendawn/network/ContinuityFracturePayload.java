package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Rotates movement input without changing camera or action controls. */
public record ContinuityFracturePayload(
        int durationTicks, int quarterTurns) implements CustomPacketPayload {

    public static final Type<ContinuityFracturePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "continuity_fracture"));

    public static final StreamCodec<ByteBuf, ContinuityFracturePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    ContinuityFracturePayload::durationTicks,
                    ByteBufCodecs.VAR_INT,
                    ContinuityFracturePayload::quarterTurns,
                    ContinuityFracturePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
