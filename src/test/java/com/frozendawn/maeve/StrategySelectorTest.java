package com.frozendawn.maeve;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StrategySelectorTest {
    @Test void informationCanOutweighImmediateCombatButThreatAndSurvivalWin() {
        assertEquals("SURVEY_ACCESS", StrategySelector.best(StrategySelector.options(.2, 16, 1, false, false, 1, 3)).action());
        assertEquals("LOCAL_ENGAGEMENT", StrategySelector.best(StrategySelector.options(.2, 16, 1, true, false, 1, 3)).action());
        assertEquals("WITHDRAW", StrategySelector.best(StrategySelector.options(.2, 16, .4, false, false, 1, 3)).action());
        assertEquals("LOCAL_ENGAGEMENT", StrategySelector.best(StrategySelector.options(.2, 16, 1, false, true, 1, 3)).action());
    }
    @Test void resolvedUncertaintyLosesItsUtilityAndPressureHasACost() {
        var uncertain = StrategySelector.options(.2, 16, 1, false, false, 0, 3).getFirst();
        var resolved = StrategySelector.options(.95, 16, 1, false, false, 0, 3);
        assertEquals("LOCAL_ENGAGEMENT", StrategySelector.best(resolved).action());
        assertTrue(uncertain.total() > resolved.getFirst().total());
        assertTrue(uncertain.total() > StrategySelector.options(.2, 16, 1, false, false, 3, 3).getFirst().total());
        assertEquals(uncertain.tactical() + uncertain.survival() + uncertain.objective()
                + uncertain.information() * uncertain.curiosity() - uncertain.exposure() - uncertain.attention(), uncertain.total());
    }
}
