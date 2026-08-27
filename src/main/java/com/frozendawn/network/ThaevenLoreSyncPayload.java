package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Per-player archive cache plus the currently relevant world semantics. */
public record ThaevenLoreSyncPayload(
        long discoveredMask, boolean recipeDiscovered,
        int[] seenRevisions, int architectLidRevision)
        implements CustomPacketPayload {
    public static final Type<ThaevenLoreSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "thaeven_lore_sync"));
    public static final StreamCodec<ByteBuf, ThaevenLoreSyncPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeLong(payload.discoveredMask());
                ByteBufCodecs.BOOL.encode(buffer, payload.recipeDiscovered());
                ByteBufCodecs.VAR_INT.encode(
                        buffer, payload.seenRevisions().length);
                for (int revision : payload.seenRevisions()) {
                    ByteBufCodecs.VAR_INT.encode(buffer, revision);
                }
                ByteBufCodecs.VAR_INT.encode(
                        buffer, payload.architectLidRevision());
            }, buffer -> {
                long discovered = buffer.readLong();
                boolean recipe = ByteBufCodecs.BOOL.decode(buffer);
                int encodedLength = Math.max(0,
                        ByteBufCodecs.VAR_INT.decode(buffer));
                int[] seen = new int[Math.min(64, encodedLength)];
                for (int index = 0; index < encodedLength; index++) {
                    int revision = ByteBufCodecs.VAR_INT.decode(buffer);
                    if (index < seen.length) {
                        seen[index] = revision;
                    }
                }
                return new ThaevenLoreSyncPayload(discovered, recipe, seen,
                        ByteBufCodecs.VAR_INT.decode(buffer));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
