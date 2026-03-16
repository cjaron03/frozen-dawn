package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server packet sent when a player confirms the world difficulty preset.
 */
public record SelectDifficultyPresetPayload(String presetName) implements CustomPacketPayload {

    public static final Type<SelectDifficultyPresetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "select_difficulty_preset"));

    public static final StreamCodec<ByteBuf, SelectDifficultyPresetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            SelectDifficultyPresetPayload::presetName,
            SelectDifficultyPresetPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
