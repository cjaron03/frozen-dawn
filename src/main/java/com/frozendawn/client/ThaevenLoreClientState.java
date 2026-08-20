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
    private static int architectLidRevision;

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
        architectLidRevision = Math.max(0, payload.architectLidRevision());
    }

    public static boolean has(ThaevenRecordId record) {
        return (discoveredMask & record.bit()) != 0L;
    }

    public static int seenRevision(ThaevenRecordId record) {
        return seenRevisions[record.ordinal()];
    }

    public static int currentRevision(ThaevenRecordId record) {
        return record == ThaevenRecordId.THE_PASSAGE
                ? architectLidRevision : 0;
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
        architectLidRevision = 0;
    }
}
