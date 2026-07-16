package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Confirms whether this player may see, already saw, or just triggered the moment. */
public record MasterArchitectFourthWallStatePayload(
        int entityId, int state) implements CustomPacketPayload {
    public static final int ELIGIBLE = 0;
    public static final int COMPLETED = 1;
    public static final int TRIGGERED = 2;

    public static final Type<MasterArchitectFourthWallStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "master_architect_fourth_wall_state"));

    public static final StreamCodec<ByteBuf, MasterArchitectFourthWallStatePayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectFourthWallStatePayload::entityId,
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectFourthWallStatePayload::state,
                    MasterArchitectFourthWallStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
