package com.frozendawn.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player narrative stats used by the finale credits.
 */
public final class PlayerEndStats {

    private static final String ROOT_TAG = "frozendawn:end_stats";
    private static final String TERMINALS_HACKED_TAG = "terminalsHacked";

    private PlayerEndStats() {
    }

    public static int getTerminalsHacked(ServerPlayer player) {
        return getStatsTag(player).getInt(TERMINALS_HACKED_TAG);
    }

    public static void incrementTerminalsHacked(ServerPlayer player) {
        CompoundTag stats = getStatsTag(player);
        stats.putInt(TERMINALS_HACKED_TAG, Math.max(0, stats.getInt(TERMINALS_HACKED_TAG)) + 1);
        player.getPersistentData().put(ROOT_TAG, stats);
    }

    private static CompoundTag getStatsTag(ServerPlayer player) {
        return player.getPersistentData().getCompound(ROOT_TAG).copy();
    }
}
