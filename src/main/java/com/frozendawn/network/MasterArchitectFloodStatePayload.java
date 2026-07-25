package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Per-player state for the Master Architect's final Flood channel. */
public record MasterArchitectFloodStatePayload(
        int entityId,
        int operation,
        float floodStrength,
        float proximity,
        float immersion)
        implements CustomPacketPayload {
    public static final int CLEAR = 0;
    public static final int ACTIVE = 1;
    public static final int COMPLETE_RECEIVED = 2;
    public static final int COMPLETE_REFUSED = 3;

    public static final Type<MasterArchitectFloodStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "master_architect_flood_state"));

    public static final StreamCodec<ByteBuf, MasterArchitectFloodStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectFloodStatePayload::entityId,
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectFloodStatePayload::operation,
                    ByteBufCodecs.FLOAT,
                    MasterArchitectFloodStatePayload::floodStrength,
                    ByteBufCodecs.FLOAT,
                    MasterArchitectFloodStatePayload::proximity,
                    ByteBufCodecs.FLOAT,
                    MasterArchitectFloodStatePayload::immersion,
                    MasterArchitectFloodStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
