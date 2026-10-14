package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Short feedback inside the Binder HUD, replacing ordinary chat output. */
public record PowerBinderNoticePayload(String key, int count) implements CustomPacketPayload {
    public static final Type<PowerBinderNoticePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "power_binder_notice"));
    public static final StreamCodec<ByteBuf, PowerBinderNoticePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), PowerBinderNoticePayload::key,
            ByteBufCodecs.VAR_INT, PowerBinderNoticePayload::count, PowerBinderNoticePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
