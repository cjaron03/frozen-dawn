package com.frozendawn.maeve;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommitmentPolicyTest {
    @Test
    void surveyAdmissionIsSpentAcrossReloadAndResetsOnlyWithTheEncounter() {
        var state = policy();
        assertTrue(state.canSurvey());
        state.surveyIssued();
        assertFalse(CommitmentPolicy.load(state.save()).canSurvey());
        state.begin(UUID.randomUUID(), List.of(), 2000);
        assertTrue(state.canSurvey());
        state.engage();
        assertFalse(CommitmentPolicy.load(state.save()).canSurvey());
        var committed = policy(belief(BeliefStore.RANGED, .8));
        assertTrue(committed.choose(PLAYER, OBSERVER, List.of(candidate(BeliefStore.RANGED, 2)), 1000));
        committed.finish("TIME_COMPLETE");
        assertFalse(committed.canSurvey(), "Finishing a combat bet cannot unlock mid-fight scouting");
    }
    private static final UUID PLAYER = new UUID(0, 1);
    private static final UUID OBSERVER = new UUID(0, 2);

    private static Belief belief(String pattern, double confidence) {
        Belief belief = new Belief(pattern);
        belief.record(new ObservedEvidence(OBSERVER, UUID.randomUUID(), "minecraft:overworld",
                new BlockPos(8, 100, 8), 100, pattern.equals(BeliefStore.RANGED)
                ? "WITNESSED_PROJECTILE_DAMAGE" : "RECOVERY_ITEM_FINISHED_UNDER_COVER", true));
        var tag = belief.save();
        tag.putDouble("confidence", confidence);
        return Belief.load(tag);
    }

    private static MaeveDirector.PositionCandidate candidate(String pattern, double cost) {
        return new MaeveDirector.PositionCandidate(pattern, new BlockPos(4, 100, 8), null, cost);
    }

    private static CommitmentPolicy policy(Belief... beliefs) {
        CommitmentPolicy policy = new CommitmentPolicy();
        policy.begin(UUID.randomUUID(), List.of(beliefs), 1000);
        return policy;
    }

    @Test
    void thresholdIsInclusiveAndWeakBeliefsOnlyProvideUtilityBias() {
        var weak = policy(belief(BeliefStore.RANGED, 0.7499));
        assertFalse(weak.choose(PLAYER, OBSERVER, List.of(candidate(BeliefStore.RANGED, 0)), 1000));
        assertNull(weak.active(1000));
        assertTrue(weak.utilityBias(1000).fortify() > 0);
        var eligible = policy(belief(BeliefStore.RANGED, 0.75));
        assertTrue(eligible.choose(PLAYER, OBSERVER, List.of(candidate(BeliefStore.RANGED, 0)), 1000));
    }

    @Test
    void cheaperRecoveryBeatsHigherConfidenceAndExplainsRejectedAlternative() {
        var policy = policy(belief(BeliefStore.RANGED, 0.99), belief(BeliefStore.RECOVERY, 0.80));
        assertTrue(policy.choose(PLAYER, OBSERVER, List.of(candidate(BeliefStore.RANGED, 4),
                candidate(BeliefStore.RECOVERY, 1)), 1000));
        assertEquals(BeliefStore.RECOVERY, policy.active(1000).pattern());
        assertEquals(0.8, policy.active(1000).confidence());
        assertTrue(policy.alternatives().stream().anyMatch(s -> s.contains("MORE_COSTLY_TO_ABANDON")));
    }

    @Test
    void newEvidenceCannotUnlockACommitmentDuringTheEncounterThatSuppliedIt() {
        var original = belief(BeliefStore.RANGED, 0.6);
        var policy = policy(original);
        original.record(new ObservedEvidence(OBSERVER, UUID.randomUUID(), "minecraft:overworld",
                BlockPos.ZERO, 1001, "WITNESSED_PROJECTILE_DAMAGE", true));
        assertEquals(0.8, original.currentConfidence(1001), 1e-9);
        assertFalse(policy.choose(PLAYER, OBSERVER, List.of(candidate(BeliefStore.RANGED, 0)), 1002));
        policy.begin(UUID.randomUUID(), List.of(original), 2000);
        assertTrue(policy.choose(PLAYER, OBSERVER, List.of(candidate(BeliefStore.RANGED, 0)), 2000));
    }

    @Test
    void anAlreadyObservedOutcomeCannotBePassedOffAsAnEarlyPrediction() {
        var policy = policy(belief(BeliefStore.RANGED, 1));
        policy.confirm(BeliefStore.RANGED);
        assertFalse(policy.choose(PLAYER, OBSERVER, List.of(candidate(BeliefStore.RANGED, 0)), 1001));
        assertEquals("PREDICTION_ALREADY_CONFIRMED", policy.ineligible(BeliefStore.RANGED, 1001));
        var loaded = CommitmentPolicy.load(policy.save());
        assertFalse(loaded.choose(PLAYER, OBSERVER, List.of(candidate(BeliefStore.RANGED, 0)), 1002));
    }

    @Test
    void crowdCannotClaimMultipleBetsEvenAfterACommitmentFinishes() {
        var policy = policy(belief(BeliefStore.RANGED, 1));
        var options = List.of(candidate(BeliefStore.RANGED, 0));
        assertTrue(policy.choose(PLAYER, OBSERVER, options, 1000));
        assertFalse(policy.choose(PLAYER, UUID.randomUUID(), options, 1001));
        policy.arrived(1001);
        policy.contact(1400);
        assertNotNull(policy.active(1400));
        assertNull(policy.active(1401), "Fresh contact cannot extend the ranged-cover hold");
        assertFalse(policy.choose(PLAYER, UUID.randomUUID(), options, 1402));
    }

    @Test
    void swordStanceKeepsOneBetUntilContactExpiresAndStillHonorsContradictionCooldown() {
        var sword = belief(BeliefStore.SWORD, 1);
        var policy = policy(sword);
        var options = List.of(candidate(BeliefStore.SWORD, 1.5));
        assertTrue(policy.choose(PLAYER, OBSERVER, options, 1000));
        policy.arrived(1001);
        assertEquals(-1, policy.active(1401).holdUntil());
        policy.contact(1500);
        policy.contradict(BeliefStore.SWORD, 1501);
        assertNotNull(policy.active(2099), "Contradiction cannot buy an immediate equipment swap");
        assertEquals(1001, policy.active(2099).arrivedAt());
        assertFalse(policy.choose(PLAYER, UUID.randomUUID(), options, 2099));
        assertNull(policy.active(2100));
        assertEquals("ENCOUNTER_ENDED", policy.outcome(2100));
        assertFalse(policy.choose(PLAYER, OBSERVER, options, 2101));
        policy.begin(UUID.randomUUID(), List.of(sword), 2200);
        assertEquals("CONTRADICTION_COOLDOWN", policy.ineligible(BeliefStore.SWORD, 2200));
    }

    @Test
    void ordinaryStoreContactsKeepTheSameSwordEncounterAliveWithoutRefreshingTheBet() {
        var store = new BeliefStore();
        for (int i = 0; i < 5; i++) store.record(PLAYER, OBSERVER, "minecraft:overworld", BlockPos.ZERO,
                i * 610L, BeliefStore.SWORD, true, "WITNESSED_DAMAGE_SWORD");
        store.contact(PLAYER, OBSERVER, "minecraft:overworld", 3050);
        var policy = store.commitment(PLAYER);
        assertTrue(policy.choose(PLAYER, OBSERVER, List.of(candidate(BeliefStore.SWORD, 1.5)), 3050));
        policy.arrived(3050);
        var encounter = policy.encounter();
        store.contact(PLAYER, 3600);
        store.contact(PLAYER, 4150);
        assertSame(policy, store.commitmentFor(OBSERVER, 4749));
        assertEquals(encounter, policy.encounter());
        assertEquals(3050, policy.active(4749).arrivedAt());
        var loaded = BeliefStore.load(store.save());
        assertNull(loaded.commitmentFor(OBSERVER, 4749));
        assertTrue(loaded.commitment(PLAYER).issued(), "Reload retains the spent stance");
        assertNull(store.commitmentFor(OBSERVER, 4750));
    }

    @Test
    void wrongPredictionHoldsForABeatEvenAtTheEndOfItsDwellTime() {
        var policy = policy(belief(BeliefStore.RECOVERY, 1));
        policy.choose(PLAYER, OBSERVER, List.of(candidate(BeliefStore.RECOVERY, 0)), 1000);
        policy.arrived(1000);
        policy.contradict(BeliefStore.RECOVERY, 1399);
        assertNotNull(policy.active(1458));
        assertEquals(1399, policy.active(1458).contradictedAt());
        assertNull(policy.active(1459));
    }

    @Test
    void contradictionBlocksExactlyTheNextEncounterEvenAtHighConfidence() {
        var belief = belief(BeliefStore.RANGED, 1);
        var policy = policy(belief);
        var options = List.of(candidate(BeliefStore.RANGED, 0));
        policy.contradict(BeliefStore.RANGED, 1001);
        assertFalse(policy.choose(PLAYER, OBSERVER, options, 1002));
        policy.begin(UUID.randomUUID(), List.of(belief), 2000);
        assertEquals("CONTRADICTION_COOLDOWN", policy.ineligible(BeliefStore.RANGED, 2000));
        assertFalse(policy.choose(PLAYER, OBSERVER, options, 2000));
        policy.begin(UUID.randomUUID(), List.of(belief), 3000);
        assertTrue(policy.choose(PLAYER, OBSERVER, options, 3000));
    }

    @Test
    void reloadCannotBuyASecondBetOrSkipTheContradictionCooldown() {
        var belief = belief(BeliefStore.RECOVERY, 1);
        var policy = policy(belief);
        var options = List.of(candidate(BeliefStore.RECOVERY, 0));
        policy.choose(PLAYER, OBSERVER, options, 1000);
        policy.contradict(BeliefStore.RECOVERY, 1001);
        var loaded = CommitmentPolicy.load(policy.save());
        assertNull(loaded.active(1002));
        assertEquals("RELOAD_RELEASED", loaded.outcome(1002));
        assertFalse(loaded.choose(PLAYER, OBSERVER, options, 1002));
        loaded.begin(UUID.randomUUID(), List.of(belief), 2000);
        loaded = CommitmentPolicy.load(loaded.save());
        assertFalse(loaded.choose(PLAYER, OBSERVER, options, 2000));
        loaded.begin(UUID.randomUUID(), List.of(belief), 3000);
        assertTrue(loaded.choose(PLAYER, OBSERVER, options, 3000));
    }

    @Test
    void frozenBasisStillDecaysAndFailedApproachHasABoundedLifetime() {
        var policy = policy(belief(BeliefStore.RANGED, 0.8));
        assertFalse(policy.choose(PLAYER, OBSERVER, List.of(candidate(BeliefStore.RANGED, 0)),
                100 + BeliefPolicy.STALE_AFTER + BeliefPolicy.HALF_LIFE));
        var fresh = policy(belief(BeliefStore.RANGED, 0.8));
        assertTrue(fresh.choose(PLAYER, OBSERVER, List.of(candidate(BeliefStore.RANGED, 0)), 1000));
        assertNotNull(fresh.active(1099));
        assertNull(fresh.active(1100));
    }

    @Test
    void diagnosticsDoNotAdvanceExecutionOrMutatePersistence() {
        var policy = policy(belief(BeliefStore.RANGED, 1));
        policy.choose(PLAYER, OBSERVER, List.of(candidate(BeliefStore.RANGED, 0)), 1000);
        var before = policy.save();
        assertEquals("TIME_COMPLETE", policy.outcome(1100));
        assertEquals(before, policy.save());
        assertEquals(1, policy.hints(1000).size());
    }

    @Test
    void legacyProfileWaitsForANewEncounterAndErasureDropsFrozenMemory() {
        var store = new BeliefStore();
        for (int i = 0; i < 4; i++) store.record(PLAYER, OBSERVER, "minecraft:overworld", BlockPos.ZERO,
                i * 600L, BeliefStore.RANGED, true, "WITNESSED_PROJECTILE_DAMAGE");
        var old = store.save();
        for (var entry : old.getList("players", net.minecraft.nbt.Tag.TAG_COMPOUND)) ((CompoundTag) entry).remove("commitment");
        store = BeliefStore.load(old);
        assertTrue(store.commitment(PLAYER).hints(1801).isEmpty());
        store.contact(PLAYER, 2400);
        assertEquals(0.8, store.commitment(PLAYER).hints(2400).getFirst().confidence(), 1e-9);
        store.clear();
        assertNull(store.commitment(PLAYER));
        assertTrue(store.save().getList("players", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty());
    }
}
