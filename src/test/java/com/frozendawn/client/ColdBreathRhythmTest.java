package com.frozendawn.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ColdBreathRhythmTest {
    @Test void sustainedSprintMakesExhalesMoreFrequentAndSlightlyFuller() {
        List<Integer> resting = burstStarts(false);
        List<Integer> running = burstStarts(true);
        float ratio = averageInterval(resting) / averageInterval(running);
        assertTrue(ratio > 1.25F && ratio < 1.35F, "Sprinting should add about 30% cadence");
        assertEquals(ColdBreathPolicy.wisps(-15) + 1, ColdBreathPolicy.wisps(-15, 1));
        assertEquals(ColdBreathPolicy.wisps(-15), ColdBreathPolicy.wisps(-15, 0));
    }

    @Test void exertionBuildsGraduallyAndRecoversOverFourSecondsWithoutRestartingBreath() {
        ColdBreathRhythm rhythm = new ColdBreathRhythm();
        for (int i = 0; i < 10; i++) rhythm.tick(-15, true, true);
        assertEquals(.5F, rhythm.exertion(), .001F);
        for (int i = 0; i < 10; i++) rhythm.tick(-15, true, true);
        assertEquals(1, rhythm.exertion());
        int recoveryWisps = 0;
        for (int i = 0; i < 40; i++) if (rhythm.tick(-15, true, false)) recoveryWisps++;
        assertEquals(.5F, rhythm.exertion(), .001F);
        for (int i = 0; i < 40; i++) if (rhythm.tick(-15, true, false)) recoveryWisps++;
        assertEquals(0, rhythm.exertion());
        assertTrue(recoveryWisps > 0, "Normal breath continues during recovery");
        for (int i = 0; i < 200; i++) rhythm.tick(-15, true, false);
        assertEquals(0, rhythm.exertion());
    }

    @Test void suppressionAndLogoutClearExertionAndPendingWisps() {
        ColdBreathRhythm rhythm = new ColdBreathRhythm();
        for (int i = 0; i < 100; i++) rhythm.tick(-15, true, true);
        for (int i = 0; i < 100; i++) assertFalse(rhythm.tick(10, false, true));
        assertEquals(0, rhythm.exertion());
        assertTrue(rhythm.tick(-15, true, false));
        rhythm.tick(-15, true, true);
        rhythm.reset();
        assertEquals(0, rhythm.exertion());
        assertFalse(rhythm.tick(-15, false, false));
        assertTrue(rhythm.tick(-15, true, false));
    }

    private static List<Integer> burstStarts(boolean running) {
        ColdBreathRhythm rhythm = new ColdBreathRhythm();
        List<Integer> starts = new ArrayList<>();
        int lastWisp = -10;
        for (int tick = 0; tick < 1200; tick++) {
            if (rhythm.tick(-15, true, running)) {
                if (tick - lastWisp > 3 && tick > 120) starts.add(tick);
                lastWisp = tick;
            }
        }
        return starts;
    }

    private static float averageInterval(List<Integer> starts) {
        assertTrue(starts.size() > 10);
        return (starts.getLast() - starts.getFirst()) / (float) (starts.size() - 1);
    }
}
