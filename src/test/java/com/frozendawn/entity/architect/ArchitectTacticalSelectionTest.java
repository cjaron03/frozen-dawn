package com.frozendawn.entity.architect;

import com.frozendawn.entity.ArchitectEntity;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectTacticalSelectionTest {
    private static ArchitectDecisionEngine.Context context(int action, float health, boolean corner) {
        return new ArchitectDecisionEngine.Context(action, true, 12, true, false, health, 40,
                false, true, false, 0, 1000, 0, 0, 6, 0, false, false, corner);
    }

    @Test void highHistoricalConfidenceSelectsMoreCoverAcrossTheSameSeeds() {
        var engine = new ArchitectDecisionEngine();
        int previous = 0, stronger = 0;
        for (long seed : new long[]{1, 7, 42, 1337, 2026, 65537, 314159, 8675309}) {
            var oldRandom = RandomSource.create(seed);
            var newRandom = RandomSource.create(seed);
            for (int i = 0; i < 100; i++) {
                var context = context(ArchitectEntity.ACTION_APPROACH, 40, false);
                if (engine.evaluate(context, oldRandom, .32f, 0).bestAction() == ArchitectEntity.ACTION_FORTIFY) previous++;
                if (engine.evaluate(context, newRandom, .56f, 0).bestAction() == ArchitectEntity.ACTION_FORTIFY) stronger++;
            }
        }
        assertTrue(stronger > previous + 400, "The tuning must change actual choices, not merely an unused score");
    }

    @Test void completedTacticsCannotWinAgainThroughBiasOrActionInertia() {
        var engine = new ArchitectDecisionEngine();
        for (int action : new int[]{ArchitectEntity.ACTION_PEEK, ArchitectEntity.ACTION_FORTIFY}) {
            var decision = engine.evaluate(context(action, 16, true), RandomSource.create(1337), .7f, .3f, false, false);
            assertEquals(ArchitectEntity.ACTION_APPROACH, decision.bestAction());
            assertEquals(0, decision.scores()[ArchitectEntity.ACTION_PEEK]);
            assertEquals(0, decision.scores()[ArchitectEntity.ACTION_FORTIFY]);
        }
    }
}
