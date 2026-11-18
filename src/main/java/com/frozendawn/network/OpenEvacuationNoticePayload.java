package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Opens the Evacuation Notice; its words are lang keys, so nothing travels with it. */
public record OpenEvacuationNoticePayload() implements CustomPacketPayload {
    public static final OpenEvacuationNoticePayload INSTANCE = new OpenEvacuationNoticePayload();
    public static final Type<OpenEvacuationNoticePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "open_evacuation_notice"));
    public static final StreamCodec<ByteBuf, OpenEvacuationNoticePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
