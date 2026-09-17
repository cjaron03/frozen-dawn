package com.frozendawn.world;

import java.util.function.LongSupplier;

/**
 * Shared wall-clock budget for one landmark-planning tick.
 *
 * <p>The first candidate is always allowed so planning cannot stall if one
 * terrain sample alone exceeds the target. The legacy candidate cap remains as
 * a secondary guard for unusually cheap generators or a non-advancing clock.</p>
 */
final class LandmarkPlanningBudget {
    static final long TICK_BUDGET_NANOS = 8_000_000L;
    static final int MAX_CANDIDATES_PER_TICK = 192;

    private final LongSupplier nanoTime;
    private final long startNanos;
    private int candidates;

    private LandmarkPlanningBudget(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        this.startNanos = nanoTime.getAsLong();
    }

    static LandmarkPlanningBudget start() {
        return new LandmarkPlanningBudget(System::nanoTime);
    }

    static LandmarkPlanningBudget start(LongSupplier nanoTime) {
        return new LandmarkPlanningBudget(nanoTime);
    }

    boolean tryAcquireCandidate() {
        if (candidates >= MAX_CANDIDATES_PER_TICK) {
            return false;
        }
        if (candidates > 0 && nanoTime.getAsLong() - startNanos >= TICK_BUDGET_NANOS) {
            return false;
        }
        candidates++;
        return true;
    }
}
