package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Change-driven server mirror for EVA puncture HUD and suit feedback. */
public record SuitIntegrityPayload(
        int punctures,
        int o2Ticks,
        int maxO2Ticks,
        int patchTicks,
        int patchDurationTicks,
        int eventId) implements CustomPacketPayload {

    public static final int NONE = 0;
    public static final int PUNCTURED = 1;
    public static final int OXYGEN_CRITICAL = 2;
    public static final int PATCHED = 3;
    public static final int PATCH_DEGRADED = 4;

    public static final Type<SuitIntegrityPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "suit_integrity"));

    public static final StreamCodec<ByteBuf, SuitIntegrityPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SuitIntegrityPayload::punctures,
                    ByteBufCodecs.VAR_INT, SuitIntegrityPayload::o2Ticks,
                    ByteBufCodecs.VAR_INT, SuitIntegrityPayload::maxO2Ticks,
                    ByteBufCodecs.VAR_INT, SuitIntegrityPayload::patchTicks,
                    ByteBufCodecs.VAR_INT, SuitIntegrityPayload::patchDurationTicks,
                    ByteBufCodecs.VAR_INT, SuitIntegrityPayload::eventId,
                    SuitIntegrityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
