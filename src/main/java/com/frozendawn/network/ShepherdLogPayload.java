package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.miller.DailyLog;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Her whole log, newest first: a few kilobytes at most, so paging needs no round trip. It carries lang keys, and no text. */
public record ShepherdLogPayload(int conversation, List<Page> pages) implements CustomPacketPayload {
    /** {@code note} is a lang key, or null for a blank. */
    public record Page(long day, boolean signed, boolean cracked, String note) {
        static final StreamCodec<FriendlyByteBuf, Page> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Page::day, ByteBufCodecs.BOOL, Page::signed, ByteBufCodecs.BOOL, Page::cracked,
                ByteBufCodecs.STRING_UTF8, page -> page.note == null ? "" : page.note, (day, signed, cracked, note) -> new Page(day, signed, cracked, note.isEmpty() ? null : note));
    }

    public static final Type<ShepherdLogPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shepherd_log"));
    public static final StreamCodec<FriendlyByteBuf, ShepherdLogPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShepherdLogPayload::conversation, Page.STREAM_CODEC.apply(ByteBufCodecs.list(DailyLog.MAX)), ShepherdLogPayload::pages, ShepherdLogPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
