package com.frozendawn.oxygen;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Continuous loaded, authoritative samples only. Gaps never count toward a grace period. */
public final class FurnaceOxygenPolicy {
    public static final int CHECK_TICKS = 20, GRACE_TICKS = 200, RECOVERY_TICKS = 40;
    private static final class Timer {
        long last; int elapsed;
        Timer(long now) { last = now; }
        void sample(long now) {
            if (now < last || now - last > CHECK_TICKS) elapsed = 0;
            else elapsed = Math.min(GRACE_TICKS, elapsed + (int)(now - last));
            last = now;
        }
    }
    private final Map<Long, Timer> overload = new HashMap<>(), recovery = new HashMap<>();
    public boolean shouldShed(long pool, long now, boolean ready, boolean strained) {
        return sustained(overload, pool, now, ready && strained, GRACE_TICKS);
    }
    public boolean canResume(long furnace, long now, boolean supported) {
        return sustained(recovery, furnace, now, supported, RECOVERY_TICKS);
    }
    private static boolean sustained(Map<Long, Timer> timers, long id, long now, boolean condition, int threshold) {
        if (!condition) { timers.remove(id); return false; }
        var timer = timers.get(id);
        if (timer == null) { timers.put(id, new Timer(now)); return false; }
        timer.sample(now);
        return timer.elapsed >= threshold;
    }
    public void retain(Set<Long> pools, Set<Long> furnaces) {
        overload.keySet().retainAll(pools); recovery.keySet().retainAll(furnaces);
    }
    public void resetPool(long pool) { overload.remove(pool); }
    public void resetRecovery(long furnace) { recovery.remove(furnace); }
    public void reset() { overload.clear(); recovery.clear(); }
}
