package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.shepherd.ShepherdFace.Mood;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
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

    private static String rows(ShepherdFace.Cells cells) {
        var out = new StringBuilder();
        for (int y = 0; y < ShepherdFace.HEIGHT; y++) {
            for (int x = 0; x < ShepherdFace.WIDTH; x++) out.append(cells.lit(x, y) ? '#' : cells.dim(x, y) ? '+' : '.');
            out.append('/');
        }
        return out.toString();
    }

    private static final Set<Mood> NO_EYES_TO_SHUT = EnumSet.of(Mood.RECORDING, Mood.OFFLINE, Mood.BEAMING, Mood.FLUSTERED, Mood.WORRIED);
    private static final String FLAT = "......../......../......../++++++++/......../......../";

    @Test
    void theNewMoodsMatchTheMockup() {
        assertEquals("......../.#....#./#.#..#.#/......../.++++++./..++++../", rows(Mood.BEAMING));
        assertEquals("......../.#....#./..#..#../.#....#./......../...++.../", rows(Mood.FLUSTERED));
        assertEquals("#......#/.##..##./.##..##./......../..++++../......../", rows(Mood.GRIM));
        assertEquals("......../++..++../##..##../......../...++.../......../", rows(Mood.WORRIED));
        assertEquals(FLAT, rows(Mood.RECORDING));
    }

    @Test
    void theOldMoodsKeepTheirPlaceOnTheWire() {
        assertEquals(8, Mood.RECORDING.ordinal());
    }

    @Test
    void beamingAndFlusteredHoldStill() {
        for (Mood mood : new Mood[] {Mood.BEAMING, Mood.FLUSTERED, Mood.WORRIED}) {
            for (int age = 0; age < LONG_TIME; age++) assertEquals(new ShepherdFace.Motion(0, false), ShepherdFace.motion(mood, age, 7, true), mood + " at " + age);
        }
    }

    @Test
    void theTraceRestsFlatAndMovesOnlyWhileSpeaking() {
        var frames = new HashSet<String>();
        for (int age = 0; age < 200; age++) {
            assertEquals(FLAT, rows(ShepherdFace.voice(Mood.RECORDING, false, age)), "at rest at " + age);
            frames.add(rows(ShepherdFace.voice(Mood.RECORDING, true, age)));
            assertEquals(rows(ShepherdFace.trace(age / 4 * 4)), rows(ShepherdFace.trace(age)), "four ticks a frame at " + age);
        }
        assertEquals(6, frames.size());
        assertFalse(frames.contains(FLAT));
    }

    @Test
    void otherFacesAreLeftAloneBySpeaking() {
        assertSame(Mood.GRIM, ShepherdFace.voice(Mood.GRIM, true, 5));
    }

    @Test
    void theTraceHasNoMouthToMove() {
        for (int age = 0; age < 24; age++) {
            for (int x = 0; x < ShepherdFace.WIDTH; x++) assertEquals(ShepherdFace.trace(age).dim(x, 5), ShepherdFace.dim(ShepherdFace.trace(age), 2, x, 5), "at " + age);
        }
    }

    @Test
    void theStripTraceStopsWhenTheLineHasTyped() {
        int length = 12, typed = length * ShepherdPolicy.TICKS_PER_CHAR;
        assertNotEquals(FLAT, rows(ShepherdFace.stripFace(Mood.RECORDING, 1, length)));
        assertEquals(FLAT, rows(ShepherdFace.stripFace(Mood.RECORDING, typed, length)));
        assertEquals(FLAT, rows(ShepherdFace.stripFace(Mood.RECORDING, typed + 40, length)));
        assertSame(Mood.SAD, ShepherdFace.stripFace(Mood.SAD, 1, length));
    }

    @Test
    void worryComesBackToNeutral() {
        var neutral = rows(Mood.NEUTRAL);
        assertEquals(neutral, rows(ShepherdFace.worry(0)));
        assertEquals(neutral, rows(ShepherdFace.worry(13)));
        assertEquals(rows(ShepherdFace.Idle.HEAVY_LIDS), rows(ShepherdFace.worry(14)));
        assertEquals("......../.++..++./.##..##./......../...++.../......../", rows(ShepherdFace.worry(24)));
        assertEquals(rows(Mood.WORRIED), rows(ShepherdFace.worry(30)));
        assertEquals(rows(Mood.WORRIED), rows(ShepherdFace.worry(61)));
        assertEquals(rows(ShepherdFace.Idle.HEAVY_LIDS), rows(ShepherdFace.worry(ShepherdFace.WORRY_TICKS - 1)));
        assertEquals(neutral, rows(ShepherdFace.worry(ShepherdFace.WORRY_TICKS)));
        assertEquals(82, ShepherdFace.WORRY_TICKS);
    }

    @Test
    void everyLivingMoodBlinksAndNeverSticksShut() {
        for (Mood mood : Mood.values()) {
            if (NO_EYES_TO_SHUT.contains(mood)) continue;
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
            if (NO_EYES_TO_SHUT.contains(mood)) continue;
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

    /** Display B from crash-mockup.png: the mark over a row of writing-shaped blue. */
    @Test
    void theStopFaceMatchesTheMockup() {
        String[] rows = {".#...#..", "....#...", "....#...", ".#...#..", "........", ".++++++."};
        for (int y = 0; y < ShepherdFace.HEIGHT; y++) {
            for (int x = 0; x < ShepherdFace.WIDTH; x++) {
                assertEquals(rows[y].charAt(x) == '#', ShepherdFace.STOP.lit(x, y), x + "," + y);
                assertEquals(rows[y].charAt(x) == '+', ShepherdFace.STOP.dim(x, y), x + "," + y);
            }
        }
    }

    @Test
    void theDropEasesDownAndHolds() {
        assertEquals(0, ShepherdFace.crashDrop(0));
        float last = 0;
        for (int age = 1; age <= ShepherdPolicy.CRASH_DROP_TICKS; age++) {
            float drop = ShepherdFace.crashDrop(age);
            assertTrue(drop > last, "Down at " + age);
            last = drop;
        }
        assertEquals(ShepherdPolicy.CRASH_DROP_BLOCKS, last);
        assertTrue(ShepherdFace.crashDrop(1) > ShepherdPolicy.CRASH_DROP_BLOCKS / ShepherdPolicy.CRASH_DROP_TICKS, "It falls fastest at first");
        assertEquals(ShepherdPolicy.CRASH_DROP_BLOCKS, ShepherdFace.crashDrop(ShepherdPolicy.CRASH_TICKS));
    }

    @Test
    void theDropRisesThroughTheLogo() {
        float last = ShepherdFace.crashDrop(ShepherdPolicy.CRASH_TICKS);
        for (int age = 1; age <= ShepherdFace.LOGO_TICKS; age++) {
            float drop = ShepherdFace.crashDrop(ShepherdPolicy.CRASH_TICKS + age);
            assertTrue(drop < last, "Up at " + age);
            last = drop;
        }
        assertEquals(0, last);
        assertEquals(0, ShepherdFace.crashDrop(ShepherdPolicy.CRASH_TICKS + ShepherdFace.LOGO_TICKS + 10));
    }

    @Test
    void everyStopRowHasGlyphs() throws java.io.IOException {
        try (var in = ShepherdFaceTest.class.getResourceAsStream("/assets/frozendawn/lang/en_us.json")) {
            var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(5, ShepherdFace.STOP_ROWS.size());
            for (String key : ShepherdFace.STOP_ROWS) {
                String row = lang.get(key).getAsString();
                assertTrue(row.length() * ShepherdFace.GLYPH_STEP <= ShepherdFace.WIDTH * 10, row + " fits the panel");
                for (char c : row.toCharArray()) assertEquals(ShepherdFace.GLYPH_ROWS, ShepherdFace.glyph(c).size(), row + ": " + c);
            }
        }
    }

    @Test
    void theFlusterSpinsAwayAndPeeks() {
        assertEquals(0, ShepherdFace.fluster(0).yaw(), "you catch her first");
        assertEquals(rows(Mood.FLUSTERED), rows(ShepherdFace.fluster(0).face()));
        assertEquals(180, ShepherdFace.fluster(30).yaw(), "she shows you her back");
        assertEquals(62, ShepherdFace.fluster(58).yaw(), "she peeks");
        assertEquals(rows(ShepherdFace.Idle.WINK), rows(ShepherdFace.fluster(58).face()), "with the Wink");
        assertEquals(0, ShepherdFace.fluster(ShepherdFace.FLUSTER_TICKS - 1).yaw(), "and carries on");
        assertEquals(rows(Mood.NEUTRAL), rows(ShepherdFace.fluster(ShepherdFace.FLUSTER_TICKS - 1).face()));
        assertEquals(106, ShepherdFace.FLUSTER_TICKS);
    }
}
