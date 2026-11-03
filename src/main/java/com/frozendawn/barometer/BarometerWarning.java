package com.frozendawn.barometer;

public enum BarometerWarning {
    NONE("None"),
    NETHER_SEVERANCE_RISK("Nether severance risk rising"),
    BLIZZARD_INTENSIFYING("Blizzard conditions intensifying"),
    ATMOSPHERIC_THINNING_DETECTED("Atmospheric thinning detected"),
    VACUUM_CONDITIONS_APPROACHING("Vacuum conditions approaching"),
    VACUUM_CONDITIONS_ACTIVE("Vacuum conditions active"),
    COOLING_TREND("Cooling trend detected"),
    PERSISTENT_PRECIPITATION("Persistent precipitation approaching");

    private final String displayName;

    BarometerWarning(String displayName) {
        this.displayName = displayName;
    }

    public String preparationAdvice() {
        return switch (this) {
            case COOLING_TREND -> "Stock fuel and establish reliable heat.";
            case PERSISTENT_PRECIPITATION -> "Protect food production and prepare warm shelter.";
            default -> "";
        };
    }

    public String displayName() {
        return displayName;
    }
}
