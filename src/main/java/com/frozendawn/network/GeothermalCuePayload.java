package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GeothermalCuePayload(String soundId, float volume, float pitch) implements CustomPacketPayload {

    public static final Type<GeothermalCuePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "geothermal_cue"));

    public static final StreamCodec<ByteBuf, GeothermalCuePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, GeothermalCuePayload::soundId,
            ByteBufCodecs.FLOAT, GeothermalCuePayload::volume,
            ByteBufCodecs.FLOAT, GeothermalCuePayload::pitch,
            GeothermalCuePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
