package com.frozendawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AirStatusEtaPolicyTest {

    @Test
    void baselineEtaReflectsTankVisorAndModule() {
        assertEquals(120, estimate(1200, false, false));
        assertEquals(240, estimate(1200, true, false));
        assertEquals(160, estimate(1200, false, true));
        assertEquals(320, estimate(1200, true, true));
    }

    @Test
    void hearthrotMultipliersShortenBaselineEta() {
        assertEquals(80, AirStatusEtaPolicy.estimateSeconds(
                1200, 1200, true, 0, 80,
                false, false, 1.2D, 1.25D));
    }

    @Test
    void punctureEtaUsesCapacityVentAndModule() {
        assertEquals(40, AirStatusEtaPolicy.estimateSeconds(
                600, 1200, true, 1, 80,
                false, false, 1.0D, 1.0D));
        assertEquals(54, AirStatusEtaPolicy.estimateSeconds(
                600, 1200, true, 1, 80,
                false, true, 1.0D, 1.0D));
        assertEquals(20, AirStatusEtaPolicy.estimateSeconds(
                600, 1200, true, 2, 80,
                false, false, 1.0D, 1.0D));
    }

    @Test
    void standbyAndFormattingRemainExplicit() {
        assertEquals(AirStatusEtaPolicy.NO_ACTIVE_DRAIN,
                AirStatusEtaPolicy.estimateSeconds(
                        1200, 1200, false, 0, 80,
                        false, false, 1.0D, 1.0D));
        assertEquals("--:--", AirStatusEtaPolicy.format(-1));
        assertEquals("0:09", AirStatusEtaPolicy.format(9));
        assertEquals("12:05", AirStatusEtaPolicy.format(725));
    }

    private static int estimate(
            int totalO2, boolean visorRig, boolean efficiencyModule) {
        return AirStatusEtaPolicy.estimateSeconds(
                totalO2, totalO2, true, 0, 80,
                visorRig, efficiencyModule, 1.0D, 1.0D);
    }
}
