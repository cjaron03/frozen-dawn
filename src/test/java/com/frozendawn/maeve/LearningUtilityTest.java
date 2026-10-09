package com.frozendawn.maeve;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LearningUtilityTest {
    private static final String DIM = "minecraft:overworld";
    private static final UUID ACTOR = new UUID(0, 1), PLAYER = new UUID(0, 2);

    private static Belief ranged(int encounters) {
        var belief = new Belief(BeliefStore.RANGED);
        for (int i = 0; i < encounters; i++) support(belief, i);
        return belief;
    }

    private static void support(Belief belief, long now) {
        belief.record(new ObservedEvidence(ACTOR, UUID.randomUUID(), DIM, BlockPos.ZERO, now, "WITNESSED_PROJECTILE_DAMAGE", true));
    }

    private static float bias(CommitmentPolicy policy, long now) {
        return LearningUtility.bias(policy, DIM, false, now).fortify();
    }

    @Test void strongerCoverWaitsForTheNextEncounterAndSurvivesReload() {
        var belief = ranged(3);
        var policy = new CommitmentPolicy();
        policy.begin(UUID.randomUUID(), List.of(belief), 100);
        assertEquals(.24, bias(policy, 100), 1e-6);
        support(belief, 101);
        assertEquals(.24, bias(policy, 102), 1e-6, "This fight cannot strengthen its own inherited cover preference");
        policy.begin(UUID.randomUUID(), List.of(belief), 1000);
        assertEquals(.56, bias(policy, 1000), 1e-6);
        assertEquals(bias(policy, 1000), bias(CommitmentPolicy.load(policy.save()), 1000));
        assertTrue(bias(policy, 101 + BeliefPolicy.STALE_AFTER + BeliefPolicy.HALF_LIFE) < .4,
                "Stale history loses the stronger cover preference");
        assertTrue(LearningUtility.bias(policy, "minecraft:the_nether", false, 1000).fortify() <= .4,
                "Another dimension cannot supply the stronger local preference");
    }

    @Test void priorContradictionsKeepTheOldWeakPreferenceForOneEncounter() {
        var belief = ranged(4);
        var policy = new CommitmentPolicy();
        policy.begin(UUID.randomUUID(), List.of(belief), 100);
        policy.contradict(BeliefStore.RANGED, 101);
        assertEquals(.56, bias(policy, 102), 1e-6, "The current encounter keeps its frozen basis");
        policy.begin(UUID.randomUUID(), List.of(belief), 1000);
        assertEquals(.32, bias(policy, 1000), 1e-6);
        policy.begin(UUID.randomUUID(), List.of(belief), 2000);
        assertEquals(.56, bias(policy, 2000), 1e-6);
    }

    @Test void failedCounterDeferralCannotBeBypassedByStrongerCoverUtility() {
        var belief = ranged(4);
        var policy = new CommitmentPolicy();
        for (int i = 0; i < 2; i++) {
            long now = 1000L * i + 100;
            policy.begin(UUID.randomUUID(), List.of(belief), now);
            assertTrue(policy.choose(PLAYER, ACTOR, List.of(new MaeveDirector.PositionCandidate(
                    BeliefStore.RANGED, BlockPos.ZERO, BlockPos.ZERO.east(2), 2)), now));
            policy.arrived(now);
            policy.performance().damage(ACTOR, PLAYER, DIM, BlockPos.ZERO, now + 20, 4, false);
            policy.finish("TIME_COMPLETE");
        }
        policy.begin(UUID.randomUUID(), List.of(belief), 3000);
        assertTrue(policy.performance().deferred(BeliefStore.RANGED, DIM));
        assertEquals(.24, bias(policy, 3000), 1e-6);
    }
}
