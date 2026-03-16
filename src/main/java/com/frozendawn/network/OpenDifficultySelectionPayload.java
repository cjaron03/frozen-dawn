package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client packet that opens the one-time difficulty selection screen.
 */
public record OpenDifficultySelectionPayload() implements CustomPacketPayload {

    public static final Type<OpenDifficultySelectionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "open_difficulty_selection"));

    public static final StreamCodec<ByteBuf, OpenDifficultySelectionPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenDifficultySelectionPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
