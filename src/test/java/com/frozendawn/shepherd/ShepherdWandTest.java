package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.frozendawn.defense.TerminusLog;
import com.frozendawn.shepherd.ShepherdWand.Where;
import org.junit.jupiter.api.Test;

/** Which of her log lines is true: she only quotes "Transfers logged: 1" while that is what the wand says. */
class ShepherdWandTest {
    @Test
    void theFoundWandIsQuotedWhereItIs() {
        assertEquals(Where.CRADLE, ShepherdWand.where(true, null, false));
        assertEquals(Where.HAND, ShepherdWand.where(false, TerminusLog.FOUND, false));
    }

    @Test
    void aWandThatHasLoggedMoreIsNotQuoted() {
        assertEquals(Where.OTHER, ShepherdWand.where(false, new TerminusLog(2, 2), false));
    }

    @Test
    void pastTheHeartTheLogReadsOtherwise() {
        assertEquals(Where.OTHER, ShepherdWand.where(true, null, true));
        assertEquals(Where.OTHER, ShepherdWand.where(false, TerminusLog.FOUND, true));
    }

    @Test
    void aWandNeitherHereNorHeldIsNotQuoted() {
        assertEquals(Where.OTHER, ShepherdWand.where(false, null, false));
    }
}
