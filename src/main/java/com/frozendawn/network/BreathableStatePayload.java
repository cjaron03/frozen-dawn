package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client packet carrying whether the player is currently in breathable
 * late-phase air according to the authoritative server check.
 */
public record BreathableStatePayload(boolean breathable) implements CustomPacketPayload {

    public static final Type<BreathableStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "breathable_state"));

    public static final StreamCodec<ByteBuf, BreathableStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BreathableStatePayload::breathable,
            BreathableStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
