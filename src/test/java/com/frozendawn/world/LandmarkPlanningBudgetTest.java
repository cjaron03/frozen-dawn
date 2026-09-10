package com.frozendawn.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandmarkPlanningBudgetTest {
    @Test
    void sharedDeadlineStopsLaterConsumers() {
        AtomicLong clock = new AtomicLong();
        LandmarkPlanningBudget budget = LandmarkPlanningBudget.start(clock::get);

        assertTrue(budget.tryAcquireCandidate());
        clock.set(LandmarkPlanningBudget.TICK_BUDGET_NANOS - 1L);
        assertTrue(budget.tryAcquireCandidate());
        clock.set(LandmarkPlanningBudget.TICK_BUDGET_NANOS);
        assertFalse(budget.tryAcquireCandidate());
    }

    @Test
    void firstCandidateAlwaysMakesProgressAfterAnOverrun() {
        AtomicLong clock = new AtomicLong();
        LandmarkPlanningBudget budget = LandmarkPlanningBudget.start(clock::get);
        clock.set(LandmarkPlanningBudget.TICK_BUDGET_NANOS * 2L);

        assertTrue(budget.tryAcquireCandidate());
        assertFalse(budget.tryAcquireCandidate());
    }

    @Test
    void legacyCandidateCapStillBoundsAStationaryClock() {
        LandmarkPlanningBudget budget = LandmarkPlanningBudget.start(() -> 0L);
        int candidates = 0;
        while (budget.tryAcquireCandidate()) {
            candidates++;
        }

        assertEquals(LandmarkPlanningBudget.MAX_CANDIDATES_PER_TICK, candidates);
    }

    @Test
    void resumingAcrossTicksDoesNotSkipOrReorderCandidates() {
        AtomicLong clock = new AtomicLong();
        List<Integer> visited = new ArrayList<>();
        int cursor = 0;

        while (cursor < 48) {
            LandmarkPlanningBudget budget = LandmarkPlanningBudget.start(clock::get);
            while (cursor < 48 && budget.tryAcquireCandidate()) {
                visited.add(cursor++);
                clock.addAndGet(3_000_000L);
            }
        }

        assertEquals(IntStream.range(0, 48).boxed().toList(), visited);
    }
}
