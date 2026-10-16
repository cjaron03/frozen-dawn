package com.frozendawn.radiation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RadiationMathTest {
    @Test void stagesFollowTheNotionTable() {
        assertEquals(0, RadiationMath.stage(99));
        assertEquals(1, RadiationMath.stage(100));
        assertEquals(2, RadiationMath.stage(300));
        assertEquals(3, RadiationMath.stage(600));
        assertEquals(4, RadiationMath.stage(1000));
    }

    @Test void bareSpentRodReachesSicknessThreeInAboutTenSeconds() {
        double dose = 0; int seconds = 0;
        while (RadiationMath.stage(dose) < 3) { dose = RadiationMath.nextDose(dose, RadiationMath.rate(RadiationMath.SPENT_ROD, 1, 1), 0); seconds++; }
        assertTrue(seconds >= 8 && seconds <= 12, "seconds " + seconds);
    }

    @Test void caskAndDistanceShield() {
        double bare = RadiationMath.rate(RadiationMath.SPENT_ROD, 1, 1);
        assertEquals(bare * 0.01, RadiationMath.rate(RadiationMath.SPENT_ROD, 1, RadiationMath.CASK_SHIELD), 1e-12);
        assertEquals(bare / 16, RadiationMath.rate(RadiationMath.SPENT_ROD, 4, 1), 1e-12);
        assertEquals(bare, RadiationMath.rate(RadiationMath.SPENT_ROD, 0.2, 1), 1e-12, "Never stronger than at one block");
    }

    @Test void recoveryIsAboutOneUnitPerTenSecondsAndNeverNegative() {
        assertEquals(99.9, RadiationMath.nextDose(100, 0, 0), 1e-9);
        assertEquals(0, RadiationMath.nextDose(0.05, 0, 0));
        assertEquals(96.6, RadiationMath.nextDose(100, 0, 3.3), 1e-9, "Anti-Rad treatment speeds recovery");
    }

    @Test void symptomsLagTheDose() {
        double felt = 0;
        for (int i = 0; i < 10; i++) felt = RadiationMath.nextFelt(felt, 1000);
        assertTrue(felt > 200 && felt < 400, "Ten seconds in, symptoms trail the real dose: " + felt);
        for (int i = 0; i < 300; i++) felt = RadiationMath.nextFelt(felt, 1000);
        assertEquals(1000, felt, 1);
    }

    @Test void geigerIsSilentAtBackgroundAndSaturates() {
        assertEquals(0, RadiationMath.clicksPerSecond(0.01));
        assertTrue(RadiationMath.clicksPerSecond(0.1) > 1);
        assertEquals(40, RadiationMath.clicksPerSecond(10_000));
    }
}
