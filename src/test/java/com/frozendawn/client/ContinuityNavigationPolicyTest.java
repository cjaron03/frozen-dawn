package com.frozendawn.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContinuityNavigationPolicyTest {
    @Test void bearingsFollowMinecraftWorldAxesAcrossEveryOctant() {
        assertEquals("N", ContinuityNavigationPolicy.bearing(0, -10));
        assertEquals("NE", ContinuityNavigationPolicy.bearing(10, -10));
        assertEquals("E", ContinuityNavigationPolicy.bearing(10, 0));
        assertEquals("SE", ContinuityNavigationPolicy.bearing(10, 10));
        assertEquals("S", ContinuityNavigationPolicy.bearing(0, 10));
        assertEquals("SW", ContinuityNavigationPolicy.bearing(-10, 10));
        assertEquals("W", ContinuityNavigationPolicy.bearing(-10, 0));
        assertEquals("NW", ContinuityNavigationPolicy.bearing(-10, -10));
    }
    @Test void distanceRoundingHasBoundedErrorOutsideSearchArea() {
        for (double distance = 32.1; distance < 2000; distance += 0.7) {
            int reported = ContinuityNavigationPolicy.approximateDistance(distance);
            assertTrue(Math.abs(reported - distance) <= 12.5);
            assertEquals(0, reported % 25);
        }
    }
    @Test void horizontalDistanceKeepsSearchAreaIndependentOfElevation() {
        assertEquals(32, ContinuityNavigationPolicy.horizontalDistance(100, -200, 100, -168));
        assertEquals(50, ContinuityNavigationPolicy.horizontalDistance(-12, 20, 18, 60));
    }
    @Test void turnIndicatorMatchesFacingAndWrapsFullRotations() {
        assertEquals("↑", ContinuityNavigationPolicy.directionArrow(0, 10, 0));
        assertEquals("←", ContinuityNavigationPolicy.directionArrow(10, 0, 0));
        assertEquals("→", ContinuityNavigationPolicy.directionArrow(-10, 0, 0));
        assertEquals("↓", ContinuityNavigationPolicy.directionArrow(0, -10, 0));
        assertEquals("↑", ContinuityNavigationPolicy.directionArrow(0, -10, 180));
        assertEquals("↑", ContinuityNavigationPolicy.directionArrow(10, 0, -90));
        assertEquals(ContinuityNavigationPolicy.directionArrow(-17, 21, -120),
                ContinuityNavigationPolicy.directionArrow(-17, 21, 600));
    }
}
