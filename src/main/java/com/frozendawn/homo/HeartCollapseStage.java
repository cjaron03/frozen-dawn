package com.frozendawn.homo;

import java.util.Locale;

/** Persisted visual stages after the Heart's fifth memory is erased. */
public enum HeartCollapseStage {
    NONE,
    RUPTURE,
    FALL,
    SETTLE,
    DORMANT;

    public static HeartCollapseStage fromName(String name) {
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
