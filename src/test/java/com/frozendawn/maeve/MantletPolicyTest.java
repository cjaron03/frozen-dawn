package com.frozendawn.maeve;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MantletPolicyTest {
    private static final UUID PLAYER = new UUID(0, 51), ACTOR = new UUID(0, 52);
    private static final String DIM = "minecraft:overworld";
    private static final MaeveDirector.PositionCandidate PILLAR = new MaeveDirector.PositionCandidate(
            BeliefStore.RANGED, BlockPos.ZERO, new BlockPos(2, 0, 0), 2);
    private static final MaeveDirector.PositionCandidate MANTLET = new MaeveDirector.PositionCandidate(
            BeliefStore.RANGED, BlockPos.ZERO, new BlockPos(2, 0, 0), 3, null, true);
    private static Belief belief(double confidence) {
        var belief = new Belief(BeliefStore.RANGED);
        belief.record(new ObservedEvidence(ACTOR, UUID.randomUUID(), DIM, new BlockPos(14, 0, 0), 100,
                "WITNESSED_PROJECTILE_DAMAGE", true));
        var tag = belief.save(); tag.putDouble("confidence", confidence); return Belief.load(tag);
    }
    private static CommitmentPolicy policy(double confidence) {
        var policy = new CommitmentPolicy(); policy.begin(UUID.randomUUID(), List.of(belief(confidence)), 1000); return policy;
    }
    private static boolean choose(CommitmentPolicy policy, long now) { return policy.choose(PLAYER, ACTOR, List.of(PILLAR, MANTLET), now); }

    @Test void thresholdIsInclusiveAndDoesNotReplaceThePillarBand() {
        for (double confidence : new double[]{.75, .8, .89999}) {
            var p = policy(confidence); assertTrue(choose(p, 1000)); assertFalse(p.selected().advancingCover());
        }
        var p = policy(.9); assertTrue(choose(p, 1000)); assertTrue(p.selected().advancingCover());
        p.arrived(1000); assertTrue(p.active(1399).advancingCover()); assertNull(p.active(1400));
        assertFalse(choose(p, 1401)); assertTrue(CommitmentPolicy.load(p.save()).issued());
    }
    @Test void newEvidenceAndReloadCannotUpgradeAnExistingEncounter() {
        var b = belief(.8); var p = new CommitmentPolicy(); p.begin(UUID.randomUUID(), List.of(b), 1000);
        b.record(new ObservedEvidence(ACTOR, UUID.randomUUID(), DIM, BlockPos.ZERO, 1001, "WITNESSED_PROJECTILE_DAMAGE", true));
        p = CommitmentPolicy.load(p.save()); assertTrue(choose(p, 1002)); assertFalse(p.selected().advancingCover());
        p.begin(UUID.randomUUID(), List.of(b), 2000); assertTrue(choose(p, 2000)); assertTrue(p.selected().advancingCover());
    }
    @Test void variantResultsAreFrozenSeparateAndBackwardCompatible() {
        var p = policy(1); assertTrue(choose(p, 1000)); p.arrived(1000);
        p.performance().damage(ACTOR, PLAYER, DIM, BlockPos.ZERO, 1001, 4, false); p.finish("LOCAL_DEFENSE");
        var saved = p.save(); p = CommitmentPolicy.load(saved);
        assertEquals(saved.getCompound("performance"), p.save().getCompound("performance"));
        p.begin(UUID.randomUUID(), List.of(belief(1)), 2000);
        assertTrue(p.performance().multiplier(CounterVariantPolicy.MANTLET, DIM) < p.performance().multiplier(BeliefStore.RANGED, DIM));
        assertTrue(choose(p, 2000)); assertFalse(p.selected().advancingCover(), "A beaten mantlet yields to historically untried pillars");
        var legacy = new StrategyPerformance(); legacy.begin(0);
        assertEquals(1, StrategyPerformance.load(legacy.save()).multiplier(CounterVariantPolicy.MANTLET, DIM));
    }
    @Test void cooldownAndCheaperOtherFamilyStillWin() {
        var p = policy(1); p.contradict(BeliefStore.RANGED, 1000);
        p.begin(UUID.randomUUID(), List.of(belief(1)), 2000); assertFalse(choose(p, 2000));
        var recovery = new Belief(BeliefStore.RECOVERY);
        for (int i = 0; i < 4; i++) recovery.record(new ObservedEvidence(ACTOR, UUID.randomUUID(), DIM, BlockPos.ZERO, i,
                "RECOVERY_ITEM_FINISHED_UNDER_COVER", true));
        p.begin(UUID.randomUUID(), List.of(belief(1), recovery), 3000);
        assertTrue(p.choose(PLAYER, ACTOR, List.of(PILLAR, MANTLET,
                new MaeveDirector.PositionCandidate(BeliefStore.RECOVERY, BlockPos.ZERO, null, 1)), 3000));
        assertEquals(BeliefStore.RECOVERY, p.selected().pattern());
    }
    @Test void unfinishedMantletBecomesUnknownAndNeverResumesOnReload() {
        var p = policy(1); assertTrue(choose(p, 1000)); p.arrived(1000);
        p = CommitmentPolicy.load(p.save());
        assertNull(p.active(1001)); assertFalse(choose(p, 1001));
        assertTrue(p.performance().diagnostics(1001).stream().anyMatch(s -> s.contains(CounterVariantPolicy.MANTLET) && s.contains("unknown=1")));
    }
}
