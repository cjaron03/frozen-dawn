package com.frozendawn.maeve;

/** Slice 1 defaults; behavioral calibration belongs to source-of-truth §9.20. */
final class BeliefPolicy {
    static final int MAX_PLAYERS = 128;
    static final int MAX_BELIEFS = 16;
    static final int MAX_PROVENANCE = 8;
    static final long ENCOUNTER_GAP = 600L;
    static final long STALE_AFTER = 20L * 24000L;
    static final long HALF_LIFE = 20L * 24000L;
    static final double SUPPORT = 0.20D;
    static final double CONTRADICTION = 0.35D;

    private BeliefPolicy() { }

    static double decay(double confidence, long updated, long confirmed, long now) {
        long onset = Math.max(updated, confirmed + STALE_AFTER);
        return now <= onset ? confidence : confidence * Math.pow(0.5D, (double) (now - onset) / HALF_LIFE);
    }

    static double clamp(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(1.0D, value)) : 0.0D;
    }

    static int increment(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }
}
