package com.frozendawn.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthrotStateTest {
    @Test
    void persistentStateRoundTripsAllAuthoritativeFields() {
        HearthrotState original = populatedState(5);
        HearthrotState loaded = new HearthrotState();
        loaded.deserializeNBT(null, original.serializeNBT(null));

        assertEquals(5, loaded.stage());
        assertEquals(42_500.5D, loaded.progressTicks());
        assertEquals(0b11_1110, loaded.transitionMask());
        assertEquals(500, loaded.stationaryTicks());
        assertTrue(loaded.stillnessEpisodeRolled());
        assertEquals(321, loaded.coughTicks());
        assertEquals(654, loaded.wheezeTicks());
        assertEquals(17, loaded.contaminationWarningTicks());
        assertTrue(loaded.contaminationWarned());
        assertEquals(0.75D, loaded.colonizationRemainder());
        assertEquals(0.35D, loaded.baselineO2Remainder());
        assertTrue(loaded.hasLastPosition());
        assertEquals(123_456L, loaded.lastPosition());
    }

    @Test
    void ordinaryDeathDropsOneStageButNeverRemovesSilentInfection() {
        HearthrotState late = new HearthrotState();
        late.copyAfterDeath(populatedState(6));
        assertEquals(5, late.stage());
        assertEquals(0.0D, late.progressTicks());
        assertTrue(late.contaminationWarned());
        assertFalse(late.hasLastPosition());

        HearthrotState silent = new HearthrotState();
        silent.copyAfterDeath(populatedState(1));
        assertEquals(1, silent.stage());

        HearthrotState clean = new HearthrotState();
        clean.copyAfterDeath(populatedState(0));
        assertEquals(0, clean.stage());
    }

    @Test
    void debugClearResetsDiseaseAuthority() {
        HearthrotState state = populatedState(6);
        state.clearForDebug();

        assertEquals(0, state.stage());
        assertEquals(0.0D, state.progressTicks());
        assertFalse(state.contaminationWarned());
        assertFalse(state.stillnessEpisodeRolled());
        assertFalse(state.hasLastPosition());
        assertEquals(0, state.wheezeTicks());
    }

    private static HearthrotState populatedState(int stage) {
        HearthrotState state = new HearthrotState();
        state.setStage(stage);
        state.setProgressTicks(42_500.5D);
        state.setTransitionMask(0b11_1110);
        state.setStationaryTicks(500);
        state.setStillnessEpisodeRolled(true);
        state.setCoughTicks(321);
        state.setWheezeTicks(654);
        state.setContaminationWarningTicks(17);
        state.setContaminationWarned(true);
        state.setColonizationRemainder(0.75D);
        state.setBaselineO2Remainder(0.35D);
        state.rememberPosition(123_456L);
        return state;
    }
}
