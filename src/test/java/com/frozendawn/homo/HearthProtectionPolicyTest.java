package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthProtectionPolicyTest {

    @Test
    void protectionUsesExactFormedLayoutPositionsAndInteriorVolume() {
        ReturnedHearthSavedData state = new ReturnedHearthSavedData();
        state.applySelectionPlan(HearthSelectionPolicy.createPlan(
                99L, new BlockPos(12, 70, -24)), 1000L);
        ReturnedHearthSavedData.HearthRecord major = state.hearth(
                HearthSelectionPolicy.HearthType.MAJOR).orElseThrow();
        BlockPos resolved = new BlockPos(100, 65, 200);
        state.resolveSurface(major.id(), resolved);
        state.advanceMaturationForDebug(HearthMaturationPolicy.FORMED_START_TICKS, 2000L);
        state.recordStructureProgress(major.id(), HearthReconciliationPolicy.FORMED_PLAN_VERSION,
                FormedHearthLayout.create(major.layoutSeed(), major.type()).size(),
                ReturnedHearthSavedData.HearthStage.FORMED, true);

        HearthStructurePlacement chest = FormedHearthLayout.create(
                        major.layoutSeed(), major.type()).stream()
                .filter(placement -> placement.piece() == HearthStructurePiece.PROTECTED_CHEST)
                .findFirst()
                .orElseThrow();
        HearthProtectionPolicy.ProtectedTarget target = HearthProtectionPolicy
                .protectedTargetAt(state, resolved.offset(chest.offset()))
                .orElseThrow();

        assertEquals(major.id(), target.hearthId());
        assertEquals(HearthStructurePlacement.Protection.CONTAINER, target.protection());
        assertTrue(findInteriorPosition(state, major, resolved));
        assertTrue(HearthProtectionPolicy.protectedTargetAt(
                state, resolved.offset(12, 0, 12)).isEmpty());
        assertTrue(HearthProtectionPolicy.isEnvironmentalMutationProtected(
                state, resolved.offset(5, 0, 0)));
        assertFalse(HearthProtectionPolicy.isEnvironmentalMutationProtected(
                state, resolved.offset(6, 0, 0)));
        assertFalse(HearthProtectionPolicy.isEnvironmentalMutationProtected(
                state, resolved.offset(0, -6, 0)));
    }

    @Test
    void intactMajorUsesExpandedExactProtectionWithoutExpandingMinorHearths() {
        ReturnedHearthSavedData state = new ReturnedHearthSavedData();
        HearthSelectionPolicy.SelectionPlan plan = planWithMinor();
        state.applySelectionPlan(plan, 1000L);
        state.advanceMaturationForDebug(HearthMaturationPolicy.INTACT_START_TICKS, 2000L);

        ReturnedHearthSavedData.HearthRecord major = state.hearth(
                HearthSelectionPolicy.HearthType.MAJOR).orElseThrow();
        BlockPos majorCenter = new BlockPos(100, 65, 200);
        state.resolveSurface(major.id(), majorCenter);
        state.recordStructureProgress(major.id(), HearthReconciliationPolicy.INTACT_PLAN_VERSION,
                IntactHearthLayout.create(major.layoutSeed(), major.type()).size(),
                ReturnedHearthSavedData.HearthStage.INTACT, true);

        HearthStructurePlacement sacredChest = IntactHearthLayout.create(
                        major.layoutSeed(), major.type()).stream()
                .filter(placement -> placement.piece() == HearthStructurePiece.SACRED_CHEST)
                .findFirst()
                .orElseThrow();
        HearthProtectionPolicy.ProtectedTarget target = HearthProtectionPolicy
                .protectedTargetAt(state, majorCenter.offset(sacredChest.offset()))
                .orElseThrow();

        assertEquals(major.id(), target.hearthId());
        assertEquals(HearthStructurePlacement.Protection.CONTAINER, target.protection());
        assertTrue(HearthProtectionPolicy.isEnvironmentalMutationProtected(
                state, majorCenter.offset(22, 0, 22)));
        assertFalse(HearthProtectionPolicy.isEnvironmentalMutationProtected(
                state, majorCenter.offset(23, 0, 0)));

        ReturnedHearthSavedData.HearthRecord minor = state.hearth(
                HearthSelectionPolicy.HearthType.MINOR).orElseThrow();
        BlockPos minorCenter = new BlockPos(-100, 65, -200);
        state.resolveSurface(minor.id(), minorCenter);
        state.recordStructureProgress(minor.id(), HearthReconciliationPolicy.FORMED_PLAN_VERSION,
                FormedHearthLayout.create(minor.layoutSeed(), minor.type()).size(),
                ReturnedHearthSavedData.HearthStage.FORMED, true);
        assertTrue(HearthProtectionPolicy.isEnvironmentalMutationProtected(
                state, minorCenter.offset(5, 0, 0)));
        assertFalse(HearthProtectionPolicy.isEnvironmentalMutationProtected(
                state, minorCenter.offset(6, 0, 0)));
    }

    private static HearthSelectionPolicy.SelectionPlan planWithMinor() {
        BlockPos anchor = new BlockPos(12, 70, -24);
        for (long seed = 0L; seed < 100L; seed++) {
            HearthSelectionPolicy.SelectionPlan plan = HearthSelectionPolicy.createPlan(
                    seed, anchor);
            if (plan.minor().isPresent()) {
                return plan;
            }
        }
        throw new AssertionError("Expected a deterministic seed with a Minor Hearth");
    }

    private static boolean findInteriorPosition(ReturnedHearthSavedData state,
                                                ReturnedHearthSavedData.HearthRecord hearth,
                                                BlockPos center) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                BlockPos candidate = center.offset(x, 0, z);
                if (HearthProtectionPolicy.protectedInteriorAt(state, candidate)
                        .filter(hearth.id()::equals).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }
}
