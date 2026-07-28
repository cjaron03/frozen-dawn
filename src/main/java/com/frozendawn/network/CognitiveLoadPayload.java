package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Per-player Heart pressure plus one-shot cognitive event. */
public record CognitiveLoadPayload(
        float load,
        long heartAnchor,
        boolean heartLive,
        boolean terminalTakeover,
        float breakoutProgress,
        int eventId) implements CustomPacketPayload {

    public static final int EVENT_NONE = 0;
    public static final int EVENT_MICRO_LAPSE = 1;
    public static final int EVENT_TAKEOVER_START = 2;
    public static final int EVENT_TAKEOVER_END = 3;

    public static final Type<CognitiveLoadPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "cognitive_load"));

    public static final StreamCodec<ByteBuf, CognitiveLoadPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT,
                    CognitiveLoadPayload::load,
                    ByteBufCodecs.VAR_LONG,
                    CognitiveLoadPayload::heartAnchor,
                    ByteBufCodecs.BOOL,
                    CognitiveLoadPayload::heartLive,
                    ByteBufCodecs.BOOL,
                    CognitiveLoadPayload::terminalTakeover,
                    ByteBufCodecs.FLOAT,
                    CognitiveLoadPayload::breakoutProgress,
                    ByteBufCodecs.VAR_INT,
                    CognitiveLoadPayload::eventId,
                    CognitiveLoadPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
