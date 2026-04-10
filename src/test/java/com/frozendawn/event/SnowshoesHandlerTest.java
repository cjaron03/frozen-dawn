package com.frozendawn.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnowshoesHandlerTest {

    @Test
    void mapsShallowSnowLayersToLightBoost() {
        assertEquals(0.10D, SnowshoesTuning.getSpeedBonusForLayers(1));
        assertEquals(0.10D, SnowshoesTuning.getSpeedBonusForLayers(3));
    }

    @Test
    void mapsMidSnowLayersToModerateBoost() {
        assertEquals(0.12D, SnowshoesTuning.getSpeedBonusForLayers(4));
        assertEquals(0.12D, SnowshoesTuning.getSpeedBonusForLayers(5));
    }

    @Test
    void mapsDeepSnowLayersToBestBoost() {
        assertEquals(0.14D, SnowshoesTuning.getSpeedBonusForLayers(6));
        assertEquals(0.14D, SnowshoesTuning.getSpeedBonusForLayers(7));
    }

    @Test
    void assignsSnowBlocksTheirOwnBoostTier() {
        assertEquals(0.12D, SnowshoesTuning.getSpeedBonusForSnowBlock());
    }

    @Test
    void mapsImpulseToSpeedTier() {
        assertEquals(0.012D, SnowshoesTuning.getTravelImpulseForSpeedBonus(0.10D));
        assertEquals(0.016D, SnowshoesTuning.getTravelImpulseForSpeedBonus(0.12D));
        assertEquals(0.019D, SnowshoesTuning.getTravelImpulseForSpeedBonus(0.14D));
    }

    @Test
    void ignoresInvalidSnowLayerDepths() {
        assertEquals(0.0D, SnowshoesTuning.getSpeedBonusForLayers(0));
        assertEquals(0.0D, SnowshoesTuning.getSpeedBonusForLayers(-1));
    }
}
