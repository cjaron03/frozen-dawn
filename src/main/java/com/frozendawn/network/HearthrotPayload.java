package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Synchronized Hearthrot state plus sparse one-shot presentation events. */
public record HearthrotPayload(
        int stage,
        float progress,
        int colonization,
        int eventId) implements CustomPacketPayload {
    public static final int NONE = 0;
    public static final int INFECTED = 1;
    public static final int CONTAMINATION_WARNING = 2;
    public static final int STAGE_ADVANCED = 3;
    public static final int COUGH = 4;
    public static final int DEATH_ROLLBACK = 5;
    public static final int WHEEZE = 6;
    public static final int BREATH_CATCH = 7;

    public static final Type<HearthrotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "hearthrot"));

    public static final StreamCodec<ByteBuf, HearthrotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, HearthrotPayload::stage,
                    ByteBufCodecs.FLOAT, HearthrotPayload::progress,
                    ByteBufCodecs.VAR_INT, HearthrotPayload::colonization,
                    ByteBufCodecs.VAR_INT, HearthrotPayload::eventId,
                    HearthrotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
