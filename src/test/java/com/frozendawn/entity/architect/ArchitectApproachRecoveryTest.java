package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectApproachRecoveryTest {
    private static final UUID TARGET = new UUID(0, 1);
    private static final BlockPos A = new BlockPos(0, 65, 0);
    private static final BlockPos B = new BlockPos(1, 64, 0);

    @Test
    void alternatingFailuresExhaustBothImmediateAndCorridorCandidates() {
        ArchitectApproachState state = new ArchitectApproachState();
        List<BlockPos> candidates = List.of(A, B);
        assertEquals(A, select(state, candidates));
        // Selection itself has no effect: only a terminal unsuccessful attempt excludes it.
        assertEquals(A, select(state, candidates));
        ArchitectApproachRecovery.finishBreakAttempt(state, A, true);
        assertEquals(B, select(state, candidates));
        ArchitectApproachRecovery.finishBreakAttempt(state, B, true);
        assertNull(select(state, candidates));
        assertNull(ArchitectWalkBreakPlanner.findCorridorBreakTarget(candidates,
                state.blockedUnstickBreakCandidates, candidates::contains, pos -> false));
    }

    @Test
    void failureDoesNotExcludeAdjacentHeadroomAndCorridorSkipsFailedFirstNode() {
        ArchitectApproachState state = new ArchitectApproachState();
        ArchitectApproachRecovery.finishBreakAttempt(state, B, true);
        assertEquals(B.above(), select(state, List.of(B, B.above())));
        assertEquals(B.above(), ArchitectWalkBreakPlanner.findCorridorBreakTarget(List.of(B),
                state.blockedUnstickBreakCandidates, pos -> true, pos -> false));
        assertEquals(A, ArchitectWalkBreakPlanner.findCorridorBreakTarget(List.of(B, A),
                state.blockedUnstickBreakCandidates, List.of(A, B)::contains, pos -> false));
    }

    @Test
    void successfulBreakDoesNotForgetAnUnrelatedFailure() {
        ArchitectApproachState state = new ArchitectApproachState();
        ArchitectApproachRecovery.finishBreakAttempt(state, A, true);
        ArchitectApproachRecovery.finishBreakAttempt(state, B, false);
        assertFalse(ArchitectApproachRecovery.canAttemptBreak(state, A));
        assertTrue(ArchitectApproachRecovery.canAttemptBreak(state, B));
    }

    @Test
    void failureSetSaturatesInsteadOfRotatingOldFailuresBackIn() {
        ArchitectApproachState state = new ArchitectApproachState();
        for (int x = 0; x < 20; x++) {
            ArchitectApproachRecovery.finishBreakAttempt(state, new BlockPos(x, 65, 0), true);
        }
        assertEquals(ArchitectApproachRecovery.MAX_FAILED_BREAK_CANDIDATES,
                state.blockedUnstickBreakCandidates.size());
        assertTrue(state.blockedUnstickBreakCandidates.contains(A));
        assertFalse(ArchitectApproachRecovery.canAttemptBreak(state, new BlockPos(100, 65, 0)));
    }

    @Test
    void reinitClearsExclusionsButPreservesGlobalNoProgressBudget() {
        ArchitectApproachState state = new ArchitectApproachState();
        ArchitectApproachRecovery.tick(state, TARGET, Vec3.ZERO);
        for (int i = 0; i < ArchitectApproachRecovery.MAX_NO_PROGRESS_REINITS; i++) {
            ArchitectApproachRecovery.finishBreakAttempt(state, A, true);
            ArchitectApproachRecovery.recordReinit(state);
            assertTrue(state.blockedUnstickBreakCandidates.isEmpty());
        }
        assertEquals("REPEATED_REPLAN_NO_PROGRESS", ArchitectApproachRecovery.tick(state, TARGET, Vec3.ZERO));
        assertEquals(2, state.approachNoProgressTicks);
    }

    @Test
    void localTrackerResetsAndOneCellPingPongCannotDefeatTimeoutWithoutAnyReinit() {
        ArchitectApproachState state = new ArchitectApproachState();
        for (int tick = 1; tick <= ArchitectApproachRecovery.MAX_NO_PROGRESS_TICKS; tick++) {
            ArchitectWalkTracking.resetWalkStuckTracker(state);
            ArchitectWalkTracking.resetUnstickBreakTracker(state);
            ArchitectActionTransitionSupport.onLeaveApproach(state);
            String reason = ArchitectApproachRecovery.tick(state, TARGET, new Vec3(tick % 2, 0, 0));
            assertEquals(tick == ArchitectApproachRecovery.MAX_NO_PROGRESS_TICKS ? "NO_PROGRESS_TIMEOUT" : null, reason);
        }
        assertEquals(0, state.unstickReinitAttempts);
    }

    @Test
    void meaningfulDisplacementResetsBothRecoveryBudgets() {
        ArchitectApproachState state = new ArchitectApproachState();
        ArchitectApproachRecovery.tick(state, TARGET, Vec3.ZERO);
        ArchitectApproachRecovery.recordReinit(state);
        ArchitectApproachRecovery.finishBreakAttempt(state, A, true);
        assertNull(ArchitectApproachRecovery.tick(state, TARGET, new Vec3(2, 0, 0)));
        assertEquals(1, state.approachNoProgressTicks);
        assertEquals(0, state.unstickReinitAttempts);
        assertTrue(state.blockedUnstickBreakCandidates.isEmpty());
    }

    @Test
    void targetMovementIsNotRequiredToAccumulateTimeAndNewTargetStartsFresh() {
        ArchitectApproachState state = new ArchitectApproachState();
        ArchitectApproachRecovery.tick(state, TARGET, Vec3.ZERO);
        ArchitectApproachRecovery.recordReinit(state);
        ArchitectApproachRecovery.tick(state, new UUID(0, 2), Vec3.ZERO);
        assertEquals(1, state.approachNoProgressTicks);
        assertEquals(0, state.unstickReinitAttempts);
    }

    @Test
    void abandonedTargetStaysSuppressedAcrossRoamResetsButOtherTargetsRemainAvailable() {
        ArchitectApproachState state = new ArchitectApproachState();
        ArchitectApproachRecovery.abandon(state, TARGET, 1000);
        ArchitectApproachRecovery.resetProgress(state);
        assertTrue(ArchitectApproachRecovery.isTargetSuppressed(state, TARGET, 1199));
        assertFalse(ArchitectApproachRecovery.isTargetSuppressed(state, TARGET, 1200));
        assertFalse(ArchitectApproachRecovery.isTargetSuppressed(state, new UUID(0, 2), 1001));
    }

    @Test
    void fallbackTrackerResetsCannotPreventPeriodicRecoveryAndMiningIsNotInterrupted() {
        ArchitectApproachState state = new ArchitectApproachState();
        int attempts = 0;
        for (int tick = 1; tick <= 160; tick++) {
            ArchitectWalkTracking.resetWalkStuckTracker(state);
            ArchitectApproachRecovery.tick(state, TARGET, Vec3.ZERO);
            if (ArchitectApproachRecovery.shouldTryLocalRecovery(state, false)) {
                attempts++;
                assertFalse(ArchitectApproachRecovery.shouldTryLocalRecovery(state, true));
            }
        }
        assertEquals(4, attempts);
    }

    private BlockPos select(ArchitectApproachState state, List<BlockPos> candidates) {
        return ArchitectWalkBreakPlanner.selectPreferredBreakCandidate(candidates,
                state.blockedUnstickBreakCandidates,
                pos -> ArchitectApproachRecovery.canAttemptBreak(state, pos), pos -> false);
    }
}
