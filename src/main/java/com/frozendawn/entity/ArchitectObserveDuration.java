package com.frozendawn.entity;

import java.util.function.IntUnaryOperator;

/**
 * Pure observe-duration timing logic, kept dependency-free for deterministic tests.
 */
final class ArchitectObserveDuration {

    private ArchitectObserveDuration() {
    }

    static int ensureObserveTargetTicks(
            int currentObserveTargetTicks,
            int minObserveTicks,
            int maxObserveTicks,
            IntUnaryOperator nextRandomInt
    ) {
        if (currentObserveTargetTicks > 0) {
            return currentObserveTargetTicks;
        }
        return sampleObserveTargetTicks(minObserveTicks, maxObserveTicks, nextRandomInt);
    }

    static int sampleObserveTargetTicks(int minObserveTicks, int maxObserveTicks, IntUnaryOperator nextRandomInt) {
        if (maxObserveTicks < minObserveTicks) {
            throw new IllegalArgumentException("maxObserveTicks must be >= minObserveTicks");
        }
        return minObserveTicks + nextRandomInt.applyAsInt(maxObserveTicks - minObserveTicks + 1);
    }

    static boolean hasReachedObserveTarget(int observeTicks, int observeTargetTicks) {
        return observeTicks >= observeTargetTicks;
    }
}
