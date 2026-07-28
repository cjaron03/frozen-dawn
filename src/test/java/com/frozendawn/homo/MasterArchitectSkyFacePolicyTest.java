package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterArchitectSkyFacePolicyTest {
    @Test
    void faceEscalatesFromHiddenToHostileAndFightStates() {
        assertEquals(0.0F, MasterArchitectSkyFacePolicy.targetFeatureOpacity(
                MasterArchitectAuraTier.PASSIVE, false, false));
        assertTrue(MasterArchitectSkyFacePolicy.targetFeatureOpacity(
                MasterArchitectAuraTier.NOTICED, false, false) < 0.25F);
        assertTrue(MasterArchitectSkyFacePolicy.targetFeatureOpacity(
                MasterArchitectAuraTier.FIGHT, false, true) > 0.8F);
        assertEquals(1.0F, MasterArchitectSkyFacePolicy.targetFeatureOpacity(
                MasterArchitectAuraTier.FIGHT, true, true));
    }

    @Test
    void crossingTheEyeDissolvesTheBeacon() {
        assertEquals(0.0F, MasterArchitectSkyFacePolicy.proximityVisibility(28.0D));
        assertTrue(MasterArchitectSkyFacePolicy.proximityVisibility(34.0D) > 0.0F);
        assertEquals(1.0F, MasterArchitectSkyFacePolicy.proximityVisibility(40.0D));
    }

    @Test
    void distantBeaconRecedesAndFadesAtItsRangeBoundary() {
        double anchor = MasterArchitectSkyFacePolicy.DISTANT_RENDER_ANCHOR;
        float anchorSize = MasterArchitectSkyFacePolicy.apparentSize(
                anchor, anchor, 1.0F);
        float distantSize = MasterArchitectSkyFacePolicy.apparentSize(
                2000.0D, anchor, 1.0F);

        assertTrue(distantSize < anchorSize * 0.4F);
        assertEquals(anchor,
                MasterArchitectSkyFacePolicy.renderedHorizontalDistance(2000.0D),
                0.0001D);
        assertEquals(1.0F,
                MasterArchitectSkyFacePolicy.rangeVisibility(2000.0D, 2500.0D),
                0.0001F);
        assertTrue(MasterArchitectSkyFacePolicy.rangeVisibility(2250.0D, 2500.0D)
                < 1.0F);
        assertEquals(0.0F,
                MasterArchitectSkyFacePolicy.rangeVisibility(2500.0D, 2500.0D),
                0.0001F);
    }

    @Test
    void aftermathBucklesTearsAndDropsTheFace() {
        var timeline = MasterArchitectStormAftermathPolicy.timeline(1.0F);
        var buckle = MasterArchitectSkyFacePolicy.aftermath(
                timeline.coreEndTick() + 1, 1.0F);
        var rupture = MasterArchitectSkyFacePolicy.aftermath(
                timeline.eyeEndTick() + 20, 1.0F);
        var collapse = MasterArchitectSkyFacePolicy.aftermath(
                timeline.ruptureEndTick() + 20, 1.0F);

        assertTrue(buckle.distortion() > 0.0F);
        assertTrue(rupture.tearProgress() > 0.0F);
        assertTrue(collapse.verticalOffset() < rupture.verticalOffset());
    }

    @Test
    void massacreBranchFadesWithoutTearing() {
        var fade = MasterArchitectSkyFacePolicy.aftermath(100, 0.0F);
        assertTrue(fade.opacity() < 1.0F);
        assertEquals(0.0F, fade.tearProgress());
    }
}
