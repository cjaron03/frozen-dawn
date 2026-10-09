package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Local saved coordinates, never live information about the state of the destination. */
public record ContinuityRecoveryPayload(CompoundTag record) implements CustomPacketPayload {
    public static final Type<ContinuityRecoveryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "continuity_recovery"));
    public static final StreamCodec<ByteBuf, ContinuityRecoveryPayload> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(ContinuityRecoveryPayload::new, ContinuityRecoveryPayload::record);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
