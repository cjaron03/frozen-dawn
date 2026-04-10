package com.frozendawn.barometer;

public enum ForecastBand {
    STABLE("Stable"),
    DETERIORATING("Deteriorating"),
    TRANSITION_LIKELY_SOON("Transition Likely Soon"),
    IMMINENT("Imminent"),
    COLLAPSE_UNDERWAY("Collapse Underway");

    private final String displayName;

    ForecastBand(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isHighUrgency() {
        return this == IMMINENT || this == COLLAPSE_UNDERWAY;
    }
}
