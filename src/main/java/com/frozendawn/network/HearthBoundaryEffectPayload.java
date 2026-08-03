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
    public static final int MAEVE_DEATH = 4;
    public static final int WORLD_EVENT_SILENCE = 5;
    public static final int WORLD_EVENT_OMEN = 6;
    public static final int WORLD_EVENT_COLLAPSE_RESPONSE = 7;
    public static final int WORLD_EVENT_BIOLOGICAL_WARNING = 8;
    public static final int UNDONE_CONTACT = 9;

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

    public static HearthBoundaryEffectPayload maeveDeath() {
        return new HearthBoundaryEffectPayload(MAEVE_DEATH);
    }

    public static HearthBoundaryEffectPayload worldEventSilence() {
        return new HearthBoundaryEffectPayload(WORLD_EVENT_SILENCE);
    }

    public static HearthBoundaryEffectPayload worldEventOmen() {
        return new HearthBoundaryEffectPayload(WORLD_EVENT_OMEN);
    }

    public static HearthBoundaryEffectPayload worldEventCollapseResponse() {
        return new HearthBoundaryEffectPayload(WORLD_EVENT_COLLAPSE_RESPONSE);
    }

    public static HearthBoundaryEffectPayload worldEventBiologicalWarning() {
        return new HearthBoundaryEffectPayload(WORLD_EVENT_BIOLOGICAL_WARNING);
    }

    public static HearthBoundaryEffectPayload undoneContact() {
        return new HearthBoundaryEffectPayload(UNDONE_CONTACT);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
