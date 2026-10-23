package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Read-only, server-authoritative targeting; no client request or link mutation. */
public record PowerBinderPayload(String dimension, @Nullable BlockPos source, @Nullable BlockPos target,
        Status status, int reach, double distance, int nodes, int links,
        double supplyKw, double demandKw, double storedKj) implements CustomPacketPayload {
    public enum Status { CHOOSE, TARGET, READY, ALREADY_LINKED, CANCEL, RANGE, FEEDTHROUGH, WAITING, UNAVAILABLE, LIMIT, DOCK_CONNECTION, COOLANT }
    public PowerBinderPayload {
        if (source != null) source = source.immutable();
        if (target != null) target = target.immutable();
    }
    public static final Type<PowerBinderPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "power_binder"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PowerBinderPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public PowerBinderPayload decode(RegistryFriendlyByteBuf buffer) {
            String dimension = buffer.readUtf(256);
            BlockPos source = buffer.readBoolean() ? buffer.readBlockPos() : null;
            BlockPos target = buffer.readBoolean() ? buffer.readBlockPos() : null;
            return new PowerBinderPayload(dimension, source, target, buffer.readEnum(Status.class),
                    buffer.readVarInt(), buffer.readDouble(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, PowerBinderPayload payload) {
            buffer.writeUtf(payload.dimension(), 256);
            buffer.writeBoolean(payload.source() != null); if (payload.source() != null) buffer.writeBlockPos(payload.source());
            buffer.writeBoolean(payload.target() != null); if (payload.target() != null) buffer.writeBlockPos(payload.target());
            buffer.writeEnum(payload.status()); buffer.writeVarInt(payload.reach()); buffer.writeDouble(payload.distance());
            buffer.writeVarInt(payload.nodes()); buffer.writeVarInt(payload.links()); buffer.writeDouble(payload.supplyKw());
            buffer.writeDouble(payload.demandKw()); buffer.writeDouble(payload.storedKj());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
