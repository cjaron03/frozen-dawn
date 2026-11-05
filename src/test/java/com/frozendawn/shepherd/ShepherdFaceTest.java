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

    @Test void neutralShowsEveryIdleExpressionAndOnlyBriefly() {
        var seen = java.util.EnumSet.noneOf(ShepherdFace.Idle.class);
        int run = 0;
        for (int age = 0; age < 20000; age++) {
            var idle = ShepherdFace.motion(Mood.NEUTRAL, age, 7).idle();
            run = idle == null ? 0 : run + 1;
            assertTrue(run <= 40, "An expression holds for a moment, not " + run);
            if (idle != null) seen.add(idle);
        }
        for (var idle : ShepherdFace.EXPRESSIONS) assertTrue(seen.contains(idle), "never " + idle);
    }

    @Test void idlesPlayOnEveryRestingMood() {
        for (Mood mood : Mood.values()) {
            if (mood == Mood.RECORDING || mood == Mood.OFFLINE) continue;
            var seen = java.util.EnumSet.noneOf(ShepherdFace.Idle.class);
            for (int seed = 0; seed < 40; seed++) for (int age = 0; age < 60000; age++) {
                var idle = ShepherdFace.motion(mood, age, seed, true).idle();
                if (idle != null) seen.add(idle);
            }
            assertTrue(seen.containsAll(ShepherdFace.EXPRESSIONS), mood + " is missing an expression: " + seen);
            assertTrue(seen.containsAll(ShepherdFace.ROLL), mood + " never rolls her eyes: " + seen);
        }
    }

    @Test void noIdlesOnRecordingOrOffline() {
        for (Mood mood : new Mood[] {Mood.RECORDING, Mood.OFFLINE}) {
            for (int age = 0; age < 20000; age++) assertEquals(new ShepherdFace.Motion(0, false), ShepherdFace.motion(mood, age, 7, true), mood + " at " + age);
        }
    }

    @Test void noIdlesOffNeutralWhenNotResting() {
        for (Mood mood : Mood.values()) {
            if (mood == Mood.NEUTRAL) continue;
            for (int age = 0; age < 20000; age++) assertTrue(ShepherdFace.motion(mood, age, 7, false).idle() == null, mood + " at " + age);
        }
    }

    @Test void neutralKeepsItsIdlesWhetherRestingOrNot() {
        for (int age = 0; age < 20000; age++) assertEquals(ShepherdFace.motion(Mood.NEUTRAL, age, 7), ShepherdFace.motion(Mood.NEUTRAL, age, 7, false));
    }

    @Test void onlyNeutralHasIdleExpressions() {
        for (Mood mood : Mood.values()) {
            if (mood == Mood.NEUTRAL) continue;
            for (int age = 0; age < 20000; age++) assertTrue(ShepherdFace.motion(mood, age, 7).idle() == null, mood + " stays " + mood);
        }
    }

    @Test void anExpressionHoldsHerEyesStill() {
        for (int age = 0; age < 20000; age++) {
            var motion = ShepherdFace.motion(Mood.NEUTRAL, age, 7);
            if (motion.idle() != null) assertTrue(!motion.blink() && motion.eyeShift() == 0, "at " + age);
        }
    }

    @Test void theEyeRollIsRareAndRunsInOrder() {
        int rolls = 0;
        var last = (ShepherdFace.Idle) null;
        for (int age = 0; age < 2400000; age++) {
            var idle = ShepherdFace.motion(Mood.NEUTRAL, age, 7).idle();
            if (idle == ShepherdFace.ROLL.getFirst() && last != idle) rolls++;
            if (idle != null && idle != last && ShepherdFace.ROLL.contains(idle) && last != null && ShepherdFace.ROLL.contains(last))
                assertTrue(ShepherdFace.ROLL.indexOf(idle) == ShepherdFace.ROLL.indexOf(last) + 1, "out of order at " + age);
            last = idle;
        }
        assertTrue(rolls >= 100 && rolls <= 400, "Roughly every ten minutes across 33 hours: " + rolls);
    }

    @Test void talkingShowsTheMoodNotTheExpression() {
        var motion = new ShepherdFace.Motion(0, false, ShepherdFace.Idle.WINK);
        assertTrue(motion.cells(Mood.NEUTRAL, true) == Mood.NEUTRAL);
        assertTrue(motion.cells(Mood.NEUTRAL, false) == ShepherdFace.Idle.WINK);
    }

    @Test void differentSeedsOpenDifferently() {
        int differ = 0;
        for (int seed = 1; seed < 40; seed++) {
            for (int age = 0; age < 48; age++) {
                if (!ShepherdFace.motion(Mood.NEUTRAL, age, seed).equals(ShepherdFace.motion(Mood.NEUTRAL, age, seed + 1))) {
                    differ++;
                    break;
                }
            }
        }
        assertTrue(differ > 30, "Only " + differ + " of 39 differ");
    }

    @Test void theLogoMovesItsMoonEveryFrame() {
        for (int frame = 0; frame < ShepherdFace.LOGO_FRAMES; frame++) {
            var now = ShepherdFace.logo(frame);
            var next = ShepherdFace.logo((frame + 1) % ShepherdFace.LOGO_FRAMES);
            boolean moved = false, shows = false;
            for (int x = 0; x < ShepherdFace.WIDTH; x++) {
                for (int y = 0; y < ShepherdFace.HEIGHT; y++) {
                    shows |= now.lit(x, y);
                    moved |= now.lit(x, y) != next.lit(x, y) || now.dim(x, y) != next.dim(x, y);
                }
            }
            assertTrue(shows && moved, "frame " + frame);
        }
    }

    @Test void theLogoFrameLoopsWithTheClock() {
        for (int age = 0; age < 200; age++) {
            int frame = ShepherdFace.logoFrame(age);
            assertTrue(frame >= 0 && frame < ShepherdFace.LOGO_FRAMES);
        }
        assertTrue(ShepherdFace.logoFrame(0) != ShepherdFace.logoFrame(ShepherdFace.LOGO_TICKS / 3));
    }

    private static final int HALF_LAP = ShepherdFace.thinkingTicks(1), WHOLE_LAP = ShepherdFace.thinkingTicks(2);
    /** The ticks of a wait spent going round, before the settle. */
    private static final int SETTLE = 8;

    private static boolean same(ShepherdFace.Cells a, ShepherdFace.Cells b) {
        for (int x = 0; x < ShepherdFace.WIDTH; x++) {
            for (int y = 0; y < ShepherdFace.HEIGHT; y++) {
                if (a.lit(x, y) != b.lit(x, y) || a.dim(x, y) != b.dim(x, y)) return false;
            }
        }
        return true;
    }

    private static int count(ShepherdFace.Cells cells, boolean lit) {
        int count = 0;
        for (int x = 0; x < ShepherdFace.WIDTH; x++) {
            for (int y = 0; y < ShepherdFace.HEIGHT; y++) if (lit ? cells.lit(x, y) : cells.dim(x, y)) count++;
        }
        return count;
    }

    @Test
    void theWaitComesInWholeHalfLaps() {
        assertEquals(0, ShepherdFace.thinkingTicks(0));
        assertEquals(28, HALF_LAP);
        assertEquals(44, WHOLE_LAP);
    }

    @Test
    void theIndicatorIsNoMood() {
        for (int ticks : new int[] {HALF_LAP, WHOLE_LAP}) {
            for (int age = 0; age < ticks; age++) {
                var frame = ShepherdFace.thinking(age, ticks);
                for (Mood mood : Mood.values()) assertFalse(same(frame, mood), mood + " at " + age);
                for (ShepherdFace.Idle idle : ShepherdFace.Idle.values()) assertFalse(same(frame, idle), idle + " at " + age);
            }
        }
    }

    @Test
    void theIndicatorIsTwoDots() {
        for (int age = 0; age < WHOLE_LAP - SETTLE; age++) {
            var frame = ShepherdFace.thinking(age, WHOLE_LAP);
            assertEquals(4, count(frame, true), "lit at " + age);
            assertEquals(4, count(frame, false), "dim at " + age);
        }
    }

    @Test
    void theIndicatorGoesRound() {
        var first = ShepherdFace.thinking(0, WHOLE_LAP);
        var half = ShepherdFace.thinking(4 * ShepherdFace.THINKING_FRAME_TICKS, WHOLE_LAP);
        for (int x = 0; x < ShepherdFace.WIDTH; x++) {
            for (int y = 0; y < ShepherdFace.HEIGHT; y++) {
                assertEquals(first.lit(x, y), half.dim(x, y), x + "," + y);
                assertEquals(first.dim(x, y), half.lit(x, y), x + "," + y);
            }
        }
    }

    @Test
    void theOrbitAlwaysEndsLevel() {
        for (int ticks : new int[] {HALF_LAP, WHOLE_LAP}) {
            var last = ShepherdFace.thinking(ticks - SETTLE - 1, ticks);
            for (int x = 0; x < ShepherdFace.WIDTH; x++) {
                for (int y = 0; y < ShepherdFace.HEIGHT; y++) {
                    boolean shown = last.lit(x, y) || last.dim(x, y);
                    assertEquals((y == 2 || y == 3) && (x == 1 || x == 2 || x == 5 || x == 6), shown, ticks + " at " + x + "," + y);
                }
            }
        }
    }

    @Test
    void herEyesRiseBeforeTheyLight() {
        for (int ticks : new int[] {HALF_LAP, WHOLE_LAP}) {
            var risen = ShepherdFace.thinking(ticks - SETTLE, ticks);
            assertEquals(4, count(risen, true));
            assertEquals(4, count(risen, false), "The dim one is still dim");
            for (int x = 0; x < ShepherdFace.WIDTH; x++) {
                assertFalse(risen.lit(x, 3) || risen.dim(x, 3), "Up a row at " + x);
            }
        }
    }

    @Test
    void herEyesComeHome() {
        for (int ticks : new int[] {HALF_LAP, WHOLE_LAP}) {
            var home = ShepherdFace.thinking(ticks - 1, ticks);
            for (int x = 0; x < ShepherdFace.WIDTH; x++) {
                for (int y = 0; y < ShepherdFace.HEIGHT; y++) {
                    assertEquals(Mood.NEUTRAL.lit(x, y), home.lit(x, y), "Her eyes at " + x + "," + y);
                    assertFalse(home.dim(x, y), "No mouth at " + x + "," + y);
                }
            }
        }
    }
}
