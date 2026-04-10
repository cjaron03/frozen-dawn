package com.frozendawn.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlizzardGogglesHandlerTest {

    @Test
    void activatesInPhaseFiveBlizzard() {
        assertTrue(BlizzardGogglesLogic.isVisionActive(5, 0.55F));
    }

    @Test
    void activatesDuringEarlyPhaseSixOnly() {
        assertTrue(BlizzardGogglesLogic.isVisionActive(6, 0.70F));
        assertTrue(BlizzardGogglesLogic.isVisionActive(6, 0.72F));
        assertFalse(BlizzardGogglesLogic.isVisionActive(6, 0.80F));
    }

    @Test
    void staysInactiveBeforeBlizzardPhases() {
        assertFalse(BlizzardGogglesLogic.isVisionActive(4, 0.45F));
    }
}
