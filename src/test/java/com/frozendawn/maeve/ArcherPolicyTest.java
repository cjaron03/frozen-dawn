package com.frozendawn.maeve;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArcherPolicyTest {
    private static final UUID PLAYER = new UUID(0, 71), ACTOR = new UUID(0, 72);
    private static final String DIM = "minecraft:overworld";
    private static final MaeveDirector.PositionCandidate SHIELD = new MaeveDirector.PositionCandidate(BeliefStore.SWORD, BlockPos.ZERO, null, 1.5);
    private static final MaeveDirector.PositionCandidate ARCHER = new MaeveDirector.PositionCandidate(BeliefStore.SWORD, BlockPos.ZERO, null, 1.5, null, false, true);
    private static Belief belief(double confidence) {
        var b = new Belief(BeliefStore.SWORD);
        b.record(new ObservedEvidence(ACTOR, UUID.randomUUID(), DIM, BlockPos.ZERO, 100, "WITNESSED_DAMAGE_SWORD", true));
        var tag = b.save(); tag.putDouble("confidence", confidence); return Belief.load(tag);
    }
    private static CommitmentPolicy policy(double confidence) {
        var p = new CommitmentPolicy(); p.begin(UUID.randomUUID(), List.of(belief(confidence)), 1000); return p;
    }
    private static boolean choose(CommitmentPolicy p, long now) { return p.choose(PLAYER, ACTOR, List.of(SHIELD, ARCHER), now); }

    @Test void exactHigherThresholdPreservesShieldBandAndSingleBet() {
        for (double value : new double[]{.75, .8, .89999}) {
            var p = policy(value); assertTrue(choose(p, 1000)); assertFalse(p.selected().keepAwayArcher());
        }
        var p = policy(.9); assertTrue(choose(p, 1000)); assertTrue(p.selected().keepAwayArcher());
        p.arrived(1001); assertTrue(p.selected().keepAwayArcher());
        p.finish("ARCHER_RUSHED_TO_MELEE"); assertFalse(choose(p, 1002));
    }
    @Test void currentEvidenceCannotUpgradeFrozenEncounter() {
        var b = belief(.8); var p = new CommitmentPolicy(); p.begin(UUID.randomUUID(), List.of(b), 1000);
        b.record(new ObservedEvidence(ACTOR, UUID.randomUUID(), DIM, BlockPos.ZERO, 1001, "WITNESSED_DAMAGE_SWORD", true));
        p = CommitmentPolicy.load(p.save()); assertTrue(choose(p, 1002)); assertFalse(p.selected().keepAwayArcher());
        p.begin(UUID.randomUUID(), List.of(b), 2000); assertTrue(choose(p, 2000)); assertTrue(p.selected().keepAwayArcher());
    }
    @Test void archerAndShieldOutcomesStaySeparateAcrossSave() {
        var p = policy(1); assertTrue(choose(p, 1000)); p.arrived(1000);
        p.performance().damage(ACTOR, PLAYER, DIM, new BlockPos(20, 0, 0), 1001, 4, false); p.finish("LOCAL_DEFENSE");
        var tag = p.save(); p = CommitmentPolicy.load(tag); assertEquals(tag.getCompound("performance"), p.save().getCompound("performance"));
        p.begin(UUID.randomUUID(), List.of(belief(1)), 2000); assertTrue(choose(p, 2000)); assertFalse(p.selected().keepAwayArcher());
        assertTrue(p.performance().multiplier(CounterVariantPolicy.ARCHER, DIM) < p.performance().multiplier(BeliefStore.SWORD, DIM));
    }
    @Test void reloadReleasesBowWithoutRefundingEncounterOrInventingSuccess() {
        var p = policy(1); assertTrue(choose(p, 1000)); p.arrived(1000);
        p = CommitmentPolicy.load(p.save()); assertNull(p.active(1001)); assertFalse(choose(p, 1001));
        assertTrue(p.performance().diagnostics(1001).stream().anyMatch(s -> s.contains(CounterVariantPolicy.ARCHER) && s.contains("unknown=1")));
    }
    @Test void invalidVariantsAndContradictionCannotSelectArcher() {
        var p = policy(1);
        assertFalse(p.choose(PLAYER, ACTOR, List.of(new MaeveDirector.PositionCandidate(BeliefStore.SWORD, BlockPos.ZERO,
                BlockPos.ZERO, 1, null, false, true)), 1000));
        assertFalse(p.choose(PLAYER, ACTOR, List.of(new MaeveDirector.PositionCandidate(BeliefStore.RANGED, BlockPos.ZERO,
                BlockPos.ZERO, 1, null, true, true)), 1000));
        p.contradict(BeliefStore.SWORD, 1001); assertFalse(choose(p, 1002));
    }
}
