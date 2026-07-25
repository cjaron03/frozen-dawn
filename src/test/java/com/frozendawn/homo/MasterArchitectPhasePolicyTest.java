package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MasterArchitectPhasePolicyTest {

    @Test
    void healthThresholdsAreInclusiveAndOrdered() {
        assertEquals(MasterArchitectCombatPhase.KIT,
                MasterArchitectPhasePolicy.phaseForHealth(75.01F, 100.0F));
        assertEquals(MasterArchitectCombatPhase.CONSTRUCTION,
                MasterArchitectPhasePolicy.phaseForHealth(75.0F, 100.0F));
        assertEquals(MasterArchitectCombatPhase.TETHER,
                MasterArchitectPhasePolicy.phaseForHealth(50.0F, 100.0F));
        assertEquals(MasterArchitectCombatPhase.ASCENT,
                MasterArchitectPhasePolicy.phaseForHealth(30.0F, 100.0F));
        assertEquals(MasterArchitectCombatPhase.FLOOD,
                MasterArchitectPhasePolicy.phaseForHealth(10.0F, 100.0F));
    }

    @Test
    void brutalPresetFloodClampDoesNotStickInAscentAtRoundedTenPercent() {
        assertEquals(MasterArchitectCombatPhase.FLOOD,
                MasterArchitectPhasePolicy.phaseForHealth(45.0F, 450.0F));
        assertEquals(MasterArchitectCombatPhase.FLOOD,
                MasterArchitectPhasePolicy.phaseForHealth(45.05F, 450.0F));
        assertEquals(MasterArchitectCombatPhase.ASCENT,
                MasterArchitectPhasePolicy.phaseForHealth(45.051F, 450.0F));
        assertEquals(MasterArchitectCombatPhase.FLOOD,
                MasterArchitectPhasePolicy.advance(
                        MasterArchitectCombatPhase.ASCENT, 45.0F, 450.0F));
    }

    @Test
    void healingNeverRewindsAnEncounterPhase() {
        assertEquals(MasterArchitectCombatPhase.TETHER,
                MasterArchitectPhasePolicy.advance(
                        MasterArchitectCombatPhase.TETHER, 80.0F, 100.0F));
        assertEquals(MasterArchitectCombatPhase.ASCENT,
                MasterArchitectPhasePolicy.advance(
                        MasterArchitectCombatPhase.ASCENT, 55.0F, 100.0F));
        assertEquals(MasterArchitectCombatPhase.FLOOD,
                MasterArchitectPhasePolicy.advance(
                        MasterArchitectCombatPhase.ASCENT, 9.0F, 100.0F));
    }

    @Test
    void legacyCombatFlagsPreserveAlreadyReachedStages() {
        assertEquals(MasterArchitectCombatPhase.TETHER,
                MasterArchitectPhasePolicy.migrateLegacyState(
                        90.0F, 100.0F, true, false));
        assertEquals(MasterArchitectCombatPhase.ASCENT,
                MasterArchitectPhasePolicy.migrateLegacyState(
                        60.0F, 100.0F, true, true));
        assertEquals(MasterArchitectCombatPhase.FLOOD,
                MasterArchitectPhasePolicy.migrateLegacyState(
                        8.0F, 100.0F, false, false));
    }

    @Test
    void realMasterCannotDieOutsideTheMind() {
        assertEquals(1.0F,
                MasterArchitectPhasePolicy.clampFloodEntryDamage(
                        MasterArchitectCombatPhase.ASCENT,
                        21.0F, 200.0F, 40.0F, false),
                0.0001F);
        assertEquals(0.0F,
                MasterArchitectPhasePolicy.clampFloodEntryDamage(
                        MasterArchitectCombatPhase.FLOOD,
                        20.0F, 200.0F, 40.0F, false),
                0.0001F);
        assertEquals(1.0F,
                MasterArchitectPhasePolicy.clampFloodEntryDamage(
                        MasterArchitectCombatPhase.ASCENT,
                        21.0F, 200.0F, 40.0F, true),
                0.0001F);
    }

    @Test
    void serializedPhaseRoundTripsAndInvalidValuesFailClosed() {
        for (MasterArchitectCombatPhase phase : MasterArchitectCombatPhase.values()) {
            assertEquals(phase,
                    MasterArchitectCombatPhase.fromId(phase.id()));
            assertEquals(phase,
                    MasterArchitectCombatPhase.fromSerializedName(
                            phase.serializedName()));
        }
        assertEquals(MasterArchitectCombatPhase.KIT,
                MasterArchitectCombatPhase.fromId(999));
        assertEquals(MasterArchitectCombatPhase.KIT,
                MasterArchitectCombatPhase.fromSerializedName("unknown"));
    }
}
