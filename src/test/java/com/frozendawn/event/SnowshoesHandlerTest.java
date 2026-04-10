package com.frozendawn.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnowshoesHandlerTest {

    @Test
    void mapsShallowSnowLayersToTenPercentBoost() {
        assertEquals(0.12D, SnowshoesTuning.getSpeedBonusForLayers(1));
        assertEquals(0.12D, SnowshoesTuning.getSpeedBonusForLayers(3));
    }

    @Test
    void mapsMidSnowLayersToFifteenPercentBoost() {
        assertEquals(0.15D, SnowshoesTuning.getSpeedBonusForLayers(4));
        assertEquals(0.15D, SnowshoesTuning.getSpeedBonusForLayers(5));
    }

    @Test
    void mapsDeepSnowLayersToTwentyPercentBoost() {
        assertEquals(0.18D, SnowshoesTuning.getSpeedBonusForLayers(6));
        assertEquals(0.18D, SnowshoesTuning.getSpeedBonusForLayers(7));
    }

    @Test
    void assignsSnowBlocksTheirOwnBoostTier() {
        assertEquals(0.16D, SnowshoesTuning.getSpeedBonusForSnowBlock());
    }

    @Test
    void mapsImpulseToSpeedTier() {
        assertEquals(0.018D, SnowshoesTuning.getTravelImpulseForSpeedBonus(0.12D));
        assertEquals(0.023D, SnowshoesTuning.getTravelImpulseForSpeedBonus(0.16D));
        assertEquals(0.028D, SnowshoesTuning.getTravelImpulseForSpeedBonus(0.18D));
    }

    @Test
    void ignoresInvalidSnowLayerDepths() {
        assertEquals(0.0D, SnowshoesTuning.getSpeedBonusForLayers(0));
        assertEquals(0.0D, SnowshoesTuning.getSpeedBonusForLayers(-1));
    }
}
