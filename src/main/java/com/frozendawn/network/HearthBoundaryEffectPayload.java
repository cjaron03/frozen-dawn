package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Starts a client-local Hearth warning or permanent Orsathae response. */
public record HearthBoundaryEffectPayload(int effectType)
        implements CustomPacketPayload {
    public static final int WARNING = 0;
    public static final int ORSATHAE = 1;
    public static final int MAEVE_BREAK = 2;
    public static final int LAST_WITNESS_RESCUE = 3;

    public static final Type<HearthBoundaryEffectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "hearth_boundary_effect"));

    public static final StreamCodec<ByteBuf, HearthBoundaryEffectPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    HearthBoundaryEffectPayload::effectType,
                    HearthBoundaryEffectPayload::new);

    public static HearthBoundaryEffectPayload warning() {
        return new HearthBoundaryEffectPayload(WARNING);
    }

    public static HearthBoundaryEffectPayload orsathae() {
        return new HearthBoundaryEffectPayload(ORSATHAE);
    }

    public static HearthBoundaryEffectPayload maeveBreak() {
        return new HearthBoundaryEffectPayload(MAEVE_BREAK);
    }

    public static HearthBoundaryEffectPayload lastWitnessRescue() {
        return new HearthBoundaryEffectPayload(LAST_WITNESS_RESCUE);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
