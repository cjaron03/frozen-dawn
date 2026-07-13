package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceHearthLayoutTest {

    @Test
    void layoutIsDeterministicAndContainsTheTraceSceneLanguage() {
        List<HearthStructurePlacement> first = TraceHearthLayout.create(
                123456789L, HearthSelectionPolicy.HearthType.MAJOR);
        List<HearthStructurePlacement> second = TraceHearthLayout.create(
                123456789L, HearthSelectionPolicy.HearthType.MAJOR);

        assertEquals(first, second);
        assertEquals(1, count(first, HearthStructurePiece.COLD_CAMPFIRE));
        assertEquals(3, count(first, HearthStructurePiece.ORSA_CRATE));
        assertTrue(count(first, HearthStructurePiece.DOOR_LOWER) >= 2);
        assertEquals(count(first, HearthStructurePiece.DOOR_LOWER),
                count(first, HearthStructurePiece.DOOR_UPPER));
        assertEquals(1, count(first, HearthStructurePiece.BED_FOOT));
        assertEquals(1, count(first, HearthStructurePiece.BED_HEAD));
        assertTrue(count(first, HearthStructurePiece.SNOW_MARKER) >= 12);
    }

    @Test
    void layoutNeverWritesTwoDifferentPiecesToOnePosition() {
        for (long seed = 0L; seed < 100L; seed++) {
            List<HearthStructurePlacement> layout = TraceHearthLayout.create(
                    seed, HearthSelectionPolicy.HearthType.MAJOR);
            Set<BlockPos> positions = new HashSet<>();
            for (HearthStructurePlacement placement : layout) {
                assertTrue(positions.add(placement.offset()),
                        () -> "duplicate placement at " + placement.offset());
                assertTrue(Math.abs(placement.offset().getX())
                                <= HearthReconciliationPolicy.TRACE_FOOTPRINT_RADIUS,
                        () -> "x outside footprint: " + placement.offset());
                assertTrue(Math.abs(placement.offset().getZ())
                                <= HearthReconciliationPolicy.TRACE_FOOTPRINT_RADIUS,
                        () -> "z outside footprint: " + placement.offset());
            }
        }
    }

    @Test
    void surfaceCandidateOrderIsDeterministicSeededAndBounded() {
        List<BlockPos> first = HearthReconciliationPolicy.candidateOffsets(11L);
        List<BlockPos> same = HearthReconciliationPolicy.candidateOffsets(11L);
        List<BlockPos> different = HearthReconciliationPolicy.candidateOffsets(12L);

        assertEquals(first, same);
        assertNotEquals(first, different);
        assertEquals(BlockPos.ZERO, first.getFirst());
        assertEquals(first.size(), new HashSet<>(first).size());
        for (BlockPos offset : first) {
            assertTrue(Math.abs(offset.getX()) <= HearthReconciliationPolicy.CANDIDATE_SEARCH_RADIUS);
            assertTrue(Math.abs(offset.getZ()) <= HearthReconciliationPolicy.CANDIDATE_SEARCH_RADIUS);
        }
    }

    @Test
    void traceEligibilityPersistsUntilTheCurrentPlanCompletes() {
        ReturnedHearthSavedData state = new ReturnedHearthSavedData();
        state.applySelectionPlan(HearthSelectionPolicy.createPlan(
                99L, new BlockPos(12, 70, -24)), 1000L);
        ReturnedHearthSavedData.HearthRecord major = state.hearth(
                HearthSelectionPolicy.HearthType.MAJOR).orElseThrow();

        assertFalse(HearthReconciliationPolicy.needsTrace(major));
        state.advanceMaturationForDebug(HearthMaturationPolicy.TRACE_START_TICKS, 1000L);
        assertTrue(HearthReconciliationPolicy.needsTrace(major));

        state.recordStructureProgress(major.id(), HearthReconciliationPolicy.TRACE_PLAN_VERSION,
                10, ReturnedHearthSavedData.HearthStage.PLANNED, false);
        assertEquals(10, HearthReconciliationPolicy.resumeCursor(major));
        assertTrue(HearthReconciliationPolicy.needsTrace(major));

        state.recordStructureProgress(major.id(), HearthReconciliationPolicy.TRACE_PLAN_VERSION,
                100, ReturnedHearthSavedData.HearthStage.TRACE, true);
        assertFalse(HearthReconciliationPolicy.needsTrace(major));

        state.advanceMaturationForDebug(
                HearthMaturationPolicy.FORMED_START_TICKS
                        - HearthMaturationPolicy.TRACE_START_TICKS,
                2000L);
        assertEquals(ReturnedHearthSavedData.HearthStage.FORMED, major.stage());
        assertTrue(HearthReconciliationPolicy.needsReconciliation(major));
        assertEquals(HearthReconciliationPolicy.FORMED_PLAN_VERSION,
                HearthReconciliationPolicy.desiredPlan(major).version());
        assertEquals(0, HearthReconciliationPolicy.resumeCursor(major));

        state.recordStructureProgress(major.id(), HearthReconciliationPolicy.FORMED_PLAN_VERSION,
                20, ReturnedHearthSavedData.HearthStage.TRACE, false);
        assertEquals(20, HearthReconciliationPolicy.resumeCursor(major));
        assertTrue(HearthReconciliationPolicy.needsReconciliation(major));

        state.recordStructureProgress(major.id(), HearthReconciliationPolicy.FORMED_PLAN_VERSION,
                200, ReturnedHearthSavedData.HearthStage.FORMED, true);
        assertFalse(HearthReconciliationPolicy.needsReconciliation(major));
    }

    private static long count(List<HearthStructurePlacement> layout,
                              HearthStructurePiece piece) {
        return layout.stream().filter(placement -> placement.piece() == piece).count();
    }
}
