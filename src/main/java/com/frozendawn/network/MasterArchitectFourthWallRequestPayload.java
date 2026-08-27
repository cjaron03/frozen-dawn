package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests eligibility or completion for the client-local third-person moment. */
public record MasterArchitectFourthWallRequestPayload(
        int entityId, boolean complete) implements CustomPacketPayload {
    public static final Type<MasterArchitectFourthWallRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "master_architect_fourth_wall_request"));

    public static final StreamCodec<ByteBuf, MasterArchitectFourthWallRequestPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectFourthWallRequestPayload::entityId,
                    ByteBufCodecs.BOOL,
                    MasterArchitectFourthWallRequestPayload::complete,
                    MasterArchitectFourthWallRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
