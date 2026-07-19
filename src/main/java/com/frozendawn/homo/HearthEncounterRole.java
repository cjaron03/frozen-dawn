package com.frozendawn.homo;

import java.util.Locale;

/** Persistent role assigned to a congregation member for the Master encounter. */
public enum HearthEncounterRole {
    UNASSIGNED,
    DISPATCHED,
    RESERVED,
    BYSTANDER,
    TETHERED,
    SPENT;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static HearthEncounterRole fromSerializedName(String value) {
        if (value == null || value.isBlank()) {
            return UNASSIGNED;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNASSIGNED;
        }
    }

    public boolean isPassive() {
        return this == BYSTANDER || this == TETHERED || this == SPENT;
    }
}
