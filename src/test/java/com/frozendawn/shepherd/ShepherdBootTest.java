package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ShepherdBootTest {
    @Test
    void aLineShowsEveryEightTicks() {
        assertEquals(1, ShepherdBoot.shownLines(0));
        assertEquals(2, ShepherdBoot.shownLines(8));
        assertEquals(11, ShepherdBoot.shownLines(87));
        assertEquals(11, ShepherdBoot.shownLines(500));
    }
}
