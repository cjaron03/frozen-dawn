package com.frozendawn.maeve;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BeliefStoreTest {
    private static final UUID PLAYER = new UUID(0, 1);
    private static final UUID OBSERVER = new UUID(1, 1);

    private static void observe(BeliefStore store, long time, boolean support) {
        store.record(PLAYER, OBSERVER, "minecraft:overworld", BlockPos.ZERO, time,
                BeliefStore.RANGED, support, support ? "WITNESSED_PROJECTILE_DAMAGE" : "WITNESSED_MELEE_DAMAGE");
    }

    private static MaeveDirector.BeliefSnapshot belief(BeliefStore store, long time) {
        return store.snapshot(PLAYER, time).getFirst();
    }

    @Test
    void aCrowdAndRepeatedHitsCannotMultiplyTheSameEncounterEvidence() {
        BeliefStore store = new BeliefStore();
        for (int i = 0; i < 100; i++) {
            store.record(PLAYER, new UUID(1, i), "minecraft:overworld", BlockPos.ZERO, 100 + i,
                    BeliefStore.RANGED, true, "WITNESSED_PROJECTILE_DAMAGE");
        }
        var first = belief(store, 200);
        assertEquals(1, first.evidence());
        assertEquals(0.20, first.confidence(), 1e-9);
        assertEquals(2, first.provenance().size());
        assertEquals(.2, first.provenance().getFirst().confidenceWeight());
        assertEquals(0, first.provenance().getLast().confidenceWeight());
        assertEquals(8, store.contacts().size());
        observe(store, 201, false);
        observe(store, 202, false);
        assertEquals(1, belief(store, 202).contradictions());
        assertEquals(0, belief(store, 202).confidence());
    }

    @Test
    void deduplicationAndObserverContactsSurviveNbtReload() {
        BeliefStore store = new BeliefStore();
        observe(store, 10, true);
        UUID encounter = belief(store, 10).provenance().getFirst().encounter();
        BeliefStore loaded = BeliefStore.load(store.save());
        observe(loaded, 100, true);
        assertEquals(1, belief(loaded, 100).evidence());
        assertEquals(encounter, belief(loaded, 100).provenance().getFirst().encounter());
        assertEquals(store.contacts(), loaded.contacts());
        assertTrue(loaded.contact(PLAYER, 500));
        observe(loaded, 1099, true);
        assertEquals(1, belief(loaded, 1099).evidence());
        observe(loaded, 1699, true);
        assertEquals(2, belief(loaded, 1699).evidence());
        assertNotEquals(encounter, belief(loaded, 1699).provenance().getLast().encounter());
    }

    @Test
    void distinctEncountersRaiseConfidenceAndContradictionsReduceIt() {
        BeliefStore store = new BeliefStore();
        for (int i = 0; i < 4; i++) observe(store, 1L + i * 600L, true);
        assertEquals(0.80, belief(store, 1801).confidence(), 1e-9);
        observe(store, 2401, false);
        assertEquals(0.45, belief(store, 2401).confidence(), 1e-9);
        for (int i = 0; i < 20; i++) observe(store, 3001L + i * 600L, true);
        assertEquals(1.0, belief(store, 14401).confidence(), 1e-9);
        assertEquals(8, belief(store, 14401).provenance().size());
    }

    @Test
    void decayStartsAfterTwentyDaysAndReadingDoesNotCompoundIt() {
        BeliefStore store = new BeliefStore();
        observe(store, 100, true);
        long onset = 100 + BeliefPolicy.STALE_AFTER;
        assertEquals(0.2, belief(store, onset - 1).confidence(), 1e-9);
        assertFalse(belief(store, onset - 1).stale());
        assertEquals(0.2, belief(store, onset).confidence(), 1e-9);
        assertTrue(belief(store, onset).stale());
        long later = onset + BeliefPolicy.HALF_LIFE;
        assertEquals(0.1, belief(store, later).confidence(), 1e-9);
        assertEquals(0.1, belief(store, later).confidence(), 1e-9);
        observe(store, later, true);
        assertEquals(0.3, belief(store, later).confidence(), 1e-9);
        assertFalse(belief(store, later).stale());
    }

    @Test
    void repeatedSupportVerifiesFreshnessWithoutAddingConfidenceInALongEncounter() {
        BeliefStore store = new BeliefStore();
        for (long time = 0; time <= 1_000_000; time += 500) observe(store, time, true);
        var result = belief(store, 1_000_000);
        assertEquals(1, result.evidence());
        assertEquals(0.2, result.confidence(), 1e-9);
        assertEquals(0, result.ageTicks());
        assertEquals(2, result.provenance().size());
        assertEquals(0, result.provenance().getFirst().time());
        assertEquals(result.lastConfirmed(), result.provenance().getLast().time());
    }

    @Test
    void scoutCreditSurvivesReloadAndCannotBeDuplicatedOrUpgraded() {
        BeliefStore store = new BeliefStore();
        store.record(PLAYER, OBSERVER, "minecraft:overworld", BlockPos.ZERO, 10,
                BeliefStore.RANGED, true, "WITNESSED_PROJECTILE_DAMAGE reconMission=test", BeliefPolicy.RECON_SUPPORT);
        store = BeliefStore.load(store.save());
        observe(store, 20, true);
        var result = belief(store, 20);
        assertEquals(.3, result.confidence(), 1e-9);
        assertEquals(1, result.evidence());
        assertEquals(.3, result.provenance().getFirst().confidenceWeight());
        assertTrue(result.provenance().getFirst().action().contains("reconMission="));
        assertEquals(0, result.provenance().getLast().confidenceWeight());
        observe(store, 21, false);
        assertEquals(0, belief(store, 21).confidence());
        assertEquals(-.35, belief(store, 21).provenance().getLast().confidenceWeight());
        observe(store, 700, true);
        store.record(PLAYER, new UUID(1, 2), "minecraft:overworld", BlockPos.ZERO, 701,
                BeliefStore.RANGED, true, "LATER_SCOUT", BeliefPolicy.RECON_SUPPORT);
        assertEquals(.2, belief(store, 701).confidence(), 1e-9);
        assertEquals(2, belief(store, 701).evidence());
    }

    @Test
    void scoutLearningRemainsLaggedAndCanBeContradicted() {
        BeliefStore store = new BeliefStore();
        for (int i = 0; i < 3; i++) {
            store.record(PLAYER, OBSERVER, "minecraft:overworld", BlockPos.ZERO, 1 + i * 600L,
                    BeliefStore.RANGED, true, "WITNESSED_PROJECTILE_DAMAGE", BeliefPolicy.RECON_SUPPORT);
        }
        assertEquals(.9, belief(store, 1201).confidence(), 1e-9);
        assertEquals(.6, store.commitment(PLAYER).hints(1201).getFirst().confidence(), 1e-9);
        store.contact(PLAYER, 1801);
        assertEquals(.9, store.commitment(PLAYER).hints(1801).getFirst().confidence(), 1e-9);
        observe(store, 1802, false);
        assertEquals(.55, belief(store, 1802).confidence(), 1e-9);
        assertEquals("CONTRADICTED_THIS_ENCOUNTER", store.commitment(PLAYER).ineligible(BeliefStore.RANGED, 1802));
    }

    @Test
    void legacyEvidenceHasUnknownWeightWithoutInventingAScoutBonus() {
        var event = new ObservedEvidence(OBSERVER, UUID.randomUUID(), "minecraft:overworld", BlockPos.ZERO,
                10, "WITNESSED_PROJECTILE_DAMAGE", true);
        assertFalse(event.save().contains("confidenceWeight"));
        assertTrue(Double.isNaN(ObservedEvidence.load(event.save()).snapshot().confidenceWeight()));
        BeliefStore store = new BeliefStore();
        observe(store, 10, true);
        var saved = store.save();
        var evidence = saved.getList("players", 10).getCompound(0).getList("beliefs", 10)
                .getCompound(0).getList("provenance", 10).getCompound(0);
        evidence.remove("confidenceWeight");
        store = BeliefStore.load(saved);
        assertEquals(.2, belief(store, 10).confidence(), 1e-9);
        assertTrue(Double.isNaN(belief(store, 10).provenance().getFirst().confidenceWeight()));
    }

    @Test
    void contradictionDoesNotRestartStalenessGrace() {
        BeliefStore store = new BeliefStore();
        for (int i = 0; i < 5; i++) observe(store, i * 600L, true);
        long now = 2400 + BeliefPolicy.STALE_AFTER + BeliefPolicy.HALF_LIFE;
        observe(store, now, false);
        assertEquals(0.15, belief(store, now).confidence(), 1e-9);
        assertEquals(0.075, belief(store, now + BeliefPolicy.HALF_LIFE).confidence(), 1e-9);
        assertEquals(2400, belief(store, now).lastConfirmed());
    }

    @Test
    void profileAndBeliefCapsEvictByObservedTimeThenStableKey() {
        BeliefStore store = new BeliefStore();
        for (int i = 1; i <= 128; i++) {
            store.record(new UUID(0, i), OBSERVER, "minecraft:overworld", BlockPos.ZERO,
                    10, BeliefStore.RANGED, true, "WITNESSED_PROJECTILE_DAMAGE");
        }
        store.contact(new UUID(0, 1), 11);
        store.record(new UUID(0, 129), OBSERVER, "minecraft:overworld", BlockPos.ZERO,
                12, BeliefStore.RANGED, true, "WITNESSED_PROJECTILE_DAMAGE");
        assertEquals(128, store.size());
        assertTrue(store.snapshot(new UUID(0, 2), 12).isEmpty());
        assertFalse(store.snapshot(new UUID(0, 1), 12).isEmpty());
        for (int i = 0; i < 17; i++) {
            store.record(PLAYER, OBSERVER, "minecraft:overworld", BlockPos.ZERO, 20 + i,
                    "FUTURE_PATTERN_" + i, true, "OBSERVED_TEST_EVENT");
        }
        assertEquals(16, store.snapshot(PLAYER, 40).size());
        assertTrue(store.snapshot(PLAYER, 40).stream().noneMatch(b -> b.pattern().equals("FUTURE_PATTERN_0")));
        assertEquals(128, BeliefStore.load(store.save()).size());
    }

    @Test
    void playerAndDimensionProvenanceRemainIndependent() {
        BeliefStore store = new BeliefStore();
        observe(store, 1, true);
        UUID other = new UUID(0, 2);
        store.record(other, OBSERVER, "minecraft:the_nether", new BlockPos(8, 50, 9),
                1, BeliefStore.RECOVERY, false, "RECOVERY_ITEM_FINISHED_OPEN_SKY");
        assertEquals(1, belief(store, 1).evidence());
        var second = store.snapshot(other, 1).getFirst();
        assertEquals(0, second.evidence());
        assertEquals(1, second.contradictions());
        assertEquals("minecraft:the_nether", second.provenance().getFirst().dimension());
        assertNotEquals(belief(store, 1).provenance().getFirst().encounter(), second.provenance().getFirst().encounter());
        assertThrows(UnsupportedOperationException.class, () -> second.provenance().clear());
    }

    @Test
    void timeRollbackDoesNotIncreaseConfidenceOrKeepAStaleEncounter() {
        BeliefStore store = new BeliefStore();
        observe(store, 1000, true);
        assertEquals(0.2, belief(store, 1).confidence(), 1e-9);
        observe(store, 10, true);
        assertEquals(2, belief(store, 10).evidence());
        assertNotEquals(belief(store, 10).provenance().getFirst().encounter(),
                belief(store, 10).provenance().getLast().encounter());
    }

    @Test
    void legacyActivationAndErasureSerializeNoDeadStore() {
        MaeveSavedData data = MaeveSavedData.load(new CompoundTag(), null);
        assertEquals("DORMANT", data.lifecycle());
        assertNull(data.store());
        data.synchronize(false, true);
        observe(data.store(), 10, true);
        data.synchronize(false, false);
        assertEquals("ACTIVE", data.lifecycle());
        CompoundTag saved = data.save(new CompoundTag(), null);
        data = MaeveSavedData.load(saved, null);
        assertEquals(1, data.store().size());
        BeliefStore previous = data.store();
        data.erase();
        data.erase();
        assertEquals(0, previous.size());
        assertNull(data.store());
        assertFalse(data.save(saved, null).contains("beliefs"));
        MaeveSavedData loaded = MaeveSavedData.load(saved, null);
        assertEquals("ERASED", loaded.lifecycle());
        loaded.synchronize(false, false);
        assertEquals("ACTIVE", loaded.lifecycle());
        assertEquals(0, loaded.store().size());
    }

    @Test
    void authoritativeErasureDiscardsStaleDiskContentsBeforeAccess() {
        MaeveSavedData data = new MaeveSavedData();
        data.synchronize(false, true);
        observe(data.store(), 10, true);
        CompoundTag oldDisk = data.save(new CompoundTag(), null);
        MaeveSavedData loaded = MaeveSavedData.load(oldDisk, null);
        loaded.synchronize(true, true);
        assertNull(loaded.store());
        assertFalse(loaded.save(new CompoundTag(), null).contains("beliefs"));
        // A complete external pre-erasure backup retains the contents of that backup.
        assertEquals(1, MaeveSavedData.load(oldDisk, null).store().size());
    }

    @Test
    void unknownFutureSchemaIsNotSilentlyOverwritten() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("dataVersion", 7);
        assertThrows(IllegalStateException.class, () -> MaeveSavedData.load(tag, null));
    }

    @Test
    void invalidProvenanceCannotCreateAnUnexplainedBelief() {
        CompoundTag tag = new CompoundTag();
        tag.putString("pattern", BeliefStore.RANGED);
        tag.putDouble("confidence", Double.NaN);
        assertNull(Belief.load(tag));
        assertNull(ObservedEvidence.load(new CompoundTag()));
    }
}
