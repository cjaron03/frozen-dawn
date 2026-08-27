package com.frozendawn.entity.architect;

/**
 * Bounds the Architect's retreat run independently of a pursuing target.
 */
public final class ArchitectRetreatPolicy {

    public static final double SAFE_TARGET_DISTANCE = 16.0D;
    public static final double MAX_COMMITTED_TRAVEL = 12.0D;
    public static final int MAX_RUN_TICKS = 100;

    private ArchitectRetreatPolicy() {
    }

    public static RunEndReason runEndReason(
            double targetDistance,
            double committedTravel,
            int runTicks
    ) {
        if (targetDistance >= SAFE_TARGET_DISTANCE) {
            return RunEndReason.SAFE_GAP;
        }
        if (committedTravel >= MAX_COMMITTED_TRAVEL) {
            return RunEndReason.TRAVEL_BUDGET;
        }
        if (runTicks >= MAX_RUN_TICKS) {
            return RunEndReason.PATHING_TIMEOUT;
        }
        return RunEndReason.CONTINUE;
    }

    public enum RunEndReason {
        CONTINUE,
        SAFE_GAP,
        TRAVEL_BUDGET,
        PATHING_TIMEOUT
    }
}
