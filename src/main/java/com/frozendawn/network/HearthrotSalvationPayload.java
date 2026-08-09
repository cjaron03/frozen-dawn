package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Silent one-shot payload; deliberately independent of transmission sessions. */
public record HearthrotSalvationPayload() implements CustomPacketPayload {
    public static final Type<HearthrotSalvationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "hearthrot_salvation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HearthrotSalvationPayload>
            STREAM_CODEC = StreamCodec.unit(new HearthrotSalvationPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
