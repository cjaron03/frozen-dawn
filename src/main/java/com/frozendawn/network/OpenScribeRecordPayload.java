package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.item.ScribeRecordContents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The server decides whether a translator is carried; the client only renders the frozen notes. */
public record OpenScribeRecordPayload(ScribeRecordContents contents, boolean translated)
        implements CustomPacketPayload {
    public static final Type<OpenScribeRecordPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "open_scribe_record"));
    public static final StreamCodec<ByteBuf, OpenScribeRecordPayload> STREAM_CODEC = StreamCodec.composite(
            ScribeRecordContents.STREAM_CODEC, OpenScribeRecordPayload::contents,
            ByteBufCodecs.BOOL, OpenScribeRecordPayload::translated,
            OpenScribeRecordPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
