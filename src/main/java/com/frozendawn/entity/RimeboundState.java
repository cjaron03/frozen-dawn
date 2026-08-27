package com.frozendawn.entity;

/** Server-authoritative activity state for the Rimebound encounter. */
public enum RimeboundState {
    DORMANT,
    EMERGING,
    STALKING,
    BURROWING,
    ERUPTING,
    RANGED_WINDUP,
    LEAP_WINDUP,
    RECOVERY,
    ARMORED,
    DEAD;

    public static RimeboundState byOrdinal(int ordinal) {
        RimeboundState[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public boolean isUnsafeAfterReload() {
        return this == BURROWING || this == ERUPTING || this == LEAP_WINDUP;
    }
}
