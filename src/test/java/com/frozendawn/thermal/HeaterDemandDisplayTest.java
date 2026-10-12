package com.frozendawn.thermal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeaterDemandDisplayTest {
    @Test void settlingDemandDoesNotProduceAnInstantEtaJumpAndConverges() {
        var display = new HeaterDemandDisplay();
        assertEquals(.24, display.sample(0, .24, true, 1));
        double first = display.sample(20, .14, true, 1);
        assertTrue(first > .20 && first < .24, "A one-second change is softened without changing actual demand");
        assertEquals(first, display.sample(20, .01, true, 1), "Repeated menu fields share the same sample");
        double settled = display.sample(620, .14, true, 1);
        assertEquals(.14, settled, .001, "Stable demand converges rather than keeping stale fuel estimates");
    }
    @Test void pauseDoesNotAdvanceAverageAndOffResumeAndModeChangesAreImmediate() {
        var display = new HeaterDemandDisplay();
        display.sample(100, .7, true, 1);
        assertEquals(.7, display.sample(100, .2, true, 1));
        assertEquals(0, display.sample(100, .7, false, 1));
        assertEquals(.2, display.sample(100, .2, true, 1));
        assertEquals(1, display.sample(101, 1, true, 2));
        assertEquals(.4, display.sample(0, .4, true, 2), "Reload/clock rollback does not retain an old sample");
    }
}
