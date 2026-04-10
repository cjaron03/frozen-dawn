package com.frozendawn.barometer;

public record PhaseBarometerSnapshot(
        int currentPhase,
        String currentPhaseName,
        ForecastBand forecastBand,
        UpcomingState upcomingState,
        BarometerWarning warning,
        float severity
) {
    public boolean shouldBlink() {
        return forecastBand.isHighUrgency();
    }
}
