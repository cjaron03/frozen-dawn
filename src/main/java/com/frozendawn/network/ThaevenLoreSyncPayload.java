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
        int[] seenRevisions, int[] currentRevisions)
        implements CustomPacketPayload {
    public static final Type<ThaevenLoreSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "thaeven_lore_sync"));
    public static final StreamCodec<ByteBuf, ThaevenLoreSyncPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeLong(payload.discoveredMask());
                ByteBufCodecs.BOOL.encode(buffer, payload.recipeDiscovered());
                writeRevisions(buffer, payload.seenRevisions());
                writeRevisions(buffer, payload.currentRevisions());
            }, buffer -> {
                long discovered = buffer.readLong();
                boolean recipe = ByteBufCodecs.BOOL.decode(buffer);
                int[] seen = readRevisions(buffer);
                return new ThaevenLoreSyncPayload(discovered, recipe, seen,
                        readRevisions(buffer));
            });

    private static void writeRevisions(ByteBuf buffer, int[] revisions) {
        ByteBufCodecs.VAR_INT.encode(buffer, revisions.length);
        for (int revision : revisions) {
            ByteBufCodecs.VAR_INT.encode(buffer, revision);
        }
    }

    private static int[] readRevisions(ByteBuf buffer) {
        int encodedLength = Math.max(0, ByteBufCodecs.VAR_INT.decode(buffer));
        int[] revisions = new int[Math.min(64, encodedLength)];
        for (int index = 0; index < encodedLength; index++) {
            int revision = ByteBufCodecs.VAR_INT.decode(buffer);
            if (index < revisions.length) {
                revisions[index] = revision;
            }
        }
        return revisions;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
