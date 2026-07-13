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
                state, resolved.offset(4, -5, 4)));
        assertFalse(HearthProtectionPolicy.isEnvironmentalMutationProtected(
                state, resolved.offset(5, 0, 0)));
        assertFalse(HearthProtectionPolicy.isEnvironmentalMutationProtected(
                state, resolved.offset(0, -6, 0)));
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
