package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A server-authoritative room transition, not a client oxygen estimate. */
public record RoomRecoveryPayload(Stage stage) implements CustomPacketPayload {
    public enum Stage { WAITING_FOR_OXYGEN, RESTORING_AIR, AIR_RESTORED }
    public static final Type<RoomRecoveryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "room_recovery"));
    public static final StreamCodec<ByteBuf, RoomRecoveryPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            value -> {
                if (value < 0 || value >= Stage.values().length) throw new IllegalArgumentException("Invalid recovery stage");
                return new RoomRecoveryPayload(Stage.values()[value]);
            }, value -> value.stage().ordinal());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
