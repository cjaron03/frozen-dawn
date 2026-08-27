package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthCombatRosterPolicyTest {

    @Test
    void majorRosterWaitsForAllSixAccountedResidents() {
        assertTrue(HearthCombatRosterPolicy.canFreezeMajorRoster(6, 6, 0));
        assertTrue(HearthCombatRosterPolicy.canFreezeMajorRoster(5, 5, 1));
        assertTrue(HearthCombatRosterPolicy.canFreezeMajorRoster(4, 4, 2));
        assertFalse(HearthCombatRosterPolicy.canFreezeMajorRoster(5, 5, 0));
        assertFalse(HearthCombatRosterPolicy.canFreezeMajorRoster(5, 4, 1));
    }

    @Test
    void onlyPartOfTheCongregationIsDispatched() {
        assertEquals(0, HearthCombatRosterPolicy.dispatchedCount(0));
        assertEquals(0, HearthCombatRosterPolicy.dispatchedCount(1));
        assertEquals(1, HearthCombatRosterPolicy.dispatchedCount(2));
        assertEquals(2, HearthCombatRosterPolicy.dispatchedCount(4));
        assertEquals(3, HearthCombatRosterPolicy.dispatchedCount(6));
        assertEquals(3, HearthCombatRosterPolicy.dispatchedCount(20));
    }

    @Test
    void dispatchReserveLeavesThreePassiveTetherCandidates() {
        assertEquals(0, HearthCombatRosterPolicy.dispatchedCountWithReserve(3, 3));
        assertEquals(1, HearthCombatRosterPolicy.dispatchedCountWithReserve(4, 3));
        assertEquals(2, HearthCombatRosterPolicy.dispatchedCountWithReserve(5, 3));
        assertEquals(3, HearthCombatRosterPolicy.dispatchedCountWithReserve(6, 3));
        assertEquals(3, HearthCombatRosterPolicy.dispatchedCountWithReserve(20, 3));
    }

    @Test
    void reservedResidentsFightUntilTheyAreActuallyTethered() {
        assertTrue(HearthCombatRosterPolicy.canAttack(HearthEncounterRole.DISPATCHED));
        assertTrue(HearthCombatRosterPolicy.canAttack(HearthEncounterRole.RESERVED));
        assertFalse(HearthCombatRosterPolicy.canAttack(HearthEncounterRole.BYSTANDER));
        assertFalse(HearthCombatRosterPolicy.canAttack(HearthEncounterRole.TETHERED));
        assertFalse(HearthCombatRosterPolicy.canAttack(HearthEncounterRole.SPENT));
    }

    @Test
    void onlyLivingReservedResidentsCanBecomeTetherNodes() {
        assertTrue(HearthCombatRosterPolicy.canBecomeTether(HearthEncounterRole.RESERVED));
        assertFalse(HearthCombatRosterPolicy.canBecomeTether(HearthEncounterRole.DISPATCHED));
        assertFalse(HearthCombatRosterPolicy.canBecomeTether(HearthEncounterRole.BYSTANDER));
        assertFalse(HearthCombatRosterPolicy.canBecomeTether(HearthEncounterRole.TETHERED));
        assertFalse(HearthCombatRosterPolicy.canBecomeTether(HearthEncounterRole.SPENT));
    }

    @Test
    void casualtyLedgerRequiresDirectPlayerAttributionAndAPassiveVictim() {
        assertFalse(HearthCombatRosterPolicy.recordsPermanentCasualty(
                HearthEncounterRole.DISPATCHED, true));
        assertFalse(HearthCombatRosterPolicy.recordsPermanentCasualty(
                HearthEncounterRole.RESERVED, true));
        assertTrue(HearthCombatRosterPolicy.recordsPermanentCasualty(
                HearthEncounterRole.BYSTANDER, true));
        assertTrue(HearthCombatRosterPolicy.recordsPermanentCasualty(
                HearthEncounterRole.TETHERED, true));
        assertTrue(HearthCombatRosterPolicy.recordsPermanentCasualty(
                HearthEncounterRole.SPENT, true));
        assertTrue(HearthCombatRosterPolicy.recordsPermanentCasualty(
                HearthEncounterRole.UNASSIGNED, true));
        assertFalse(HearthCombatRosterPolicy.recordsPermanentCasualty(
                HearthEncounterRole.BYSTANDER, false));
    }

    @Test
    void residentReplacementWaitsUntilTheMasterEncounterEnds() {
        assertTrue(HearthCombatRosterPolicy.suppressReplacement(true, true));
        assertFalse(HearthCombatRosterPolicy.suppressReplacement(false, true));
        assertFalse(HearthCombatRosterPolicy.suppressReplacement(true, false));
    }
}
