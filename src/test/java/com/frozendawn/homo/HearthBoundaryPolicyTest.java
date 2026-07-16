package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HearthBoundaryPolicyTest {

    @Test
    void formedBoundarySeparatesWarningFromProtectedGroundAcrossRotations() {
        for (long seed = 0L; seed < 16L; seed++) {
            ReturnedHearthSavedData state = formedState(seed);
            ReturnedHearthSavedData.HearthRecord major = state.hearth(
                    HearthSelectionPolicy.HearthType.MAJOR).orElseThrow();
            HearthStructurePlacement marker = FormedHearthLayout.create(
                            major.layoutSeed(), major.type()).stream()
                    .filter(placement -> placement.piece()
                            == HearthStructurePiece.BOUNDARY_MARKER)
                    .findFirst()
                    .orElseThrow();

            assertEquals(HearthBoundaryPolicy.Zone.PROTECTED,
                    HearthBoundaryPolicy.zoneFor(major, major.center()));
            assertEquals(HearthBoundaryPolicy.Zone.WARNING,
                    HearthBoundaryPolicy.zoneFor(
                            major, major.center().offset(marker.offset())));
            assertEquals(HearthBoundaryPolicy.Zone.OUTSIDE,
                    HearthBoundaryPolicy.zoneFor(major, major.center().offset(12, 0, 12)));
        }
    }

    @Test
    void intactMajorProtectsItsCentralRingAndOuterInteriors() {
        for (long seed = 0L; seed < 16L; seed++) {
            ReturnedHearthSavedData state = intactState(seed);
            ReturnedHearthSavedData.HearthRecord major = state.hearth(
                    HearthSelectionPolicy.HearthType.MAJOR).orElseThrow();
            HearthStructurePlacement marker = IntactHearthLayout.create(
                            major.layoutSeed(), major.type()).stream()
                    .filter(placement -> placement.piece()
                            == HearthStructurePiece.BOUNDARY_MARKER)
                    .max(Comparator.comparingDouble(
                            placement -> placement.offset().distSqr(BlockPos.ZERO)))
                    .orElseThrow();
            HearthStructurePlacement sacredChest = IntactHearthLayout.create(
                            major.layoutSeed(), major.type()).stream()
                    .filter(placement -> placement.piece()
                            == HearthStructurePiece.SACRED_CHEST)
                    .findFirst()
                    .orElseThrow();

            assertEquals(HearthBoundaryPolicy.Zone.PROTECTED,
                    HearthBoundaryPolicy.zoneFor(major, major.center()));
            assertEquals(HearthBoundaryPolicy.Zone.WARNING,
                    HearthBoundaryPolicy.zoneFor(
                            major, major.center().offset(marker.offset())));
            assertEquals(HearthBoundaryPolicy.Zone.PROTECTED,
                    HearthBoundaryPolicy.zoneFor(
                            major, major.center().offset(sacredChest.offset())));
            assertEquals(HearthBoundaryPolicy.Zone.OUTSIDE,
                    HearthBoundaryPolicy.zoneFor(major, major.center().offset(30, 0, 30)));
        }
    }

    private static ReturnedHearthSavedData formedState(long seed) {
        ReturnedHearthSavedData state = selectedState(seed);
        ReturnedHearthSavedData.HearthRecord major = state.hearth(
                HearthSelectionPolicy.HearthType.MAJOR).orElseThrow();
        state.advanceMaturationForDebug(HearthMaturationPolicy.FORMED_START_TICKS, 2000L);
        state.recordStructureProgress(major.id(), HearthReconciliationPolicy.FORMED_PLAN_VERSION,
                FormedHearthLayout.create(major.layoutSeed(), major.type()).size(),
                ReturnedHearthSavedData.HearthStage.FORMED, true);
        return state;
    }

    private static ReturnedHearthSavedData intactState(long seed) {
        ReturnedHearthSavedData state = selectedState(seed);
        ReturnedHearthSavedData.HearthRecord major = state.hearth(
                HearthSelectionPolicy.HearthType.MAJOR).orElseThrow();
        state.advanceMaturationForDebug(HearthMaturationPolicy.INTACT_START_TICKS, 2000L);
        state.recordStructureProgress(major.id(), HearthReconciliationPolicy.INTACT_PLAN_VERSION,
                IntactHearthLayout.create(major.layoutSeed(), major.type()).size(),
                ReturnedHearthSavedData.HearthStage.INTACT, true);
        return state;
    }

    private static ReturnedHearthSavedData selectedState(long seed) {
        ReturnedHearthSavedData state = new ReturnedHearthSavedData();
        state.applySelectionPlan(HearthSelectionPolicy.createPlan(
                seed, new BlockPos(12, 70, -24)), 1000L);
        ReturnedHearthSavedData.HearthRecord major = state.hearth(
                HearthSelectionPolicy.HearthType.MAJOR).orElseThrow();
        state.resolveSurface(major.id(), new BlockPos(100, 65, 200));
        return state;
    }
}
