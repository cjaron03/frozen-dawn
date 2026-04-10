package com.frozendawn.barometer;

public enum BarometerWarning {
    NONE("None"),
    NETHER_SEVERANCE_RISK("Nether severance risk rising"),
    BLIZZARD_INTENSIFYING("Blizzard conditions intensifying"),
    ATMOSPHERIC_THINNING_DETECTED("Atmospheric thinning detected"),
    VACUUM_CONDITIONS_APPROACHING("Vacuum conditions approaching"),
    VACUUM_CONDITIONS_ACTIVE("Vacuum conditions active");

    private final String displayName;

    BarometerWarning(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
