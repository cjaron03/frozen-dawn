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
public record ShepherdNodePayload(int conversation, String node, List<Line> lines, List<Reply> replies, boolean canContinue) implements CustomPacketPayload {
    private static final int MAX_LINES = 16, MAX_REPLIES = 4;

    public record Line(String key, Mood mood) {
        static final StreamCodec<FriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Line::key, NeoForgeStreamCodecs.enumCodec(Mood.class), Line::mood, Line::new);
    }

    public record Reply(int slot, String label, String full) {
        static final StreamCodec<FriendlyByteBuf, Reply> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Reply::slot, ByteBufCodecs.STRING_UTF8, Reply::label, ByteBufCodecs.STRING_UTF8, Reply::full, Reply::new);
    }

    public static final Type<ShepherdNodePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shepherd_node"));
    public static final StreamCodec<FriendlyByteBuf, ShepherdNodePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShepherdNodePayload::conversation,
            ByteBufCodecs.STRING_UTF8, ShepherdNodePayload::node,
            Line.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)), ShepherdNodePayload::lines,
            Reply.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_REPLIES)), ShepherdNodePayload::replies,
            ByteBufCodecs.BOOL, ShepherdNodePayload::canContinue,
            ShepherdNodePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
