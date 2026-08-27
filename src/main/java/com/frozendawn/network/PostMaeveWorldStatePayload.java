package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** World-scoped post-Maeve authority mirrored to each client. */
public record PostMaeveWorldStatePayload(
        boolean maeveErased,
        boolean undoneSpawningReleased,
        long moonriseStartDayTime,
        long moonElapsedDayTicks,
        long moonSyncDayTime,
        long moonVisualSeed,
        boolean moonriseStarted) implements CustomPacketPayload {

    public static final Type<PostMaeveWorldStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "post_maeve_world_state"));

    public static final StreamCodec<ByteBuf, PostMaeveWorldStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        ByteBufCodecs.BOOL.encode(buffer, payload.maeveErased());
                        ByteBufCodecs.BOOL.encode(
                                buffer, payload.undoneSpawningReleased());
                        ByteBufCodecs.VAR_LONG.encode(
                                buffer, payload.moonriseStartDayTime());
                        ByteBufCodecs.VAR_LONG.encode(
                                buffer, payload.moonElapsedDayTicks());
                        ByteBufCodecs.VAR_LONG.encode(
                                buffer, payload.moonSyncDayTime());
                        buffer.writeLong(payload.moonVisualSeed());
                        ByteBufCodecs.BOOL.encode(
                                buffer, payload.moonriseStarted());
                    },
                    buffer -> new PostMaeveWorldStatePayload(
                            ByteBufCodecs.BOOL.decode(buffer),
                            ByteBufCodecs.BOOL.decode(buffer),
                            ByteBufCodecs.VAR_LONG.decode(buffer),
                            ByteBufCodecs.VAR_LONG.decode(buffer),
                            ByteBufCodecs.VAR_LONG.decode(buffer),
                            buffer.readLong(),
                            ByteBufCodecs.BOOL.decode(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
