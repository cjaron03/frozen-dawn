package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.minecraft.world.phys.Vec3;

class HearthMasterArchitectPolicyTest {

    @Test
    void onlyCompletedIntactMajorHearthCanHostTheMasterArchitect() {
        ReturnedHearthSavedData state = selectedState();
        ReturnedHearthSavedData.HearthRecord major = major(state);
        assertFalse(HearthMasterArchitectPolicy.canHostMasterArchitect(major));

        state.advanceMaturationForDebug(HearthMaturationPolicy.INTACT_START_TICKS, 1000L);
        state.resolveSurface(major.id(), new BlockPos(major.center().getX(), 70,
                major.center().getZ()));
        state.recordStructureProgress(major.id(), HearthReconciliationPolicy.INTACT_PLAN_VERSION,
                10_000, ReturnedHearthSavedData.HearthStage.INTACT, true);

        assertTrue(HearthMasterArchitectPolicy.canHostMasterArchitect(major));
        state.hearth(HearthSelectionPolicy.HearthType.MINOR).ifPresent(minor ->
                assertFalse(HearthMasterArchitectPolicy.canHostMasterArchitect(minor)));
    }

    @Test
    void masterAnchorIsStableAndUsesTheReservedIntactPosition() {
        long seed = 424242L;
        assertEquals(IntactHearthLayout.masterArchitectAnchor(seed),
                HearthMasterArchitectPolicy.anchorOffset(seed));
        assertEquals(HearthMasterArchitectPolicy.anchorOffset(seed),
                HearthMasterArchitectPolicy.anchorOffset(seed));
    }

    @Test
    void onlyPermanentOrsathaeTriggersCombat() {
        assertFalse(HearthMasterArchitectPolicy.isHostileRelationship(
                ReturnedHearthSavedData.HiveRelationship.NEUTRAL));
        assertFalse(HearthMasterArchitectPolicy.isHostileRelationship(
                ReturnedHearthSavedData.HiveRelationship.SUSPICIOUS));
        assertTrue(HearthMasterArchitectPolicy.isHostileRelationship(
                ReturnedHearthSavedData.HiveRelationship.ORSATHAE));
    }

    @Test
    void combatStatsScaleWithTheWorldPreset() {
        assertEquals(300.0D,
                HearthMasterArchitectPolicy.maxHealthForPreset("default"));
        assertEquals(200.0D,
                HearthMasterArchitectPolicy.maxHealthForPreset("cinematic"));
        assertEquals(450.0D,
                HearthMasterArchitectPolicy.maxHealthForPreset("brutal"));
        assertEquals(300.0D,
                HearthMasterArchitectPolicy.maxHealthForPreset("custom"));
        assertEquals(12.0D, HearthMasterArchitectPolicy.ARMOR);
        assertEquals(1.0D, HearthMasterArchitectPolicy.KNOCKBACK_RESISTANCE);
    }

    @Test
    void combatDestinationsNeverLeaveTheStormEye() {
        BlockPos center = new BlockPos(100, 70, -40);
        Vec3 outside = center.getCenter().add(80.0D, 0.0D, 0.0D);
        Vec3 clamped = HearthMasterArchitectPolicy.clampToStormBoundary(center, outside);

        assertFalse(HearthMasterArchitectPolicy.isInsideStormBoundary(center, outside));
        assertTrue(HearthMasterArchitectPolicy.isInsideStormBoundary(center, clamped));
        assertEquals(HearthMasterArchitectPolicy.STORM_BOUNDARY_RADIUS,
                Math.sqrt(clamped.subtract(center.getCenter()).horizontalDistanceSqr()),
                0.0001D);
    }

    private static ReturnedHearthSavedData selectedState() {
        ReturnedHearthSavedData state = new ReturnedHearthSavedData();
        BlockPos anchor = new BlockPos(12, 70, -24);
        state.rememberTransponderAnchor(anchor);
        state.applySelectionPlan(HearthSelectionPolicy.createPlan(998877L, anchor), 1000L);
        return state;
    }

    private static ReturnedHearthSavedData.HearthRecord major(
            ReturnedHearthSavedData state) {
        return state.hearth(HearthSelectionPolicy.HearthType.MAJOR).orElseThrow();
    }
}
