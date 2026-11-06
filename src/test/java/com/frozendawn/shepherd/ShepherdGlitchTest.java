package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.shepherd.ShepherdGlitch.Beat;
import com.frozendawn.shepherd.ShepherdGlitch.Cell;
import org.junit.jupiter.api.Test;

class ShepherdGlitchTest {
    private static final String PHRASE = "PLEASE REMAIN CA";
    private static final int NOISE = ShepherdPolicy.CRASH_NOISE_TICKS, LEAD = ShepherdPolicy.CRASH_LEAD_TICKS;

    @Test void theCrashHasThreeBeats() {
        assertEquals(Beat.NOISE, ShepherdGlitch.beat(0));
        assertEquals(Beat.NOISE, ShepherdGlitch.beat(NOISE - 1));
        assertEquals(Beat.STILL, ShepherdGlitch.beat(NOISE));
        assertEquals(Beat.STILL, ShepherdGlitch.beat(LEAD - 1));
        assertEquals(Beat.STOP, ShepherdGlitch.beat(LEAD));
        assertEquals(LEAD + ShepherdPolicy.CRASH_TICKS, ShepherdPolicy.CRASH_DOWN_TICKS);
    }

    @Test void herLineBreaksThenFills() {
        assertEquals("P", ShepherdGlitch.line(PHRASE, 0, 1));
        assertEquals(PHRASE, ShepherdGlitch.line(PHRASE, PHRASE.length() - 1, 1));
        String noise = ShepherdGlitch.line(PHRASE, NOISE - 1, 1);
        assertTrue(noise.startsWith(PHRASE), noise);
        assertTrue(noise.substring(PHRASE.length()).matches("[A-Z0-9]+"), noise);
        assertTrue(noise.length() > PHRASE.length() + 40, "It pours: " + noise.length());
        assertTrue(noise.length() <= ShepherdGlitch.ROW * ShepherdGlitch.ROWS, "It stays in the panel: " + noise.length());
        assertTrue(ShepherdGlitch.line(PHRASE, NOISE - 2, 1).length() <= noise.length(), "It only grows");
        assertNotEquals(noise, ShepherdGlitch.line(PHRASE, NOISE - 1, 2), "Not the same every time");
    }

    private static int wrong(int age) {
        int wrong = 0;
        for (int y = 0; y < ShepherdFace.HEIGHT; y++) {
            for (int x = 0; x < ShepherdFace.WIDTH; x++) {
                var cell = ShepherdGlitch.cell(age, 1, x, y);
                boolean neutral = ShepherdFace.Mood.NEUTRAL.lit(x, y) ? cell == Cell.LIT : ShepherdFace.Mood.NEUTRAL.dim(x, y) ? cell == Cell.DIM : cell == Cell.BLANK;
                if (!neutral) wrong++;
            }
        }
        return wrong;
    }

    @Test void herFaceComesApart() {
        assertTrue(wrong(0) <= 2, "It starts as hers: " + wrong(0));
        assertTrue(wrong(NOISE - 1) >= 14, "It ends as noise: " + wrong(NOISE - 1));
    }

    @Test void theTearsCoverThePanelsAndNothingElse() {
        for (int age = 0; age < NOISE; age++) {
            int at = 8;
            for (var tear : ShepherdGlitch.tears(age, 1, 8, 218)) {
                assertEquals(at, tear.top(), "No gaps");
                assertTrue(tear.bottom() > tear.top());
                assertTrue(Math.abs(tear.shift()) <= ShepherdGlitch.MAX_SHIFT);
                at = tear.bottom();
            }
            assertEquals(218, at, "To the bottom");
            for (var block : ShepherdGlitch.blocks(age, 1, 84, 8, 396, 218)) {
                assertTrue(block.x() >= 84 && block.x() + block.w() <= 396 && block.y() >= 8 && block.y() + block.h() <= 218, "Inside: " + block);
            }
        }
        assertTrue(ShepherdGlitch.tears(0, 1, 8, 218).stream().allMatch(tear -> tear.shift() == 0), "It starts whole");
        assertTrue(ShepherdGlitch.tears(NOISE - 1, 1, 8, 218).stream().filter(tear -> tear.shift() != 0).count() >= 4, "It ends torn");
    }

    @Test void theShakeGrowsAndStops() {
        float early = 0, late = 0;
        for (int age = 0; age < NOISE; age++) {
            float reach = Math.abs(ShepherdGlitch.shake(age, 0));
            assertTrue(reach <= 4 / 16F, "Age " + age);
            if (age < 10) early = Math.max(early, reach);
            if (age >= NOISE - 10) late = Math.max(late, reach);
        }
        assertTrue(late > early, late + " after " + early);
        assertEquals(0, ShepherdGlitch.shake(NOISE, 0));
        assertEquals(0, ShepherdGlitch.shake(LEAD + 5, 1));
    }
}
