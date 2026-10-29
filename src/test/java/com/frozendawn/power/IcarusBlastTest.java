package com.frozendawn.power;

import static com.frozendawn.power.IcarusPhysics.CAPSULE;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IcarusBlastTest {
    @Test void radiusScalesByCubeRoot() {
        assertEquals(400, IcarusBlast.radius(64 * CAPSULE), 1e-9);
        assertEquals(125.99, IcarusBlast.radius(2 * CAPSULE), 0.01);
        assertEquals(0, IcarusBlast.radius(0), 1e-12);
        assertEquals(8, IcarusBlast.radius(512), 1e-6);
    }

    @Test void craterIsDeepAtTheCentreAndRaggedAtTheRim() {
        long seed = 42;
        assertEquals(80, IcarusBlast.depth(0, 400, seed, 0, 0));
        double min = Double.MAX_VALUE, max = 0;
        for (int i = 0; i < 720; i++) {
            double angle = i * Math.PI / 360, edge = IcarusBlast.edge(400, seed, angle);
            assertTrue(edge >= 360 && edge <= 400, "edge " + edge);
            min = Math.min(min, edge); max = Math.max(max, edge);
            int x = (int) Math.round(Math.cos(angle) * 340), z = (int) Math.round(Math.sin(angle) * 340);
            assertTrue(IcarusBlast.depth(Math.hypot(x, z), 400, seed, x, z) >= 4);
            int ox = (int) Math.round(Math.cos(angle) * 401), oz = (int) Math.round(Math.sin(angle) * 401);
            assertEquals(0, IcarusBlast.depth(Math.hypot(ox, oz), 400, seed, ox, oz));
        }
        assertTrue(max - min > 10, "The rim is ragged, not a circle");
        assertEquals(IcarusBlast.depth(200, 400, seed, 120, 160), IcarusBlast.depth(200, 400, seed, 120, 160));
        for (int i = 0; i < 100; i++) {
            int rim = IcarusBlast.rimDepth(seed, i * 7, i * 13);
            assertTrue(rim >= 4 && rim <= 7);
        }
    }

    @Test void flashFadesWithDistanceAndReducedFlashIsCapped() {
        assertEquals(1.0, IcarusBlast.flashStrength(0, 100, false), 1e-12);
        assertTrue(IcarusBlast.flashStrength(600, 100, false) >= 0.15, "Still a real flash at six radii");
        double last = 1;
        for (int d = 0; d <= 2000; d += 10) {
            double full = IcarusBlast.flashStrength(d, 100, false), reduced = IcarusBlast.flashStrength(d, 100, true);
            assertTrue(full <= last + 1e-12, "The flash never brightens with distance, at " + d);
            assertTrue(full >= 0 && reduced <= 0.35 + 1e-12 && reduced <= full + 1e-12, "Reduced flash is capped at 35%, at " + d);
            last = full;
        }
        assertEquals(0.35, IcarusBlast.flashStrength(0, 100, true), 1e-12);
    }

    @Test void staringIntoTheBlastIsBrighterAndReducedFlashStillCaps() {
        double far = IcarusBlast.flashStrength(600, 100, false), near = IcarusBlast.flashStrength(200, 100, false);
        assertEquals(far, IcarusBlast.gazeFlash(far, 0, false), 1e-12);
        assertEquals(far, IcarusBlast.gazeFlash(far, -1, false), 1e-12);
        assertTrue(IcarusBlast.gazeFlash(far, 1, false) >= 2 * far, "Staring at six radii is far brighter than looking away");
        assertEquals(1, IcarusBlast.gazeFlash(near, 1, false), 1e-12);
        assertTrue(IcarusBlast.gazeFlash(near, 0.5, false) < IcarusBlast.gazeFlash(near, 0.9, false));
        for (double f = -1; f <= 1; f += 0.05) assertTrue(IcarusBlast.gazeFlash(1, f, true) <= 0.35 + 1e-12);
    }

    @Test void ringingIsLongerCloserAndNeverInVacuum() {
        assertEquals(0, IcarusBlast.ringingSeconds(200, 100, true), 1e-12);
        assertEquals(0, IcarusBlast.ringingSeconds(301, 100, false), 1e-12);
        assertTrue(IcarusBlast.ringingSeconds(150, 100, false) > IcarusBlast.ringingSeconds(250, 100, false));
        assertTrue(IcarusBlast.ringingSeconds(250, 100, false) > 0);
        assertEquals(IcarusBlast.MAX_RINGING_SECONDS, IcarusBlast.ringingSeconds(50, 100, false), 1e-12);
        assertTrue(IcarusBlast.MAX_RINGING_SECONDS <= 12);
    }

    @Test void burstDoseFadesToNothingAtTwoRadii() {
        assertEquals(IcarusBlast.BURST_DOSE / 2, IcarusBlast.burstDose(100, 100), 1e-9);
        assertEquals(0, IcarusBlast.burstDose(200, 100), 1e-12);
        assertEquals(0, IcarusBlast.burstDose(900, 100), 1e-12);
    }

    @Test void meltRingIsOneAndAHalfRadii() { assertEquals(300, IcarusBlast.meltRadius(200), 1e-12); }

    @Test void boomTravelsAtTheSpeedOfSound() { assertEquals(2.0, IcarusBlast.soundDelaySeconds(686), 1e-12); }

    @Test void damageFallsOffOutsideTheRadius() {
        assertEquals(Float.MAX_VALUE, IcarusBlast.damage(50, 100));
        assertEquals(20, IcarusBlast.damage(150, 100), 1e-4);
        assertEquals(0, IcarusBlast.damage(200, 100));
        assertEquals(0, IcarusBlast.damage(500, 100));
        assertEquals(4, IcarusBlast.knockback(100.0001, 100), 1e-3);
        assertEquals(0, IcarusBlast.knockback(50, 100));
        assertEquals(0, IcarusBlast.knockback(200, 100), 1e-9);
    }

    @Test void witnessesStandOutsideTheRadius() {
        assertFalse(IcarusBlast.witness(100, 100));
        assertTrue(IcarusBlast.witness(200, 100));
        assertFalse(IcarusBlast.witness(301, 100));
    }
}
