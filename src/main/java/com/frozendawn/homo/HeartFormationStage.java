package com.frozendawn.homo;

import java.util.Locale;

/** Authoritative lifecycle of the post-storm Heart formation. */
public enum HeartFormationStage {
    NONE,
    DEAD_AIR,
    SHAKE,
    GATHER,
    HOLD,
    LIVE;

    public static HeartFormationStage fromName(String name) {
        if (name == null || name.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
