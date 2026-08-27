package com.frozendawn.homo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.frozendawn.data.ReturnedHearthSavedData;
import org.junit.jupiter.api.Test;

class MasterArchitectAuraTierTest {
    @Test
    void peacefulMoodMapsToPassiveOrNoticed() {
        assertEquals(MasterArchitectAuraTier.PASSIVE,
                MasterArchitectAuraTier.fromMood(
                        ReturnedHearthSavedData.HearthDisposition.DORMANT, false));
        assertEquals(MasterArchitectAuraTier.PASSIVE,
                MasterArchitectAuraTier.fromMood(
                        ReturnedHearthSavedData.HearthDisposition.WATCHFUL, false));
        assertEquals(MasterArchitectAuraTier.NOTICED,
                MasterArchitectAuraTier.fromMood(
                        ReturnedHearthSavedData.HearthDisposition.AGITATED, false));
    }

    @Test
    void fightOrHostileMoodAlwaysMapsToFight() {
        assertEquals(MasterArchitectAuraTier.FIGHT,
                MasterArchitectAuraTier.fromMood(
                        ReturnedHearthSavedData.HearthDisposition.DORMANT, true));
        assertEquals(MasterArchitectAuraTier.FIGHT,
                MasterArchitectAuraTier.fromMood(
                        ReturnedHearthSavedData.HearthDisposition.HOSTILE, false));
    }

    @Test
    void networkValuesAreClampedToKnownTiers() {
        assertEquals(MasterArchitectAuraTier.NONE, MasterArchitectAuraTier.clamp(-12));
        assertEquals(MasterArchitectAuraTier.FIGHT, MasterArchitectAuraTier.clamp(99));
    }
}
