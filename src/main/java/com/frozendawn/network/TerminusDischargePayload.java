package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A Terminus discharge, for the operator and everyone watching: who fired, and the entity id of each mind it stopped. */
public record TerminusDischargePayload(int operator, List<Integer> minds) implements CustomPacketPayload {
    /** The most minds one discharge shows; those past it are still stopped, just without a mote or a crumble. */
    public static final int MAX_MINDS = 64;
    public static final Type<TerminusDischargePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "terminus_discharge"));
    public static final StreamCodec<ByteBuf, TerminusDischargePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminusDischargePayload::operator,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_MINDS)), TerminusDischargePayload::minds, TerminusDischargePayload::new);

    public static TerminusDischargePayload of(int operator, List<Integer> minds) {
        return new TerminusDischargePayload(operator, List.copyOf(minds.subList(0, Math.min(MAX_MINDS, minds.size()))));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
