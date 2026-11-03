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
    public static final Type<TerminusDischargePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "terminus_discharge"));
    public static final StreamCodec<ByteBuf, TerminusDischargePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminusDischargePayload::operator,
            ByteBufCodecs.VECTOR3F.apply(ByteBufCodecs.list(64)), TerminusDischargePayload::minds, TerminusDischargePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
