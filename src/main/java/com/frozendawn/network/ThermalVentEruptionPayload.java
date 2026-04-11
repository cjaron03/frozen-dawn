package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ThermalVentEruptionPayload(BlockPos pos, float strength, int durationTicks)
        implements CustomPacketPayload {

    public static final Type<ThermalVentEruptionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "thermal_vent_eruption"));

    public static final StreamCodec<ByteBuf, ThermalVentEruptionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ThermalVentEruptionPayload::pos,
            ByteBufCodecs.FLOAT, ThermalVentEruptionPayload::strength,
            ByteBufCodecs.VAR_INT, ThermalVentEruptionPayload::durationTicks,
            ThermalVentEruptionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
