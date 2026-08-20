package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One-shot visual/audio event emitted by the Master Architect aura controller. */
public record MasterArchitectAuraEventPayload(
        int eventType,
        BlockPos origin,
        BlockPos target,
        long seed,
        float intensity) implements CustomPacketPayload {
    public static final int BOLT = 1;
    public static final int ARC = 2;
    public static final int TETHER_SHUDDER = 3;
    public static final int FOLD_CONTRACTION = 4;
    public static final int EXPOSURE_STUTTER = 5;
    public static final int DEATH_COLLAPSE = 6;
    public static final int DEATH_PRESSURE_WAVE = 7;
    public static final int AGGREGATE_BOLT = 8;

    public static final Type<MasterArchitectAuraEventPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "master_architect_aura_event"));

    public static final StreamCodec<ByteBuf, MasterArchitectAuraEventPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectAuraEventPayload::eventType,
                    BlockPos.STREAM_CODEC,
                    MasterArchitectAuraEventPayload::origin,
                    BlockPos.STREAM_CODEC,
                    MasterArchitectAuraEventPayload::target,
                    ByteBufCodecs.VAR_LONG,
                    MasterArchitectAuraEventPayload::seed,
                    ByteBufCodecs.FLOAT,
                    MasterArchitectAuraEventPayload::intensity,
                    MasterArchitectAuraEventPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
