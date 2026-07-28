package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A gaze acknowledgement or deliberate attack against the current Echo. */
public record HeartEchoActionPayload(int generation, boolean violent)
        implements CustomPacketPayload {
    public static final Type<HeartEchoActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "heart_echo_action"));

    public static final StreamCodec<ByteBuf, HeartEchoActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    HeartEchoActionPayload::generation,
                    ByteBufCodecs.BOOL,
                    HeartEchoActionPayload::violent,
                    HeartEchoActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
