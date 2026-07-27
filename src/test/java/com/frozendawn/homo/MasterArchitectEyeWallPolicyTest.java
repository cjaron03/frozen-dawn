package com.frozendawn.homo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MasterArchitectEyeWallPolicyTest {
    @Test
    void agitatedAndFightUseTheSameEyeGeometry() {
        assertFalse(MasterArchitectEyeWallPolicy.isVisible(
                MasterArchitectAuraTier.PASSIVE, 0, 0.0F));
        assertTrue(MasterArchitectEyeWallPolicy.isVisible(
                MasterArchitectAuraTier.NOTICED, 0, 0.0F));
        assertTrue(MasterArchitectEyeWallPolicy.isVisible(
                MasterArchitectAuraTier.FIGHT, 0, 0.0F));
        assertEquals(
                MasterArchitectEyeWallPolicy.radius(
                        MasterArchitectAuraTier.NOTICED, 0, 0.0F),
                MasterArchitectEyeWallPolicy.radius(
                        MasterArchitectAuraTier.FIGHT, 0, 0.0F),
                0.0001F);
        assertEquals(
                MasterArchitectEyeWallPolicy.height(MasterArchitectAuraTier.NOTICED),
                MasterArchitectEyeWallPolicy.height(MasterArchitectAuraTier.FIGHT),
                0.0001F);
    }

    @Test
    void deathPullsTheEyeInwardBeforeRupture() {
        float strength = 1.0F;
        MasterArchitectStormAftermathPolicy.Timeline timeline =
                MasterArchitectStormAftermathPolicy.timeline(strength);
        float initialRadius = MasterArchitectEyeWallPolicy.radius(
                MasterArchitectAuraTier.FIGHT, timeline.coreEndTick(), strength);
        float lateRadius = MasterArchitectEyeWallPolicy.radius(
                MasterArchitectAuraTier.FIGHT, timeline.eyeEndTick() - 1, strength);

        assertTrue(lateRadius < initialRadius * 0.25F);
        assertFalse(MasterArchitectEyeWallPolicy.isVisible(
                MasterArchitectAuraTier.FIGHT, timeline.eyeEndTick(), strength));
    }

    @Test
    void hostileStormStaysInsideTheEyeWall() {
        float center = MasterArchitectEyeWallPolicy.localStormFactor(
                0.0D, MasterArchitectAuraTier.FIGHT, 0, 0.0F);
        float outside = MasterArchitectEyeWallPolicy.localStormFactor(
                40.0D, MasterArchitectAuraTier.FIGHT, 0, 0.0F);

        assertEquals(1.0F, center, 0.0001F);
        assertEquals(0.0F, outside, 0.0001F);
    }

    @Test
    void hostileFogLeavesTheOpenSkyVisibleThroughLightHaze() {
        assertEquals(1.0F,
                MasterArchitectEyeWallPolicy.directionalFogFactor(0.0F),
                0.0001F);
        assertEquals(0.18F,
                MasterArchitectEyeWallPolicy.directionalFogFactor(-90.0F),
                0.0001F);
        assertTrue(MasterArchitectEyeWallPolicy.directionalFogFactor(-45.0F)
                < 1.0F);
        assertTrue(MasterArchitectEyeWallPolicy.directionalFogFactor(-45.0F)
                > 0.18F);
    }

    @Test
    void lodBandsCrossFadeWithoutCoverageGaps() {
        assertEquals(1.0F, MasterArchitectEyeWallPolicy.nearParticleWeight(0.0D), 0.0001F);
        assertEquals(1.0F, MasterArchitectEyeWallPolicy.midRenderWeight(80.0D), 0.0001F);
        assertEquals(1.0F, MasterArchitectEyeWallPolicy.distantRenderWeight(150.0D), 0.0001F);

        double nearTransition = 56.0D;
        assertEquals(1.0F,
                MasterArchitectEyeWallPolicy.nearParticleWeight(nearTransition)
                        + MasterArchitectEyeWallPolicy.midRenderWeight(nearTransition),
                0.0001F);
        double farTransition = 116.0D;
        assertEquals(1.0F,
                MasterArchitectEyeWallPolicy.midRenderWeight(farTransition)
                        + MasterArchitectEyeWallPolicy.distantRenderWeight(farTransition),
                0.0001F);
    }

    @Test
    void sparseObserverFlakesBridgeTheBatchedWallAtMidDistance() {
        assertEquals(1.0F,
                MasterArchitectEyeWallPolicy.observerParticleWeight(48.0D),
                0.0001F);
        assertTrue(MasterArchitectEyeWallPolicy.observerParticleWeight(96.0D) > 0.0F);
        assertEquals(0.0F,
                MasterArchitectEyeWallPolicy.observerParticleWeight(176.0D),
                0.0001F);
    }

    @Test
    void batchedWallCoversTheNearParticleWarmup() {
        assertEquals(1.0F,
                MasterArchitectEyeWallPolicy.batchedRenderWeight(20.0D, 0.0F),
                0.0001F);
        assertEquals(0.775F,
                MasterArchitectEyeWallPolicy.batchedRenderWeight(20.0D, 0.5F),
                0.0001F);
        assertEquals(0.55F,
                MasterArchitectEyeWallPolicy.batchedRenderWeight(20.0D, 1.0F),
                0.0001F);
        assertEquals(1.0F,
                MasterArchitectEyeWallPolicy.batchedRenderWeight(80.0D, 1.0F),
                0.0001F);
    }
}
