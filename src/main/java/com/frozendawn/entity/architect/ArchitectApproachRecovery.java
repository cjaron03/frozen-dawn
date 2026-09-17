package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/** Recovery budgets independent of navigation, mining and action-local stuck trackers. */
public final class ArchitectApproachRecovery {

    public static final int MAX_FAILED_BREAK_CANDIDATES = 6;
    public static final int MAX_NO_PROGRESS_TICKS = 600;
    public static final int MAX_NO_PROGRESS_REINITS = 4;
    public static final int TARGET_RETRY_COOLDOWN_TICKS = 200;
    public static final int LOCAL_RECOVERY_INTERVAL_TICKS = 40;
    public static final int LOCAL_REPLAN_INTERVAL_TICKS = 160;
    // A one-cell ping-pong or jump in place is not an escape from the stalled area.
    private static final double PROGRESS_DISPLACEMENT_SQR = 4.0;

    private ArchitectApproachRecovery() {
    }

    public static boolean canAttemptBreak(ArchitectApproachState state, BlockPos pos) {
        return state.blockedUnstickBreakCandidates.size() < MAX_FAILED_BREAK_CANDIDATES
                && !state.blockedUnstickBreakCandidates.contains(pos);
    }

    /** Called only when a selected target is released or replaced, never when selected. */
    public static boolean finishBreakAttempt(ArchitectApproachState state, BlockPos pos, boolean stillObstructing) {
        if (!stillObstructing) {
            state.blockedUnstickBreakCandidates.remove(pos);
            return false;
        }
        return state.blockedUnstickBreakCandidates.size() < MAX_FAILED_BREAK_CANDIDATES
                && state.blockedUnstickBreakCandidates.add(pos.immutable());
    }

    public static void recordReinit(ArchitectApproachState state) {
        ArchitectWalkTracking.resetUnstickBreakTracker(state);
        state.unstickReinitAttempts++;
    }

    public static boolean shouldTryLocalRecovery(ArchitectApproachState state, boolean hasBreakTarget) {
        return !hasBreakTarget && state.approachNoProgressTicks > 0
                && state.approachNoProgressTicks % LOCAL_RECOVERY_INTERVAL_TICKS == 0;
    }

    /** Sample once per active approach tick, before any movement/mining/planning early return. */
    @Nullable
    public static String tick(ArchitectApproachState state, UUID target, Vec3 position) {
        if (!target.equals(state.approachProgressTarget)
                || state.approachProgressAnchor == null
                || position.distanceToSqr(state.approachProgressAnchor) >= PROGRESS_DISPLACEMENT_SQR) {
            resetProgress(state);
            state.approachProgressTarget = target;
            state.approachProgressAnchor = position;
        }
        state.approachNoProgressTicks++;
        if (state.unstickReinitAttempts >= MAX_NO_PROGRESS_REINITS) {
            return "REPEATED_REPLAN_NO_PROGRESS";
        }
        return state.approachNoProgressTicks >= MAX_NO_PROGRESS_TICKS ? "NO_PROGRESS_TIMEOUT" : null;
    }

    public static void resetProgress(ArchitectApproachState state) {
        state.approachProgressTarget = null;
        state.approachProgressAnchor = null;
        state.approachNoProgressTicks = 0;
        state.unstickReinitAttempts = 0;
        ArchitectWalkTracking.resetUnstickBreakTracker(state);
    }

    public static void abandon(ArchitectApproachState state, UUID target, int tick) {
        state.abandonedApproachTarget = target;
        state.approachRetryAfterTick = tick + TARGET_RETRY_COOLDOWN_TICKS;
        resetProgress(state);
    }

    public static boolean isTargetSuppressed(ArchitectApproachState state, UUID target, int tick) {
        return target.equals(state.abandonedApproachTarget) && tick < state.approachRetryAfterTick;
    }
}
