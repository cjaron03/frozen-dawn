package com.frozendawn.entity;

public enum RemnantState {
    PLACING,
    DORMANT,
    OBSERVING,
    LURE_READY,
    COMMITTED,
    SEALING,
    HUNTING,
    EXPOSED,
    COLLAPSING,
    RESOLVED,
    DYING;

    public static RemnantState byOrdinal(int ordinal) {
        RemnantState[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DORMANT;
    }

    public boolean isCommitted() {
        return ordinal() >= COMMITTED.ordinal() && this != RESOLVED;
    }

    public boolean acceptsFalseRadio() {
        return this == DORMANT || this == OBSERVING || this == LURE_READY;
    }

    public boolean locksShelter() {
        return this == COMMITTED || this == SEALING || this == HUNTING
                || this == EXPOSED || this == DYING;
    }

    public boolean protectsShelterFromEnvironment() {
        return this != COLLAPSING && this != RESOLVED;
    }

    public boolean isUnsafeAfterReload() {
        return this == EXPOSED || this == DYING;
    }
}
