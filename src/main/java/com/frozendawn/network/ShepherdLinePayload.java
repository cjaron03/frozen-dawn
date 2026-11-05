package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/** One thing she says during play: a lang key for the strip, the face that goes with it, and what fills its slot, which may be null. */
public record ShepherdLinePayload(String key, Mood mood, String arg) implements CustomPacketPayload {
    public static final Type<ShepherdLinePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shepherd_line"));
    public static final StreamCodec<FriendlyByteBuf, ShepherdLinePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ShepherdLinePayload::key, NeoForgeStreamCodecs.enumCodec(Mood.class), ShepherdLinePayload::mood,
            ByteBufCodecs.STRING_UTF8, line -> line.arg == null ? "" : line.arg, (key, mood, arg) -> new ShepherdLinePayload(key, mood, arg.isEmpty() ? null : arg));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
