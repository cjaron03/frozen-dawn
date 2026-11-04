package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.shepherd.ShepherdFace.Mood;
import org.junit.jupiter.api.Test;

/** Motion is free to change; these are the limits it has to stay inside. */
class ShepherdFaceTest {
    private static final int LONG_TIME = 20_000;

    private static int litCells(Mood mood, int shift) {
        int lit = 0;
        for (int x = 0; x < ShepherdFace.WIDTH; x++) {
            for (int y = 0; y < ShepherdFace.HEIGHT; y++) if (ShepherdFace.lit(mood, false, shift, x, y)) lit++;
        }
        return lit;
    }

    @Test
    void theRecordingAndOfflineNeverMove() {
        for (Mood mood : new Mood[] {Mood.RECORDING, Mood.OFFLINE}) {
            for (int age = 0; age < LONG_TIME; age++) {
                assertEquals(new ShepherdFace.Motion(0, false), ShepherdFace.motion(mood, age, 7), mood + " at " + age);
            }
        }
    }

    @Test
    void everyLivingMoodBlinksAndNeverSticksShut() {
        for (Mood mood : Mood.values()) {
            if (mood == Mood.RECORDING || mood == Mood.OFFLINE) continue;
            int shut = 0, blinks = 0;
            for (int age = 0; age < LONG_TIME; age++) {
                boolean blink = ShepherdFace.motion(mood, age, 7).blink();
                if (blink && shut == 0) blinks++;
                shut = blink ? shut + 1 : 0;
                assertTrue(shut <= 8, mood + " shut too long at " + age);
            }
            assertTrue(blinks > LONG_TIME / 200, mood + " blinked " + blinks + " times");
        }
    }

    @Test
    void eyesNeverSlideOffTheDisplay() {
        for (Mood mood : Mood.values()) {
            for (int age = 0; age < LONG_TIME; age++) {
                int shift = ShepherdFace.motion(mood, age, 7).eyeShift();
                assertEquals(litCells(mood, 0), litCells(mood, shift), mood + " at " + age);
            }
        }
    }

    @Test
    void sheLooksAroundWhenIdle() {
        boolean left = false, right = false;
        for (int age = 0; age < LONG_TIME; age++) {
            int shift = ShepherdFace.motion(Mood.NEUTRAL, age, 7).eyeShift();
            left |= shift < 0;
            right |= shift > 0;
        }
        assertTrue(left && right);
    }

    @Test
    void theFlameSputtersOutBrieflyAndOnlySometimes() {
        int dark = 0, run = 0, sputters = 0;
        for (int age = 0; age < LONG_TIME; age++) {
            int height = ShepherdFace.flameHeight(age, 7);
            assertTrue(height >= 0 && height <= ShepherdFace.FLAME_FRAMES);
            if (height == 0) {
                if (run == 0) sputters++;
                dark++;
                run++;
                assertTrue(run <= 6, "out too long at " + age);
            } else {
                run = 0;
            }
        }
        assertTrue(sputters > LONG_TIME / 400, "sputtered " + sputters + " times");
        assertTrue(dark < LONG_TIME / 10, "dark for " + dark + " ticks");
    }

    @Test
    void theFlameFlickersBetweenSizes() {
        boolean[] seen = new boolean[ShepherdFace.FLAME_FRAMES + 1];
        for (int age = 0; age < 400; age++) seen[ShepherdFace.flameHeight(age, 7)] = true;
        for (int height = 1; height <= ShepherdFace.FLAME_FRAMES; height++) assertTrue(seen[height], "never height " + height);
    }
}
