package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** An event's seed and clock; clients animate locally. Preview uses game time. */
public record AuroraStatePayload(long seed, long startTick, int durationTicks,
                                 float strength, boolean preview) implements CustomPacketPayload {
    public static final AuroraStatePayload NONE = new AuroraStatePayload(0, 0, 0, 0, false);
    public static final Type<AuroraStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "aurora_state"));
    public static final StreamCodec<ByteBuf, AuroraStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, AuroraStatePayload::seed,
            ByteBufCodecs.VAR_LONG, AuroraStatePayload::startTick,
            ByteBufCodecs.VAR_INT, AuroraStatePayload::durationTicks,
            ByteBufCodecs.FLOAT, AuroraStatePayload::strength,
            ByteBufCodecs.BOOL, AuroraStatePayload::preview, AuroraStatePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
