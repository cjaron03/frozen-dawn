package com.frozendawn.maeve;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DirectorDiagnosticsTest {
    private static final UUID PLAYER = new UUID(0, 1);
    private static final UUID OBSERVER = new UUID(1, 1);

    private static void observe(BeliefStore store, long tick, String pattern, boolean support, String action) {
        store.record(PLAYER, OBSERVER, "minecraft:overworld", new BlockPos(12, 101, 8), tick, pattern, support, action);
    }

    private static String explain(BeliefStore store, long tick, String pattern) {
        return String.join("\n", DirectorDiagnostics.explain(new MaeveDirector.Snapshot("ACTIVE", true,
                store.size(), store.beliefCount(), store.snapshot(PLAYER, tick)), PLAYER, pattern));
    }

    @Test
    void explainsActualSupportAndContradictionWithTheirFullProvenance() {
        BeliefStore store = new BeliefStore();
        observe(store, 100, BeliefStore.RANGED, true, "WITNESSED_PROJECTILE_DAMAGE");
        observe(store, 110, BeliefStore.RANGED, false, "WITNESSED_MELEE_DAMAGE");
        String result = explain(store, 120, BeliefStore.RANGED);
        assertTrue(result.contains("prefers ranged attacks"));
        assertTrue(result.contains("EVIDENCE: 1 contributing encounters; 1 retained events"));
        assertTrue(result.contains("CONTRADICTIONS: 1 contributing encounters; 1 retained events"));
        assertTrue(result.contains("SUPPORT WITNESSED_PROJECTILE_DAMAGE tick=100 observer=" + OBSERVER));
        assertTrue(result.contains("CONTRADICTION WITNESSED_MELEE_DAMAGE tick=110 observer=" + OBSERVER));
        assertTrue(result.contains("encounter=" + store.snapshot(PLAYER, 120).getFirst().provenance().getFirst().encounter()));
        assertTrue(result.contains("at=minecraft:overworld 12, 101, 8"));
        assertTrue(result.contains("CONFIDENCE 0.0000"));
        assertTrue(result.contains("CURRENT UNCERTAINTY:"));
    }

    @Test
    void explainsDecayFromTheMaterializedScoreWithoutMutatingTheSave() {
        BeliefStore store = new BeliefStore();
        for (int i = 0; i < 5; i++) observe(store, i * 600L, BeliefStore.RANGED, true, "WITNESSED_PROJECTILE_DAMAGE");
        long contradiction = 2400 + BeliefPolicy.STALE_AFTER + BeliefPolicy.HALF_LIFE;
        observe(store, contradiction, BeliefStore.RANGED, false, "WITNESSED_MELEE_DAMAGE");
        var saved = store.save();
        String result = explain(store, contradiction + BeliefPolicy.HALF_LIFE, BeliefStore.RANGED);
        assertTrue(result.contains("stored score=0.1500 at tick=" + contradiction));
        assertTrue(result.contains("0.1500 * 0.5^(480000/480000) = 0.0750"));
        assertTrue(result.contains("CURRENT UNCERTAINTY: Stale"));
        assertEquals(result, explain(store, contradiction + BeliefPolicy.HALF_LIFE, BeliefStore.RANGED));
        assertEquals(saved, store.save());
    }

    @Test
    void repeatedSupportAndLostHistoryAreExplainedWithoutInventingEvents() {
        BeliefStore store = new BeliefStore();
        for (int i = 0; i < 12; i++) observe(store, i * 600L, BeliefStore.RANGED, true, "WITNESSED_PROJECTILE_DAMAGE");
        observe(store, 6601, BeliefStore.RANGED, true, "WITNESSED_PROJECTILE_DAMAGE");
        observe(store, 6602, BeliefStore.RANGED, false, "WITNESSED_MELEE_DAMAGE");
        String result = explain(store, 6700, BeliefStore.RANGED);
        assertTrue(result.contains("EVIDENCE: 12 contributing encounters; 7 retained events"));
        assertTrue(result.contains("HISTORY: 8/8 retained entries; 5 counted contributions no longer retained"));
        assertTrue(result.contains("Repeated support refreshes confirmation"));
        assertTrue(result.contains("tick=6601 observer="));
        assertFalse(result.contains("tick=6600 observer="));
        assertEquals(8, result.lines().filter(line -> line.contains(" observer=")).count());
    }

    @Test
    void unconfirmedRecoveryDoesNotClaimHiddenHealthOrInventSupportingEvidence() {
        BeliefStore store = new BeliefStore();
        observe(store, 50, BeliefStore.RECOVERY, false, "RECOVERY_ITEM_FINISHED_OPEN_SKY");
        String result = explain(store, 60, BeliefStore.RECOVERY);
        assertTrue(result.contains("EVIDENCE: 0 contributing encounters; 0 retained events\n  None observed."));
        assertTrue(result.contains("CURRENT UNCERTAINTY: Never confirmed"));
        assertTrue(result.contains("does not reveal hidden health, healing effectiveness, or room identity"));
        assertTrue(result.contains("CONTRADICTION RECOVERY_ITEM_FINISHED_OPEN_SKY"));
        assertFalse(result.contains("SUPPORT RECOVERY_ITEM_FINISHED_UNDER_COVER"));
    }

    @Test
    void missingAndUnknownPatternsDoNotBecomeFabricatedBeliefs() {
        BeliefStore store = new BeliefStore();
        assertTrue(explain(store, 1, BeliefStore.RANGED).contains("No retained belief"));
        observe(store, 2, "FUTURE_PATTERN", true, "OBSERVED_TEST_EVENT");
        String result = explain(store, 3, "FUTURE_PATTERN");
        assertTrue(result.contains("No authored description is registered"));
        assertTrue(result.contains("SUPPORT OBSERVED_TEST_EVENT tick=2"));
        assertFalse(result.contains("prefers ranged attacks"));
    }

    @Test
    void explanationsStayWithinTheirSubjectAndNeverExposeErasedContents() {
        MaeveSavedData data = new MaeveSavedData();
        data.synchronize(false, true);
        observe(data.store(), 1, BeliefStore.RANGED, true, "WITNESSED_PROJECTILE_DAMAGE");
        UUID other = new UUID(0, 2);
        var otherView = new MaeveDirector.Snapshot(data.lifecycle(), true, 1, 1, data.store().snapshot(other, 2));
        String result = String.join("\n", DirectorDiagnostics.explain(otherView, other, BeliefStore.RANGED));
        assertTrue(result.contains("No retained belief"));
        assertFalse(result.contains(OBSERVER.toString()));
        var oldBeliefs = data.store().snapshot(PLAYER, 2);
        data.erase();
        // Even a stale list supplied to the formatter cannot expose evidence in ERASED state.
        var erased = new MaeveDirector.Snapshot(data.lifecycle(), true, 0, 0, oldBeliefs);
        var lines = DirectorDiagnostics.explain(erased, PLAYER, BeliefStore.RANGED);
        assertEquals(List.of("Maeve ERASED | profiles=0 beliefs=0"), lines);
        assertThrows(UnsupportedOperationException.class, () -> lines.add("archive"));
        data.synchronize(false, false);
        assertTrue(explain(data.store(), 2, BeliefStore.RANGED).contains("No retained belief"));
    }
}
