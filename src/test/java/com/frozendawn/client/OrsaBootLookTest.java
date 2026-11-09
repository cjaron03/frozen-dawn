package com.frozendawn.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The regenerated ORSA spinner and the first boot's ORSA beat (owner mockups, 2026-11-09). */
class OrsaBootLookTest {
    @Test
    void theSpinnerHasSixtyFourFramesOnTwoRows() {
        assertEquals(0, OrsaLogoRenderer.bootFrame(0.0F));
        assertEquals(8, OrsaLogoRenderer.bootFrame(0.125F), "The masthead's pose");
        assertEquals(63, OrsaLogoRenderer.bootFrame(0.999F));
        assertEquals(0, OrsaLogoRenderer.bootFrame(1.0F), "One orbit wraps");
        assertEquals(48, OrsaLogoRenderer.bootFrame(-0.25F), "Negative phases wrap too");
        assertArrayEquals(new float[] {0.0F, 0.0F, 1.0F / 32, 0.5F}, OrsaLogoRenderer.bootFrameUv(0), 1e-6F);
        assertArrayEquals(new float[] {1.0F / 32, 0.5F, 2.0F / 32, 1.0F}, OrsaLogoRenderer.bootFrameUv(33), 1e-6F);
        assertArrayEquals(new float[] {31.0F / 32, 0.5F, 1.0F, 1.0F}, OrsaLogoRenderer.bootFrameUv(63), 1e-6F);
    }
}
