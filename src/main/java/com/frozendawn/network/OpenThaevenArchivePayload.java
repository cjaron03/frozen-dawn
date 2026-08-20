package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenThaevenArchivePayload(int focusRecord, boolean rawOnly)
        implements CustomPacketPayload {
    public static final Type<OpenThaevenArchivePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "open_thaeven_archive"));
    public static final StreamCodec<ByteBuf, OpenThaevenArchivePayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    OpenThaevenArchivePayload::focusRecord,
                    ByteBufCodecs.BOOL,
                    OpenThaevenArchivePayload::rawOnly,
                    OpenThaevenArchivePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
