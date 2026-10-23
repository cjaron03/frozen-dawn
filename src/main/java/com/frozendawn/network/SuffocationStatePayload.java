package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.SuffocationStage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative danger state, replacing competing server action-bar text. */
public record SuffocationStatePayload(SuffocationStage stage) implements CustomPacketPayload {
    public static final Type<SuffocationStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "suffocation_state"));
    public static final StreamCodec<ByteBuf, SuffocationStatePayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            value -> {
                if (value < 0 || value >= SuffocationStage.values().length)
                    throw new IllegalArgumentException("Invalid suffocation stage");
                return new SuffocationStatePayload(SuffocationStage.values()[value]);
            }, value -> value.stage().ordinal());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
