package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The black screen with Boot and Leave. Whose dock it is stays on the server. */
public record ShepherdBootOfferPayload() implements CustomPacketPayload {
    public static final Type<ShepherdBootOfferPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shepherd_boot_offer"));
    public static final StreamCodec<ByteBuf, ShepherdBootOfferPayload> STREAM_CODEC = StreamCodec.unit(new ShepherdBootOfferPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
