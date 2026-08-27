package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CognitiveLoadPolicyTest {
    @Test
    void stagesChangeAtLockedThresholds() {
        assertEquals(CognitiveLoadPolicy.Stage.NONE,
                CognitiveLoadPolicy.stage(0.0F));
        assertEquals(CognitiveLoadPolicy.Stage.CONTACT,
                CognitiveLoadPolicy.stage(24.999F));
        assertEquals(CognitiveLoadPolicy.Stage.SPREADING,
                CognitiveLoadPolicy.stage(25.0F));
        assertEquals(CognitiveLoadPolicy.Stage.SATURATION,
                CognitiveLoadPolicy.stage(50.0F));
        assertEquals(CognitiveLoadPolicy.Stage.FREAK_OUT,
                CognitiveLoadPolicy.stage(75.0F));
    }

    @Test
    void lineOfSightAndProximityCreatePressure() {
        float near = CognitiveLoadPolicy.nextLoad(
                10.0F, 1.0F, true, CognitiveLoadPolicy.Relief.NONE, 1.0F);
        float far = CognitiveLoadPolicy.nextLoad(
                10.0F, 0.1F, true, CognitiveLoadPolicy.Relief.NONE, 1.0F);
        float blocked = CognitiveLoadPolicy.nextLoad(
                10.0F, 1.0F, false, CognitiveLoadPolicy.Relief.NONE, 1.0F);
        assertTrue(near > far);
        assertTrue(far > 10.0F);
        assertTrue(blocked < 10.0F);
    }

    @Test
    void reliefSourcesDrainAtIncreasingRates() {
        float shelter = CognitiveLoadPolicy.nextLoad(
                50.0F, 1.0F, true, CognitiveLoadPolicy.Relief.SHELTER, 1.0F);
        float heat = CognitiveLoadPolicy.nextLoad(
                50.0F, 1.0F, true, CognitiveLoadPolicy.Relief.HEAT, 1.0F);
        float comfort = CognitiveLoadPolicy.nextLoad(
                50.0F, 1.0F, true, CognitiveLoadPolicy.Relief.COMFORT, 1.0F);
        assertTrue(comfort < heat);
        assertTrue(heat < shelter);
        assertTrue(shelter < 50.0F);
    }

    @Test
    void loadAlwaysClampsToValidRange() {
        assertEquals(100.0F, CognitiveLoadPolicy.nextLoad(
                100.0F, 1.0F, true, CognitiveLoadPolicy.Relief.NONE, 1.0F));
        assertEquals(0.0F, CognitiveLoadPolicy.nextLoad(
                0.0F, 0.0F, false, CognitiveLoadPolicy.Relief.NONE, 1.0F));
    }

    @Test
    void watcherCountsScaleWithoutGameplayEntities() {
        assertEquals(0, CognitiveLoadPolicy.watcherCount(24.999F));
        assertEquals(4, CognitiveLoadPolicy.watcherCount(25.0F));
        assertEquals(7, CognitiveLoadPolicy.watcherCount(50.0F));
        assertEquals(10, CognitiveLoadPolicy.watcherCount(75.0F));
        assertEquals(14, CognitiveLoadPolicy.watcherCount(90.0F));
    }

    @Test
    void interactionDelayMatchesTwoToThreeTenthsOfASecond() {
        assertEquals(0, CognitiveLoadPolicy.inputDelayTicks(74.999F));
        assertEquals(4, CognitiveLoadPolicy.inputDelayTicks(75.0F));
        assertEquals(5, CognitiveLoadPolicy.inputDelayTicks(87.5F));
        assertEquals(6, CognitiveLoadPolicy.inputDelayTicks(100.0F));
    }

    @Test
    void heartDescentIsSmoothAndBounded() {
        assertEquals(0.0F, CognitiveLoadPolicy.heartDescentBlocks(0.0F));
        assertEquals(9.0F, CognitiveLoadPolicy.heartDescentBlocks(50.0F), 0.0001F);
        assertEquals(CognitiveLoadPolicy.MAX_DESCENT_BLOCKS,
                CognitiveLoadPolicy.heartDescentBlocks(100.0F));
    }

    @Test
    void terminalDamageEscalatesTowardTheHeart() {
        assertEquals(0.0F, CognitiveLoadPolicy.terminalDamage(16.01D));
        assertEquals(2.0F, CognitiveLoadPolicy.terminalDamage(16.0D), 0.0001F);
        assertEquals(5.0F, CognitiveLoadPolicy.terminalDamage(8.0D), 0.0001F);
        assertEquals(8.0F, CognitiveLoadPolicy.terminalDamage(0.0D), 0.0001F);
    }

    @Test
    void breakoutRequiresSustainedMovementAwayFromTheHeart() {
        assertEquals(1.0F, CognitiveLoadPolicy.nextBreakoutTicks(
                0.0F, CognitiveLoadPolicy.BREAKOUT_RESISTANCE_THRESHOLD));
        assertEquals(8.5F, CognitiveLoadPolicy.nextBreakoutTicks(10.0F, 0.0F));
        assertEquals(0.0F, CognitiveLoadPolicy.nextBreakoutTicks(1.0F, 0.0F));
        assertEquals(CognitiveLoadPolicy.BREAKOUT_REQUIRED_TICKS,
                CognitiveLoadPolicy.nextBreakoutTicks(
                        CognitiveLoadPolicy.BREAKOUT_REQUIRED_TICKS, 1.0F));
    }

    @Test
    void successfulBreakoutReturnsBelowInputFailureThreshold() {
        assertTrue(CognitiveLoadPolicy.BREAKOUT_RELEASE_LOAD
                < CognitiveLoadPolicy.INPUT_DELAY_THRESHOLD);
        assertEquals(0.5F, CognitiveLoadPolicy.breakoutProgress(
                CognitiveLoadPolicy.BREAKOUT_REQUIRED_TICKS * 0.5F), 0.0001F);
    }
}
