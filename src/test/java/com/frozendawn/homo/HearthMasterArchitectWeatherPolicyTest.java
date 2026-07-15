package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthMasterArchitectWeatherPolicyTest {

    @Test
    void stormUsesSmoothMajorHearthDistanceFalloff() {
        assertEquals(1.0F,
                HearthMasterArchitectWeatherPolicy.strengthAtHorizontalDistance(0.0D));
        assertEquals(1.0F,
                HearthMasterArchitectWeatherPolicy.strengthAtHorizontalDistance(
                        HearthMasterArchitectWeatherPolicy.FULL_STRENGTH_RADIUS));
        assertEquals(0.0F,
                HearthMasterArchitectWeatherPolicy.strengthAtHorizontalDistance(
                        HearthMasterArchitectWeatherPolicy.OUTER_RADIUS));

        float near = HearthMasterArchitectWeatherPolicy.strengthAtHorizontalDistance(60.0D);
        float far = HearthMasterArchitectWeatherPolicy.strengthAtHorizontalDistance(92.0D);
        assertTrue(near > far);
        assertTrue(far > 0.0F);
    }

    @Test
    void onlyLivingHostileBoundMasterInVacuumCanProjectWeather() {
        ReturnedHearthSavedData state = intactState();
        ReturnedHearthSavedData.HearthRecord major = major(state);

        assertFalse(HearthMasterArchitectWeatherPolicy.canProject(
                major, ReturnedHearthSavedData.HiveRelationship.ORSATHAE,
                6, PhaseManager.PHASE6_VACUUM_START));

        UUID master = UUID.randomUUID();
        assertTrue(state.bindMasterArchitect(major.id(), master));
        assertFalse(HearthMasterArchitectWeatherPolicy.canProject(
                major, ReturnedHearthSavedData.HiveRelationship.NEUTRAL,
                6, PhaseManager.PHASE6_VACUUM_START));
        assertFalse(HearthMasterArchitectWeatherPolicy.canProject(
                major, ReturnedHearthSavedData.HiveRelationship.SUSPICIOUS,
                6, PhaseManager.PHASE6_VACUUM_START));
        assertTrue(HearthMasterArchitectWeatherPolicy.canProject(
                major, ReturnedHearthSavedData.HiveRelationship.ORSATHAE,
                6, PhaseManager.PHASE6_VACUUM_START));
        assertFalse(HearthMasterArchitectWeatherPolicy.canProject(
                major, ReturnedHearthSavedData.HiveRelationship.ORSATHAE,
                6, PhaseManager.PHASE6_MID_START));

        assertTrue(state.markMasterArchitectDefeated(major.id(), master, 5000L));
        assertFalse(HearthMasterArchitectWeatherPolicy.canProject(
                major, ReturnedHearthSavedData.HiveRelationship.ORSATHAE,
                6, PhaseManager.PHASE6_VACUUM_START));
    }

    private static ReturnedHearthSavedData intactState() {
        ReturnedHearthSavedData state = new ReturnedHearthSavedData();
        BlockPos anchor = new BlockPos(12, 70, -24);
        state.rememberTransponderAnchor(anchor);
        state.applySelectionPlan(
                HearthSelectionPolicy.createPlan(998877L, anchor), 1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        state.advanceMaturationForDebug(
                HearthMaturationPolicy.INTACT_START_TICKS, 1000L);
        state.resolveSurface(major.id(), new BlockPos(
                major.center().getX(), 70, major.center().getZ()));
        state.recordStructureProgress(
                major.id(), HearthReconciliationPolicy.INTACT_PLAN_VERSION,
                10_000, ReturnedHearthSavedData.HearthStage.INTACT, true);
        return state;
    }

    private static ReturnedHearthSavedData.HearthRecord major(
            ReturnedHearthSavedData state) {
        return state.hearth(HearthSelectionPolicy.HearthType.MAJOR).orElseThrow();
    }
}
