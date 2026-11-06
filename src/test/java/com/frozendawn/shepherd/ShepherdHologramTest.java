package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShepherdHologramTest {
    private static final int LONG_ENOUGH = 2000, SKIN = 64;
    private static final int BEAM = ShepherdProjection.BEAM_TICKS, FORM = ShepherdHologram.FORM_TICKS;

    private static int formed(int age) {
        int count = 0;
        for (int x = 0; x < SKIN; x++) for (int y = 0; y < SKIN; y++) if (ShepherdHologram.formed(age, x, y)) count++;
        return count;
    }

    @Test
    void theHologramIsDeterministic() {
        boolean tearsMove = false;
        for (int age = 0; age < LONG_ENOUGH; age++) {
            assertEquals(ShepherdHologram.tearRow(age), ShepherdHologram.tearRow(age));
            assertEquals(ShepherdHologram.tearShift(age), ShepherdHologram.tearShift(age));
            assertEquals(ShepherdHologram.bars(age), ShepherdHologram.bars(age));
            assertEquals(ShepherdHologram.flecks(age), ShepherdHologram.flecks(age));
            tearsMove |= ShepherdHologram.tearRow(age) != ShepherdHologram.tearRow(0);
        }
        assertTrue(tearsMove, "it glitches, so it must change");
    }

    @Test
    void theArchitectStopsToThink() {
        List<Integer> spells = new ArrayList<>();
        int spell = 0;
        for (int age = 0; age < LONG_ENOUGH; age++) {
            if (ShepherdHologram.chin(age)) {
                spell++;
            } else if (spell > 0) {
                spells.add(spell);
                spell = 0;
            }
        }
        assertTrue(spells.size() > 3, "it comes back to it: " + spells);
        assertTrue(spells.stream().allMatch(ticks -> ticks >= 20), "and stays a second at least: " + spells);
        assertFalse(ShepherdHologram.chin(0), "it starts by standing");
    }

    @Test
    void tearsStayOnTheFigure() {
        for (int age = 0; age < LONG_ENOUGH; age++) {
            int top = ShepherdHologram.tearRow(age) + ShepherdHologram.tearWidth(age);
            assertTrue(ShepherdHologram.tearRow(age) >= 0 && top <= ShepherdHologram.ROWS, "row at " + age);
            assertTrue(Math.abs(ShepherdHologram.tearShift(age)) <= ShepherdHologram.MAX_SHIFT, "shift at " + age);
            assertTrue(ShepherdHologram.gapRow(age) >= 0 && ShepherdHologram.gapRow(age) + 1 < ShepherdHologram.ROWS, "gap at " + age);
            for (var bar : ShepherdHologram.bars(age)) assertTrue(bar.y() >= 0 && bar.y() <= ShepherdHologram.ROWS + 1 && bar.length() > 0, "bar at " + age);
        }
    }

    @Test
    void theSkinMapsOntoTheFigure() {
        assertEquals(15, ShepherdHologram.row(9, 8), "the top of the head");
        assertEquals(12, ShepherdHologram.row(9, 15), "the chin");
        assertEquals(11, ShepherdHologram.row(20, 20), "the top of the chest");
        assertEquals(6, ShepherdHologram.row(20, 31), "the bottom of the chest");
        assertEquals(11, ShepherdHologram.row(44, 20), "an arm hangs from the shoulder");
        assertEquals(5, ShepherdHologram.row(4, 20), "the top of a leg");
        assertEquals(0, ShepherdHologram.row(4, 31), "a foot");
        for (int x = 0; x < SKIN; x++) for (int y = 0; y < SKIN; y++) assertTrue(ShepherdHologram.row(x, y) >= 0 && ShepherdHologram.row(x, y) < ShepherdHologram.ROWS, x + "," + y);
    }

    @Test
    void theBeamComesBeforeTheFigure() {
        for (int age = 0; age < BEAM - 1; age++) {
            assertTrue(ShepherdHologram.beam(age) < 1, "still reaching at " + age);
            assertEquals(0, formed(age), "nothing yet at " + age);
        }
        assertEquals(1, ShepherdHologram.beam(BEAM - 1), "landed");
        assertTrue(ShepherdHologram.beam(1) > ShepherdHologram.beam(0), "it travels");
        assertEquals(1, ShepherdHologram.beam(LONG_ENOUGH), "and stays");
    }

    private static int goneCount(int leaving) {
        int gone = 0;
        for (int x = 0; x < SKIN; x++) {
            for (int y = 0; y < SKIN; y++) if (ShepherdHologram.gone(leaving, x, y)) gone++;
        }
        return gone;
    }

    @Test
    void theFigureFallsAwayEyesFirst() {
        assertEquals(0, goneCount(ShepherdHologram.STAYING), "nothing goes while it stays");
        assertTrue(ShepherdHologram.gone(0, 9, 10), "the eyes go first");
        int last = 0, steps = 0;
        for (int leaving = 0; leaving < ShepherdHologram.LEAVE_FADE_TICKS; leaving++) {
            int now = goneCount(leaving);
            assertTrue(now >= last, "a texel never comes back at " + leaving);
            if (now > last) steps++;
            last = now;
        }
        assertEquals(SKIN * SKIN, last, "every texel by the end");
        assertTrue(steps > ShepherdHologram.LEAVE_FADE_TICKS / 2, "a few at a time: " + steps);
    }

    @Test
    void theBeamDrawsBackAfterTheFigure() {
        assertEquals(1, ShepherdHologram.beamLeft(ShepherdHologram.LEAVE_FADE_TICKS - 1), "it stays until the figure has gone");
        assertTrue(ShepherdHologram.beamLeft(ShepherdHologram.LEAVE_FADE_TICKS) < 1, "then it starts back");
        assertEquals(0, ShepherdHologram.beamLeft(ShepherdHologram.LEAVE_TICKS - 1), "and it is home by the end");
    }

    @Test
    void theFigureFormsSlowlyAndCompletely() {
        int last = 0, steps = 0;
        for (int age = BEAM - 1; age < BEAM + FORM; age++) {
            int now = formed(age);
            assertTrue(now >= last, "a texel never goes back at " + age);
            if (now > last) steps++;
            last = now;
        }
        assertEquals(SKIN * SKIN, last, "every texel by the end");
        assertTrue(steps > FORM / 2, "a few at a time: " + steps);
        int half = formed(BEAM + FORM / 2);
        assertTrue(half > SKIN * SKIN / 4 && half < SKIN * SKIN * 3 / 4, "about half midway: " + half);
    }

    @Test
    void theEyesComeLast() {
        for (int age = 0; age < BEAM + FORM - 1; age++) assertFalse(ShepherdHologram.formed(age, 9, 10), "still shut at " + age);
        assertTrue(ShepherdHologram.formed(BEAM + FORM - 1, 9, 10), "open at the end");
        assertTrue(ShepherdHologram.eye(13, 11) && !ShepherdHologram.eye(11, 10), "two eyes, with a nose between");
    }

    @Test
    void theEggTearsInAndOut() {
        assertTrue(ShepherdHologram.eggTearing(0) && ShepherdHologram.eggTearing(ShepherdProjection.EGG_TICKS - 1), "a tear at each end");
        assertFalse(ShepherdHologram.eggTearing(ShepherdProjection.EGG_TICKS / 2), "and the shape alone between");
    }
}
