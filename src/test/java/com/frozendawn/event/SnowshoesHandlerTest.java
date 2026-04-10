package com.frozendawn.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnowshoesHandlerTest {

    @Test
    void mapsShallowSnowLayersToTenPercentBoost() {
        assertEquals(0.10D, SnowshoesTuning.getSpeedBonusForLayers(1));
        assertEquals(0.10D, SnowshoesTuning.getSpeedBonusForLayers(3));
    }

    @Test
    void mapsMidSnowLayersToFifteenPercentBoost() {
        assertEquals(0.15D, SnowshoesTuning.getSpeedBonusForLayers(4));
        assertEquals(0.15D, SnowshoesTuning.getSpeedBonusForLayers(5));
    }

    @Test
    void mapsDeepSnowLayersToTwentyPercentBoost() {
        assertEquals(0.20D, SnowshoesTuning.getSpeedBonusForLayers(6));
        assertEquals(0.20D, SnowshoesTuning.getSpeedBonusForLayers(7));
    }

    @Test
    void ignoresInvalidSnowLayerDepths() {
        assertEquals(0.0D, SnowshoesTuning.getSpeedBonusForLayers(0));
        assertEquals(0.0D, SnowshoesTuning.getSpeedBonusForLayers(-1));
    }
}
