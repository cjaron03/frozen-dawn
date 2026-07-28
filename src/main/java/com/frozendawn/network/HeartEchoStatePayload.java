package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-owned state and one-shot outcomes for a player's private Heart Echo. */
public record HeartEchoStatePayload(
        int generation,
        int state,
        double x,
        double y,
        double z,
        int nodeIndex,
        int exposureTicks,
        int clarityTicks) implements CustomPacketPayload {
    public static final int CLEAR = 0;
    public static final int ACTIVE = 1;
    public static final int ACKNOWLEDGED = 2;
    public static final int SCREAMED = 3;
    public static final int VIOLENTLY_DISMISSED = 4;
    public static final int NODE_HIT = 5;

    public static final Type<HeartEchoStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "heart_echo_state"));

    public static final StreamCodec<ByteBuf, HeartEchoStatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public HeartEchoStatePayload decode(ByteBuf buffer) {
                    return new HeartEchoStatePayload(
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer));
                }

                @Override
                public void encode(ByteBuf buffer, HeartEchoStatePayload payload) {
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.generation());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.state());
                    buffer.writeDouble(payload.x());
                    buffer.writeDouble(payload.y());
                    buffer.writeDouble(payload.z());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.nodeIndex());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.exposureTicks());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.clarityTicks());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
