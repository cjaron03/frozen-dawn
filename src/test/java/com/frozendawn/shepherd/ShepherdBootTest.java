package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShepherdBootTest {
    @Test
    void aLineShowsEveryEightTicks() {
        assertEquals(1, ShepherdBoot.shownLines(0));
        assertEquals(2, ShepherdBoot.shownLines(8));
        assertEquals(11, ShepherdBoot.shownLines(87));
        assertEquals(11, ShepherdBoot.shownLines(500));
    }

    @Test
    void theLinesHoldTheScreenUntilTheyAreLongOverdue() {
        assertFalse(ShepherdBoot.stuck(ShepherdBoot.BIOS_TICKS));
        assertTrue(ShepherdBoot.stuck(ShepherdBoot.BIOS_TICKS * 3));
    }
}
