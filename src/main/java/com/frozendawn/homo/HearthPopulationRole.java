package com.frozendawn.homo;

import java.util.Locale;

/**
 * Persistent resident slots owned by an INTACT Major Hearth.
 */
public enum HearthPopulationRole {
    RETURNED,
    HUNTER,
    MIMIC,
    ARCHITECT;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static HearthPopulationRole fromSerializedName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
