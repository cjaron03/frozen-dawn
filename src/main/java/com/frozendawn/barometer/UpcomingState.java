package com.frozendawn.barometer;

public enum UpcomingState {
    PHASE_1("Phase 1 // Twilight"),
    PHASE_2("Phase 2 // Cooling"),
    PHASE_3("Phase 3 // The Long Night"),
    PHASE_4("Phase 4 // Deep Freeze"),
    PHASE_5("Phase 5 // Eternal Winter"),
    PHASE_6("Phase 6 // Atmospheric Collapse"),
    ATMOSPHERIC_THINNING("Atmospheric Thinning"),
    VACUUM_ONSET("Vacuum Onset"),
    TERMINAL_CONDITIONS("Terminal Conditions");

    private final String displayName;

    UpcomingState(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
