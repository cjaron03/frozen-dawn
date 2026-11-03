package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/** A Terminus discharge, for the operator and everyone watching: who fired, and where each stopped mind's head was. */
public record TerminusDischargePayload(int operator, List<Vector3f> minds) implements CustomPacketPayload {
    /** The most motes one discharge shows; the minds past it are still stopped, just without one. */
    public static final int MAX_MINDS = 64;
    public static final Type<TerminusDischargePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "terminus_discharge"));
    public static final StreamCodec<ByteBuf, TerminusDischargePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminusDischargePayload::operator,
            ByteBufCodecs.VECTOR3F.apply(ByteBufCodecs.list(MAX_MINDS)), TerminusDischargePayload::minds, TerminusDischargePayload::new);

    public static TerminusDischargePayload of(int operator, List<Vector3f> minds) {
        return new TerminusDischargePayload(operator, List.copyOf(minds.subList(0, Math.min(MAX_MINDS, minds.size()))));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
