package com.frozendawn.maeve;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExitInterceptionTest {
    private static final UUID PLAYER = new UUID(0, 1), ACTOR = new UUID(0, 2), WITNESS = new UUID(0, 3);
    private static final String DIM = "minecraft:overworld", A = "RETREAT_BEARING_E", B = "RETREAT_BEARING_N";
    private static final BlockPos EXIT_A = new BlockPos(4, 0, 0), EXIT_B = new BlockPos(0, 0, -4);

    private static BeliefStore trained() {
        var store = new BeliefStore();
        var world = store.observeContact(PLAYER, ACTOR, DIM, 0);
        world.sample(ACTOR, DIM, BlockPos.ZERO, true, 0);
        for (int i = 0; i < 5; i++) support(store, A, i * 640L);
        for (var exit : List.of(EXIT_A, EXIT_B)) world.access(DIM, exit, exit.equals(EXIT_A) ? exit.west() : exit.south(),
                new ObservedEvidence(ACTOR, store.commitment(PLAYER).encounter(), DIM, exit, 2560, "CROSSING", true));
        return store;
    }

    private static void support(BeliefStore store, String pattern, long now) {
        store.record(PLAYER, ACTOR, DIM, EXIT_A, now, pattern, true, "WITNESSED_OUTWARD_CROSSING");
    }
    private static String pattern(BeliefStore store) { return ExitPrediction.key(A, B, store.world(PLAYER).area(DIM).id()); }
    private static MaeveDirector.PositionCandidate candidate(String pattern, BlockPos pos) {
        return new MaeveDirector.PositionCandidate(pattern, pos, null, 4, new MaeveDirector.SpatialTarget(pos.below(), pos));
    }
    private static void watch(BeliefStore store, String pattern, BlockPos pos, long now) {
        store.contact(PLAYER, ACTOR, DIM, now);
        var policy = store.commitment(PLAYER);
        assertTrue(policy.choose(PLAYER, ACTOR, List.of(candidate(pattern, pos)), now), policy.alternatives().toString());
        policy.arrived(now + 1, store.world(PLAYER));
    }
    private static MaeveDirector.BeliefSnapshot belief(BeliefStore store, String pattern, long now) {
        return store.snapshot(PLAYER, now).stream().filter(b -> b.pattern().equals(pattern)).findFirst().orElseThrow();
    }

    @Test void fiveCausalEpisodesReachGateWithoutBypassingOrdinaryCooldownOrConfidence() {
        var store = trained(); long now = 3200;
        String pattern = pattern(store);
        for (int i = 0; i < 5; i++) {
            watch(store, A, EXIT_A, now);
            ExitInterception.record(store, PLAYER, WITNESS, DIM, EXIT_B, now + 10, .2);
            store.record(PLAYER, WITNESS, DIM, EXIT_B, now + 10, A, false, "OTHER_BEARING");
            ExitInterception.record(store, PLAYER, ACTOR, DIM, EXIT_B, now + 11, .2);
            assertEquals(i + 1, belief(store, pattern, now + 11).evidence(), "Observers share the encounter allowance");
            assertEquals("ENCOUNTER_BET_ALREADY_USED", store.commitment(PLAYER).ineligible(pattern, now + 11));
            store.commitment(PLAYER).finish("INTERRUPTED");
            support(store, A, now + 650);
            assertTrue(store.commitment(PLAYER).blocked().contains(A));
            support(store, A, now + 1300);
            now += 1950;
        }
        watch(store, pattern, EXIT_B, now);
        assertEquals(1, store.commitment(PLAYER).selected().confidence(), 1e-9);
        var selected = store.commitment(PLAYER).selected();
        ExitInterception.record(store, PLAYER, WITNESS, DIM, EXIT_A, now + 10, .2);
        assertEquals(.65, belief(store, pattern, now + 10).confidence(), 1e-9);
        assertEquals(EXIT_B, store.commitment(PLAYER).active(now + 11).position(), "Returning to A beats the fixed B watch");
        assertEquals(selected.encounter(), store.commitment(PLAYER).active(now + 11).encounter());
        assertEquals(1, store.snapshot(PLAYER, now + 10).stream().filter(b -> ExitPrediction.parse(b.pattern()) != null).count(), "No recursive pair");
        assertTrue(store.commitment(PLAYER).blockNext().contains(pattern));
    }

    @Test void missingArrivalExpiredOrReleasedWatchesAndOtherSheltersCannotTrain() {
        var store = trained(); store.contact(PLAYER, 3200);
        assertTrue(store.commitment(PLAYER).choose(PLAYER, ACTOR, List.of(candidate(A, EXIT_A)), 3200));
        ExitInterception.record(store, PLAYER, WITNESS, DIM, EXIT_B, 3201, .2);
        assertEquals(1, store.snapshot(PLAYER, 3201).size());
        store.commitment(PLAYER).arrived(3202, store.world(PLAYER));
        ExitInterception.record(store, UUID.randomUUID(), WITNESS, DIM, EXIT_B, 3203, .2);
        ExitInterception.record(store, PLAYER, WITNESS, "minecraft:the_nether", EXIT_B, 3203, .2);
        ExitInterception.record(store, PLAYER, WITNESS, DIM, EXIT_B, 3602, .2);
        assertEquals(1, store.snapshot(PLAYER, 3602).size());
        watch(store, A, EXIT_A, 4200); store.commitment(PLAYER).finish("EFFECTIVE_DAMAGE");
        ExitInterception.record(store, PLAYER, WITNESS, DIM, EXIT_B, 4205, .2);
        assertEquals(1, store.snapshot(PLAYER, 4205).size());
        watch(store, A, EXIT_A, 4900);
        store.world(PLAYER).sample(ACTOR, DIM, new BlockPos(80, 0, 0), true, 4905);
        ExitInterception.record(store, PLAYER, WITNESS, DIM, EXIT_B, 4906, .2);
        assertEquals(1, store.snapshot(PLAYER, 4906).size());
    }

    @Test void conditionalGateIsInclusiveFrozenAndDistinctFromOrdinaryBearing() {
        String key = ExitPrediction.key(A, B, new UUID(0, 9));
        for (double score : List.of(.89999, .9, .3 + .3 + .3)) {
            var belief = new Belief(key);
            belief.record(new ObservedEvidence(ACTOR, UUID.randomUUID(), DIM, EXIT_B, 10, "CAUSAL", true), score);
            var policy = new CommitmentPolicy(); policy.begin(UUID.randomUUID(), List.of(belief), 1000);
            belief.record(new ObservedEvidence(ACTOR, UUID.randomUUID(), DIM, EXIT_B, 1001, "CAUSAL", true));
            assertEquals(score > .89999, policy.choose(PLAYER, ACTOR, List.of(candidate(key, EXIT_B)), 1002));
            assertEquals(score, policy.hints(1002).getFirst().confidence());
        }
        var ordinary = new Belief(A);
        ordinary.record(new ObservedEvidence(ACTOR, UUID.randomUUID(), DIM, EXIT_A, 10, "CROSSING", true), .75);
        var policy = new CommitmentPolicy(); policy.begin(UUID.randomUUID(), List.of(ordinary), 1000);
        assertTrue(policy.choose(PLAYER, ACTOR, List.of(candidate(A, EXIT_A)), 1000));
    }

    @Test void eligibleAlternativeReplacesItsParentButCooldownRestoresOrdinaryFallback() {
        var store = trained(); String key = pattern(store);
        for (int i = 0; i < 5; i++) support(store, key, 3200 + 640L * i);
        store.contact(PLAYER, 6400);
        var policy = store.commitment(PLAYER);
        var candidates = List.of(candidate(A, EXIT_A), candidate(key, EXIT_B));
        assertEquals(List.of(candidate(key, EXIT_B)), ExitCandidates.bound(candidates, policy, 6400));
        assertTrue(policy.choose(PLAYER, ACTOR, candidates, 6400));
        assertEquals(key, policy.selected().pattern());
        policy.contradict(key, 6401); policy.finish("INTERRUPTED");
        var reloaded = BeliefStore.load(store.save()); reloaded.contact(PLAYER, 7050);
        assertTrue(reloaded.commitment(PLAYER).blocked().contains(key));
        assertEquals(List.of(candidate(A, EXIT_A)), ExitCandidates.bound(candidates, reloaded.commitment(PLAYER), 7050));
        assertTrue(reloaded.commitment(PLAYER).choose(PLAYER, ACTOR, candidates, 7050));
        assertEquals(A, reloaded.commitment(PLAYER).selected().pattern());
    }

    @Test void alternativeBearingKeepsItsObservedReferenceWhenCentroidDrifts() {
        var store = trained(); var world = store.world(PLAYER); String key = pattern(store);
        var old = world.resolve(DIM, key, EXIT_A, 3200);
        world.sample(ACTOR, DIM, new BlockPos(10, 0, 0), true, 3210);
        world.sample(ACTOR, DIM, new BlockPos(12, 0, 0), true, 3220);
        assertNotEquals(BlockPos.ZERO, world.center(DIM));
        assertEquals(old, world.resolve(DIM, key, EXIT_A, 3220));
        assertEquals(old, WorldModel.load(world.save()).resolve(DIM, key, EXIT_A, 3220));
        assertNull(world.resolve("minecraft:the_nether", key, EXIT_A, 3220));
    }

    @Test void saveRetainsCausalEvidenceAndSpentBetButNeverResumesTheWatch() {
        var store = trained(); watch(store, A, EXIT_A, 3200);
        String key = pattern(store);
        ExitInterception.record(store, PLAYER, WITNESS, DIM, EXIT_B, 3210, .2);
        var loaded = BeliefStore.load(store.save());
        assertEquals(belief(store, key, 3210), belief(loaded, key, 3210));
        assertEquals(store.world(PLAYER).area(DIM), loaded.world(PLAYER).area(DIM));
        assertTrue(loaded.commitment(PLAYER).issued()); assertNull(loaded.commitment(PLAYER).exitWatch());
        assertNull(loaded.commitment(PLAYER).active(3220));
        ExitInterception.record(loaded, PLAYER, ACTOR, DIM, EXIT_B, 3220, .2);
        assertEquals(1, belief(loaded, key, 3220).evidence());
        assertTrue(belief(loaded, key, 3220).provenance().getFirst().action().contains("arrived=3201"));
        assertNotNull(loaded.world(PLAYER).resolve(DIM, key, EXIT_A, 3220));
        loaded.world(PLAYER).sample(ACTOR, DIM, new BlockPos(80, 0, 0), true, 3230);
        assertNull(loaded.world(PLAYER).resolve(DIM, key, EXIT_A, 3230));
        assertFalse(ExitPrediction.spatial("EXIT_AFTER_E_E_00000000000000000000000000000000"));
    }

    @Test void competingResponsesContradictAndConditionalRecordsShareTheExistingCapAndErasure() {
        var store = trained(); watch(store, A, EXIT_A, 3200); String key = pattern(store);
        ExitInterception.record(store, PLAYER, WITNESS, DIM, EXIT_B, 3210, .2);
        ExitInterception.record(store, PLAYER, WITNESS, DIM, new BlockPos(0, 0, 4), 3220, .2);
        assertEquals(0, belief(store, key, 3220).confidence());
        assertEquals(1, belief(store, key, 3220).contradictions());
        for (int i = 0; i < 20; i++) support(store, ExitPrediction.key(A, B, new UUID(0, 100 + i)), 4000 + i);
        assertEquals(16, store.snapshot(PLAYER, 4100).size());
        assertEquals(store.snapshot(PLAYER, 4100), BeliefStore.load(store.save()).snapshot(PLAYER, 4100));
        var data = new MaeveSavedData(); data.synchronize(false, true);
        var saved = data.save(new CompoundTag(), null); saved.put("beliefs", store.save()); saved.putInt("dataVersion", 6);
        var loaded = MaeveSavedData.load(saved, null); assertEquals(16, loaded.store().beliefCount());
        loaded.erase(); assertFalse(loaded.save(new CompoundTag(), null).contains("beliefs"));
        loaded.synchronize(false, true); assertEquals(0, loaded.store().beliefCount());
    }
}
