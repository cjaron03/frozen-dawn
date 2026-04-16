package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LaunchSequencePayload(int entityId, BlockPos padCenter, int countdownTicks, int ascentTicks)
        implements CustomPacketPayload {

    public static final Type<LaunchSequencePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "launch_sequence"));

    public static final StreamCodec<ByteBuf, LaunchSequencePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LaunchSequencePayload::entityId,
            BlockPos.STREAM_CODEC, LaunchSequencePayload::padCenter,
            ByteBufCodecs.VAR_INT, LaunchSequencePayload::countdownTicks,
            ByteBufCodecs.VAR_INT, LaunchSequencePayload::ascentTicks,
            LaunchSequencePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
