package com.frozendawn.entity;

/** Explicit forms used by the assembled Frostmite colony. */
public enum FrostwritheState {
    ASSEMBLING,
    CRAWLER,
    BURROWING,
    ERUPTING,
    SHELL,
    CLIMBER,
    BRIDGING,
    OVERRUN,
    DISASSEMBLING,
    LOOSE,
    REGROUPING,
    DEAD;

    public static FrostwritheState byOrdinal(int ordinal) {
        FrostwritheState[] values = values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal] : CRAWLER;
    }

    public boolean isUnsafeAfterReload() {
        return this == ASSEMBLING || this == BURROWING || this == ERUPTING
                || this == CLIMBER
                || this == BRIDGING || this == OVERRUN
                || this == DISASSEMBLING || this == LOOSE
                || this == REGROUPING || this == DEAD;
    }
}
