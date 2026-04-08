package com.frozendawn.entity;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectObserveDurationTest {

    private static final int MIN_OBSERVE_TICKS = 600;
    private static final int MAX_OBSERVE_TICKS = 1200;

    @Test
    void sampleObserveTargetTicksRespectsInclusiveBounds() {
        assertEquals(
                MIN_OBSERVE_TICKS,
                ArchitectObserveDuration.sampleObserveTargetTicks(MIN_OBSERVE_TICKS, MAX_OBSERVE_TICKS, bound -> 0)
        );
        assertEquals(
                MAX_OBSERVE_TICKS,
                ArchitectObserveDuration.sampleObserveTargetTicks(
                        MIN_OBSERVE_TICKS,
                        MAX_OBSERVE_TICKS,
                        bound -> bound - 1
                )
        );

        Random random = new Random(12345L);
        for (int i = 0; i < 10_000; i++) {
            int sample = ArchitectObserveDuration.sampleObserveTargetTicks(
                    MIN_OBSERVE_TICKS,
                    MAX_OBSERVE_TICKS,
                    random::nextInt
            );
            assertTrue(sample >= MIN_OBSERVE_TICKS);
            assertTrue(sample <= MAX_OBSERVE_TICKS);
        }
    }

    @Test
    void hasReachedObserveTargetUsesGreaterThanOrEqualBoundary() {
        int target = 900;
        assertFalse(ArchitectObserveDuration.hasReachedObserveTarget(target - 1, target));
        assertTrue(ArchitectObserveDuration.hasReachedObserveTarget(target, target));
        assertTrue(ArchitectObserveDuration.hasReachedObserveTarget(target + 1, target));
    }

    @Test
    void ensureObserveTargetTicksSamplesOncePerCycle() {
        AtomicInteger calls = new AtomicInteger();
        IntUnaryOperator nextRandomInt = bound -> {
            calls.incrementAndGet();
            return 25;
        };

        int first = ArchitectObserveDuration.ensureObserveTargetTicks(
                0,
                MIN_OBSERVE_TICKS,
                MAX_OBSERVE_TICKS,
                nextRandomInt
        );
        int second = ArchitectObserveDuration.ensureObserveTargetTicks(
                first,
                MIN_OBSERVE_TICKS,
                MAX_OBSERVE_TICKS,
                nextRandomInt
        );
        int third = ArchitectObserveDuration.ensureObserveTargetTicks(
                second,
                MIN_OBSERVE_TICKS,
                MAX_OBSERVE_TICKS,
                nextRandomInt
        );

        assertEquals(MIN_OBSERVE_TICKS + 25, first);
        assertEquals(first, second);
        assertEquals(first, third);
        assertEquals(1, calls.get());
    }

    @Test
    void ensureObserveTargetTicksResamplesAfterReset() {
        AtomicInteger calls = new AtomicInteger();
        IntUnaryOperator nextRandomInt = bound -> {
            int call = calls.getAndIncrement();
            return call == 0 ? 11 : 47;
        };

        int firstCycleTarget = ArchitectObserveDuration.ensureObserveTargetTicks(
                0,
                MIN_OBSERVE_TICKS,
                MAX_OBSERVE_TICKS,
                nextRandomInt
        );
        int secondCycleTarget = ArchitectObserveDuration.ensureObserveTargetTicks(
                0,
                MIN_OBSERVE_TICKS,
                MAX_OBSERVE_TICKS,
                nextRandomInt
        );

        assertEquals(MIN_OBSERVE_TICKS + 11, firstCycleTarget);
        assertEquals(MIN_OBSERVE_TICKS + 47, secondCycleTarget);
        assertEquals(2, calls.get());
    }
}
