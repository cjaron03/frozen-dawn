package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client packet carrying apocalypse state for rendering.
 * Sent every 100 ticks and on player join.
 */
public record ApocalypseDataPayload(
        int phase,
        float progress,
        float tempOffset,
        float sunScale,
        float sunBrightness,
        float skyLight,
        boolean schematicUnlocked
) implements CustomPacketPayload {

    public static final Type<ApocalypseDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "apocalypse_data"));

    public static final StreamCodec<ByteBuf, ApocalypseDataPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ApocalypseDataPayload decode(ByteBuf buf) {
            int phase = ByteBufCodecs.VAR_INT.decode(buf);
            float progress = ByteBufCodecs.FLOAT.decode(buf);
            float tempOffset = ByteBufCodecs.FLOAT.decode(buf);
            float sunScale = ByteBufCodecs.FLOAT.decode(buf);
            float sunBrightness = ByteBufCodecs.FLOAT.decode(buf);
            float skyLight = ByteBufCodecs.FLOAT.decode(buf);
            boolean schematicUnlocked = ByteBufCodecs.BOOL.decode(buf);
            return new ApocalypseDataPayload(phase, progress, tempOffset,
                    sunScale, sunBrightness, skyLight, schematicUnlocked);
        }

        @Override
        public void encode(ByteBuf buf, ApocalypseDataPayload payload) {
            ByteBufCodecs.VAR_INT.encode(buf, payload.phase());
            ByteBufCodecs.FLOAT.encode(buf, payload.progress());
            ByteBufCodecs.FLOAT.encode(buf, payload.tempOffset());
            ByteBufCodecs.FLOAT.encode(buf, payload.sunScale());
            ByteBufCodecs.FLOAT.encode(buf, payload.sunBrightness());
            ByteBufCodecs.FLOAT.encode(buf, payload.skyLight());
            ByteBufCodecs.BOOL.encode(buf, payload.schematicUnlocked());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
