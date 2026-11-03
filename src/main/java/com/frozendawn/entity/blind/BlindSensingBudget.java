package com.frozendawn.entity.blind;

import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/** Shared level and per-executor work bounds include echo validation and game-event bursts. */
public final class BlindSensingBudget {
    private static final WeakHashMap<ServerLevel, BlindSensingBudget> LEVELS = new WeakHashMap<>();
    private long tick = Long.MIN_VALUE;
    private int used;
    public static BlindSensingBudget forLevel(ServerLevel level) { return LEVELS.computeIfAbsent(level, ignored -> new BlindSensingBudget()); }
    public int remaining(long now) {
        if (now != tick) { tick = now; used = 0; }
        return Math.max(0, BlindArchitectPolicy.LEVEL_WORK - used);
    }
    public void spend(long now, int count) { remaining(now); used += count; }
}
