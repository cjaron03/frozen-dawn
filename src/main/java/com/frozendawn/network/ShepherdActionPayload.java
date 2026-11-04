package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.shepherd.ShepherdTalk.Action;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

public record ShepherdActionPayload(int conversation, Action action) implements CustomPacketPayload {
    public static final Type<ShepherdActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shepherd_action"));
    public static final StreamCodec<FriendlyByteBuf, ShepherdActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShepherdActionPayload::conversation, NeoForgeStreamCodecs.enumCodec(Action.class), ShepherdActionPayload::action, ShepherdActionPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
