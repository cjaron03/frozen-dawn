package com.frozendawn.vision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisionModeResolverTest {

    @Test
    void prefersThermalWhenRequestedAndVisible() {
        assertEquals(VisionMode.THERMAL, VisionModeResolver.resolveActiveMode(true, true, VisionMode.THERMAL));
    }

    @Test
    void prefersBlizzardWhenRequestedAndVisible() {
        assertEquals(VisionMode.BLIZZARD, VisionModeResolver.resolveActiveMode(true, true, VisionMode.BLIZZARD));
    }

    @Test
    void fallsBackToBlizzardWhenOnlyBlizzardIsVisible() {
        assertEquals(VisionMode.BLIZZARD, VisionModeResolver.resolveActiveMode(false, true, VisionMode.NONE));
    }

    @Test
    void fallsBackToThermalWhenOnlyThermalIsVisible() {
        assertEquals(VisionMode.THERMAL, VisionModeResolver.resolveActiveMode(true, false, VisionMode.NONE));
    }

    @Test
    void resolvesNoneWhenNoVisionModeIsVisible() {
        assertEquals(VisionMode.NONE, VisionModeResolver.resolveActiveMode(false, false, VisionMode.NONE));
    }
}
