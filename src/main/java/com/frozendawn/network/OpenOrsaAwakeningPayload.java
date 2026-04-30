package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenOrsaAwakeningPayload() implements CustomPacketPayload {
    public static final Type<OpenOrsaAwakeningPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "open_orsa_awakening"));

    public static final StreamCodec<ByteBuf, OpenOrsaAwakeningPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenOrsaAwakeningPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
