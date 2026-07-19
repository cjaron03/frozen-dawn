package com.frozendawn.homo;

import java.util.Locale;

/** Persisted, server-authoritative stages of the Master Architect encounter. */
public enum MasterArchitectCombatPhase {
    KIT(0),
    CONSTRUCTION(1),
    TETHER(2),
    ASCENT(3),
    FLOOD(4);

    private final int id;

    MasterArchitectCombatPhase(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isBefore(MasterArchitectCombatPhase other) {
        return id < other.id;
    }

    public static MasterArchitectCombatPhase fromId(int id) {
        for (MasterArchitectCombatPhase phase : values()) {
            if (phase.id == id) {
                return phase;
            }
        }
        return KIT;
    }

    public static MasterArchitectCombatPhase fromSerializedName(String name) {
        if (name == null) {
            return KIT;
        }
        for (MasterArchitectCombatPhase phase : values()) {
            if (phase.serializedName().equals(name.toLowerCase(Locale.ROOT))) {
                return phase;
            }
        }
        return KIT;
    }
}
