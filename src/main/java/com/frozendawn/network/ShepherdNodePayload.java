package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/** One node of a conversation, already resolved for the swearing setting. The client never holds a tree. */
public record ShepherdNodePayload(int conversation, String node, List<Line> lines, List<Reply> replies, boolean canContinue, int logoTicks, int thinkTicks, boolean plain) implements CustomPacketPayload {
    private static final int MAX_LINES = 16, MAX_REPLIES = 4, MAX_ARGS = 4;

    /** {@code args} are already worked out, as text for the line's slots. */
    public record Line(String key, Mood mood, List<String> args) {
        static final StreamCodec<FriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Line::key, NeoForgeStreamCodecs.enumCodec(Mood.class), Line::mood,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_ARGS)), Line::args, Line::new);
    }

    public record Reply(int slot, String label, String full) {
        static final StreamCodec<FriendlyByteBuf, Reply> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Reply::slot, ByteBufCodecs.STRING_UTF8, Reply::label, ByteBufCodecs.STRING_UTF8, Reply::full, Reply::new);
    }

    public static final Type<ShepherdNodePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shepherd_node"));
    static final StreamCodec<FriendlyByteBuf, List<Line>> LINES = Line.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES));
    private static final StreamCodec<FriendlyByteBuf, List<Reply>> REPLIES = Reply.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_REPLIES));
    /** Written out, because {@code composite} stops at six fields. */
    public static final StreamCodec<FriendlyByteBuf, ShepherdNodePayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
        ByteBufCodecs.VAR_INT.encode(buffer, payload.conversation);
        ByteBufCodecs.STRING_UTF8.encode(buffer, payload.node);
        LINES.encode(buffer, payload.lines);
        REPLIES.encode(buffer, payload.replies);
        ByteBufCodecs.BOOL.encode(buffer, payload.canContinue);
        ByteBufCodecs.VAR_INT.encode(buffer, payload.logoTicks);
        ByteBufCodecs.VAR_INT.encode(buffer, payload.thinkTicks);
        ByteBufCodecs.BOOL.encode(buffer, payload.plain);
    }, buffer -> new ShepherdNodePayload(ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.STRING_UTF8.decode(buffer), LINES.decode(buffer),
            REPLIES.decode(buffer), ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.BOOL.decode(buffer)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
