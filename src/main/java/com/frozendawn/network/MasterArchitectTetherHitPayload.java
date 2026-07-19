package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client feedback state for one Master Architect tether-routed hit. */
public record MasterArchitectTetherHitPayload(int entityId, int feedbackStateId)
        implements CustomPacketPayload {

    public static final Type<MasterArchitectTetherHitPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "master_architect_tether_hit"));

    public static final StreamCodec<ByteBuf, MasterArchitectTetherHitPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectTetherHitPayload::entityId,
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectTetherHitPayload::feedbackStateId,
                    MasterArchitectTetherHitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
