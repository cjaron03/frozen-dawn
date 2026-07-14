package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HearthSurveyAudioPayload(
        boolean active,
        float proximity
) implements CustomPacketPayload {
    public static final Type<HearthSurveyAudioPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "hearth_survey_audio"));

    public static final StreamCodec<ByteBuf, HearthSurveyAudioPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HearthSurveyAudioPayload decode(ByteBuf buffer) {
            return new HearthSurveyAudioPayload(
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer)
            );
        }

        @Override
        public void encode(ByteBuf buffer, HearthSurveyAudioPayload payload) {
            ByteBufCodecs.BOOL.encode(buffer, payload.active());
            ByteBufCodecs.FLOAT.encode(buffer, payload.proximity());
        }
    };

    public static HearthSurveyAudioPayload inactive() {
        return new HearthSurveyAudioPayload(false, 0.0F);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
