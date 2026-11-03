package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Once a second: dose rate for the Geiger counter, dose and felt stage. */
public record RadiationPayload(float rate, float dose, byte stage, float contamination) implements CustomPacketPayload {
    public static final Type<RadiationPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "radiation"));
    public static final StreamCodec<ByteBuf, RadiationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, RadiationPayload::rate, ByteBufCodecs.FLOAT, RadiationPayload::dose,
            ByteBufCodecs.BYTE, RadiationPayload::stage, ByteBufCodecs.FLOAT, RadiationPayload::contamination, RadiationPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
