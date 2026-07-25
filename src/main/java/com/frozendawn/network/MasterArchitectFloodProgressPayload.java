package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Per-player throne-world charge plus shared core-exposure progress. */
public record MasterArchitectFloodProgressPayload(
        int stacks,
        int exposureCycle,
        boolean coreExposed,
        boolean deathRitual,
        int healingTier) implements CustomPacketPayload {

    public static final Type<MasterArchitectFloodProgressPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "master_architect_flood_progress"));

    public static final StreamCodec<ByteBuf, MasterArchitectFloodProgressPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectFloodProgressPayload::stacks,
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectFloodProgressPayload::exposureCycle,
                    ByteBufCodecs.BOOL,
                    MasterArchitectFloodProgressPayload::coreExposed,
                    ByteBufCodecs.BOOL,
                    MasterArchitectFloodProgressPayload::deathRitual,
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectFloodProgressPayload::healingTier,
                    MasterArchitectFloodProgressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
