package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A meltdown's prompt excursion: a white flash for nearby players, fading with distance. */
public record ReactorFlashPayload(double x, double y, double z) implements CustomPacketPayload {
    public static final Type<ReactorFlashPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "reactor_flash"));
    public static final StreamCodec<ByteBuf, ReactorFlashPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, ReactorFlashPayload::x, ByteBufCodecs.DOUBLE, ReactorFlashPayload::y, ByteBufCodecs.DOUBLE, ReactorFlashPayload::z, ReactorFlashPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
