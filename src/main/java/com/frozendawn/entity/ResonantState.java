package com.frozendawn.entity;

public enum ResonantState {
    DORMANT,
    LISTENING,
    TRIANGULATING,
    PHASING,
    STALKING,
    BREACHING,
    GRABBING,
    DISORIENTED;

    public static ResonantState byOrdinal(int ordinal) {
        ResonantState[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : LISTENING;
    }

    public boolean isUnsafeAfterReload() {
        return this == PHASING || this == BREACHING || this == GRABBING;
    }
}
