package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Boot, or Leave. */
public record ShepherdBootAnswerPayload(boolean boot) implements CustomPacketPayload {
    public static final Type<ShepherdBootAnswerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shepherd_boot_answer"));
    public static final StreamCodec<ByteBuf, ShepherdBootAnswerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ShepherdBootAnswerPayload::boot, ShepherdBootAnswerPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
