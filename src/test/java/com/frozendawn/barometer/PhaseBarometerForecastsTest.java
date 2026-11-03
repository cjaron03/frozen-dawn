package com.frozendawn.barometer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhaseBarometerForecastsTest {

    @Test
    void phaseOneWarnsAboutCoolingThenPreparationForPersistentRain() {
        var early=PhaseBarometerForecasts.evaluate(1,.057F);
        var late=PhaseBarometerForecasts.evaluate(1,.115F);
        assertEquals(BarometerWarning.COOLING_TREND,early.warning());
        assertEquals(BarometerWarning.PERSISTENT_PRECIPITATION,late.warning());
        org.junit.jupiter.api.Assertions.assertTrue(early.warning().preparationAdvice().contains("fuel"));
        org.junit.jupiter.api.Assertions.assertTrue(late.warning().preparationAdvice().contains("food"));
        assertEquals(BarometerWarning.NONE,PhaseBarometerForecasts.evaluate(0,.01F).warning());
        assertEquals(BarometerWarning.NONE,PhaseBarometerForecasts.evaluate(2,.15F).warning());
    }

    @Test
    void mapsPhaseFourLocalProgressToForecastBands() {
        assertEquals(ForecastBand.STABLE, PhaseBarometerForecasts.evaluate(4, 0.35f).forecastBand());
        assertEquals(ForecastBand.DETERIORATING, PhaseBarometerForecasts.evaluate(4, 0.39f).forecastBand());
        assertEquals(ForecastBand.TRANSITION_LIKELY_SOON, PhaseBarometerForecasts.evaluate(4, 0.43f).forecastBand());
        assertEquals(ForecastBand.IMMINENT, PhaseBarometerForecasts.evaluate(4, 0.45f).forecastBand());
    }

    @Test
    void addsNetherWarningOnlyLateInPhaseFour() {
        assertEquals(BarometerWarning.NONE, PhaseBarometerForecasts.evaluate(4, 0.38f).warning());
        assertEquals(BarometerWarning.NETHER_SEVERANCE_RISK, PhaseBarometerForecasts.evaluate(4, 0.43f).warning());
    }

    @Test
    void keepsPhaseFiveLockedToBlizzardWarning() {
        assertEquals(BarometerWarning.BLIZZARD_INTENSIFYING, PhaseBarometerForecasts.evaluate(5, 0.50f).warning());
    }

    @Test
    void handlesPhaseSixEarlyMidAndVacuumStages() {
        assertEquals(UpcomingState.ATMOSPHERIC_THINNING, PhaseBarometerForecasts.evaluate(6, 0.61f).upcomingState());
        assertEquals(BarometerWarning.ATMOSPHERIC_THINNING_DETECTED, PhaseBarometerForecasts.evaluate(6, 0.61f).warning());

        assertEquals(UpcomingState.VACUUM_ONSET, PhaseBarometerForecasts.evaluate(6, 0.73f).upcomingState());
        assertEquals(BarometerWarning.VACUUM_CONDITIONS_APPROACHING, PhaseBarometerForecasts.evaluate(6, 0.73f).warning());

        PhaseBarometerSnapshot vacuum = PhaseBarometerForecasts.evaluate(6, 0.90f);
        assertEquals(ForecastBand.COLLAPSE_UNDERWAY, vacuum.forecastBand());
        assertEquals(UpcomingState.TERMINAL_CONDITIONS, vacuum.upcomingState());
        assertEquals(BarometerWarning.VACUUM_CONDITIONS_ACTIVE, vacuum.warning());
    }
}
