package com.frozendawn.oxygen;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Continuous authoritative loaded samples; the unavailable latch belongs to separate SavedData. */
public final class BreathingOxygenPolicy {
    private final FurnaceOxygenPolicy timing = new FurnaceOxygenPolicy();
    public boolean update(long pool, long now, boolean ready, boolean breathingOverload,
            boolean flamesRemain, boolean fullySupported, boolean unavailable) {
        boolean grace = timing.shouldShed(pool, now, ready, breathingOverload);
        boolean recovery = timing.canResume(pool, now, ready && !breathingOverload && fullySupported);
        if (!ready) return unavailable;
        if (unavailable) return !recovery;
        return grace && !flamesRemain;
    }
    public void resetPool(long pool) { timing.resetPool(pool); timing.resetRecovery(pool); }
    public void retain(Set<Long> pools) { timing.retain(pools, pools); }
    public void reset() { timing.reset(); }
}
