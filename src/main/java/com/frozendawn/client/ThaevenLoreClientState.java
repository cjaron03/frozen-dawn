package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.lore.ThaevenRecordId;
import com.frozendawn.network.ThaevenLoreSyncPayload;
import java.util.Arrays;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Disposable client mirror; the server SavedData remains authoritative. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class ThaevenLoreClientState {
    private static long discoveredMask;
    private static boolean recipeDiscovered;
    private static int[] seenRevisions =
            new int[ThaevenRecordId.values().length];
    private static int[] currentRevisions =
            new int[ThaevenRecordId.values().length];

    private ThaevenLoreClientState() {
    }

    static {
        Arrays.fill(seenRevisions, -1);
    }

    public static void update(ThaevenLoreSyncPayload payload) {
        discoveredMask = payload.discoveredMask();
        recipeDiscovered = payload.recipeDiscovered();
        seenRevisions = new int[ThaevenRecordId.values().length];
        Arrays.fill(seenRevisions, -1);
        System.arraycopy(payload.seenRevisions(), 0, seenRevisions, 0,
                Math.min(payload.seenRevisions().length,
                        seenRevisions.length));
        currentRevisions = new int[ThaevenRecordId.values().length];
        for (int index = 0; index < Math.min(
                payload.currentRevisions().length, currentRevisions.length);
                index++) {
            currentRevisions[index] = Math.max(0,
                    payload.currentRevisions()[index]);
        }
    }

    public static boolean has(ThaevenRecordId record) {
        return (discoveredMask & record.bit()) != 0L;
    }

    public static int seenRevision(ThaevenRecordId record) {
        return seenRevisions[record.ordinal()];
    }

    public static int currentRevision(ThaevenRecordId record) {
        return currentRevisions[record.ordinal()];
    }

    public static boolean recipeDiscovered() {
        return recipeDiscovered;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        discoveredMask = 0L;
        recipeDiscovered = false;
        seenRevisions = new int[ThaevenRecordId.values().length];
        Arrays.fill(seenRevisions, -1);
        currentRevisions = new int[ThaevenRecordId.values().length];
    }
}
