package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Selects between already recorded targets; cannot upload positions or change the reserve. */
public record ContinuityTargetPayload(UUID issue, boolean shelter) implements CustomPacketPayload {
    public static final Type<ContinuityTargetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "continuity_target"));
    public static final StreamCodec<ByteBuf, ContinuityTargetPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ContinuityTargetPayload::issue,
            ByteBufCodecs.BOOL, ContinuityTargetPayload::shelter, ContinuityTargetPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
