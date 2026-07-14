package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthSurveyPolicyTest {

    @Test
    void plannedHearthsStayRadioSilent() {
        assertFalse(HearthSurveyPolicy.emitsSignal(ReturnedHearthSavedData.HearthStage.PLANNED));
        assertTrue(HearthSurveyPolicy.emitsSignal(ReturnedHearthSavedData.HearthStage.TRACE));
        assertTrue(HearthSurveyPolicy.emitsSignal(ReturnedHearthSavedData.HearthStage.FORMED));
        assertTrue(HearthSurveyPolicy.emitsSignal(ReturnedHearthSavedData.HearthStage.INTACT));
    }

    @Test
    void signalBandsResolveTowardTheHearth() {
        HearthSurveyPolicy.SignalProfile profile = HearthSurveyPolicy.STANDARD;

        assertEquals(HearthSurveyPolicy.SignalBand.NONE,
                HearthSurveyPolicy.bandFor(1_281.0D, profile, false));
        assertEquals(HearthSurveyPolicy.SignalBand.STATIC,
                HearthSurveyPolicy.bandFor(1_000.0D, profile, false));
        assertEquals(HearthSurveyPolicy.SignalBand.CARRIER,
                HearthSurveyPolicy.bandFor(640.0D, profile, false));
        assertEquals(HearthSurveyPolicy.SignalBand.FRAGMENT,
                HearthSurveyPolicy.bandFor(256.0D, profile, false));
        assertEquals(HearthSurveyPolicy.SignalBand.LOCK,
                HearthSurveyPolicy.bandFor(96.0D, profile, false));
        assertEquals(HearthSurveyPolicy.SignalBand.CATALOGUED,
                HearthSurveyPolicy.bandFor(1_000.0D, profile, true));
    }

    @Test
    void calibratedLensCanReachOptionalMinorSelectionRadius() {
        assertTrue(HearthSurveyPolicy.CALIBRATED.maximumRange()
                > HearthSelectionPolicy.MINOR_MAX_RADIUS);
        assertTrue(HearthSurveyPolicy.STANDARD.maximumRange()
                > HearthSelectionPolicy.MAJOR_MAX_RADIUS);
    }

    @Test
    void majorAndMatureHearthsCarryStrongerSignals() {
        float traceMajor = HearthSurveyPolicy.intrinsicStrength(
                HearthSelectionPolicy.HearthType.MAJOR,
                ReturnedHearthSavedData.HearthStage.TRACE);
        float formedMajor = HearthSurveyPolicy.intrinsicStrength(
                HearthSelectionPolicy.HearthType.MAJOR,
                ReturnedHearthSavedData.HearthStage.FORMED);
        float formedMinor = HearthSurveyPolicy.intrinsicStrength(
                HearthSelectionPolicy.HearthType.MINOR,
                ReturnedHearthSavedData.HearthStage.FORMED);

        assertTrue(formedMajor > traceMajor);
        assertTrue(formedMajor > formedMinor);
        assertTrue(HearthSurveyPolicy.observedStrength(
                formedMajor, 128.0D, HearthSurveyPolicy.STANDARD)
                > HearthSurveyPolicy.observedStrength(
                formedMajor, 1_000.0D, HearthSurveyPolicy.STANDARD));
    }

    @Test
    void geigerCadenceAcceleratesSmoothlyTowardTheSignal() {
        assertEquals(42, HearthSurveyPolicy.geigerMeanIntervalTicks(0.0F));
        assertEquals(3, HearthSurveyPolicy.geigerMeanIntervalTicks(1.0F));
        assertTrue(HearthSurveyPolicy.geigerMeanIntervalTicks(0.25F)
                > HearthSurveyPolicy.geigerMeanIntervalTicks(0.5F));
        assertTrue(HearthSurveyPolicy.geigerMeanIntervalTicks(0.5F)
                > HearthSurveyPolicy.geigerMeanIntervalTicks(0.75F));
    }

    @Test
    void geigerIntervalsFollowBoundedPoissonTiming() {
        int farMedian = HearthSurveyPolicy.sampleGeigerIntervalTicks(0.0F, 0.5F);
        int nearMedian = HearthSurveyPolicy.sampleGeigerIntervalTicks(1.0F, 0.5F);

        assertTrue(farMedian > nearMedian);
        assertEquals(1, HearthSurveyPolicy.sampleGeigerIntervalTicks(0.5F, 0.0F));
        assertEquals(
                HearthSurveyPolicy.geigerMeanIntervalTicks(0.5F) * 3,
                HearthSurveyPolicy.sampleGeigerIntervalTicks(0.5F, 0.9999F));
    }

    @Test
    void proximityIsNormalizedByLensRange() {
        assertEquals(0.0F, HearthSurveyPolicy.proximity(
                HearthSurveyPolicy.STANDARD.maximumRange(), HearthSurveyPolicy.STANDARD));
        assertEquals(1.0F, HearthSurveyPolicy.proximity(0.0D, HearthSurveyPolicy.STANDARD));
        assertTrue(HearthSurveyPolicy.proximity(500.0D, HearthSurveyPolicy.CALIBRATED)
                > HearthSurveyPolicy.proximity(500.0D, HearthSurveyPolicy.STANDARD));
    }
}
