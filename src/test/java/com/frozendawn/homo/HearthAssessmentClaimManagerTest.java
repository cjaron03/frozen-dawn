package com.frozendawn.homo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two Architects of one Major Hearth should not both assess the same player,
 * unless that player is the only one there.
 */
class HearthAssessmentClaimManagerTest {

    private static final UUID HEARTH = new UUID(1L, 0L);
    private static final UUID OTHER_HEARTH = new UUID(1L, 1L);
    private static final UUID ASSESSOR = new UUID(2L, 0L);
    private static final UUID RESIDENT = new UUID(2L, 1L);
    private static final UUID ALICE = new UUID(0L, 1L);
    private static final UUID BOB = new UUID(0L, 2L);

    private static final long TTL = 100L;
    private static final Function<UUID, UUID> SELF = id -> id;

    @BeforeEach
    void clearClaims() {
        HearthAssessmentClaimManager.reset();
    }

    @Test
    void anUnclaimedPlayerIsFreeToAssess() {
        assertFalse(HearthAssessmentClaimManager.claimedByOther(0L, HEARTH, ASSESSOR, ALICE));
    }

    @Test
    void theSecondArchitectSeesTheFirstArchitectsClaim() {
        HearthAssessmentClaimManager.claim(0L, HEARTH, ASSESSOR, ALICE);

        assertTrue(HearthAssessmentClaimManager.claimedByOther(0L, HEARTH, RESIDENT, ALICE));
    }

    @Test
    void anArchitectNeverBlocksItself() {
        HearthAssessmentClaimManager.claim(0L, HEARTH, ASSESSOR, ALICE);

        assertFalse(HearthAssessmentClaimManager.claimedByOther(0L, HEARTH, ASSESSOR, ALICE));
    }

    @Test
    void claimsDoNotCrossBetweenHearths() {
        HearthAssessmentClaimManager.claim(0L, HEARTH, ASSESSOR, ALICE);

        assertFalse(
                HearthAssessmentClaimManager.claimedByOther(0L, OTHER_HEARTH, RESIDENT, ALICE));
    }

    @Test
    void aSoloPlayerIsStillOfferedToTheSecondArchitect() {
        HearthAssessmentClaimManager.claim(0L, HEARTH, ASSESSOR, ALICE);

        List<UUID> offered = HearthAssessmentClaimManager.filterClaimed(
                0L, HEARTH, RESIDENT, List.of(ALICE), SELF);

        assertEquals(List.of(ALICE), offered);
    }

    @Test
    void aSecondPlayerDivertsTheSecondArchitect() {
        HearthAssessmentClaimManager.claim(0L, HEARTH, ASSESSOR, ALICE);

        List<UUID> offered = HearthAssessmentClaimManager.filterClaimed(
                0L, HEARTH, RESIDENT, List.of(ALICE, BOB), SELF);

        assertEquals(List.of(BOB), offered);
    }

    @Test
    void twoArchitectsSettleOnDifferentPlayersAndStayThere() {
        // Tick one: the assessor takes the weakest, the resident is pushed to the other.
        HearthAssessmentClaimManager.claim(0L, HEARTH, ASSESSOR, ALICE);
        List<UUID> residentSees = HearthAssessmentClaimManager.filterClaimed(
                0L, HEARTH, RESIDENT, List.of(ALICE, BOB), SELF);
        assertEquals(List.of(BOB), residentSees);
        HearthAssessmentClaimManager.claim(0L, HEARTH, RESIDENT, BOB);

        // Tick two: neither is offered the other's target, so neither swaps.
        assertEquals(List.of(ALICE), HearthAssessmentClaimManager.filterClaimed(
                1L, HEARTH, ASSESSOR, List.of(ALICE, BOB), SELF));
        assertEquals(List.of(BOB), HearthAssessmentClaimManager.filterClaimed(
                1L, HEARTH, RESIDENT, List.of(ALICE, BOB), SELF));
    }

    @Test
    void aDeadArchitectStopsReservingItsPlayerOnceTheLeaseLapses() {
        HearthAssessmentClaimManager.claim(0L, HEARTH, ASSESSOR, ALICE);

        assertTrue(HearthAssessmentClaimManager.claimedByOther(TTL, HEARTH, RESIDENT, ALICE));
        assertFalse(HearthAssessmentClaimManager.claimedByOther(TTL + 1L, HEARTH, RESIDENT, ALICE));
    }

    @Test
    void refreshingTheClaimEachTickKeepsItAlive() {
        for (long tick = 0L; tick <= 500L; tick += 20L) {
            HearthAssessmentClaimManager.claim(tick, HEARTH, ASSESSOR, ALICE);
        }

        assertTrue(HearthAssessmentClaimManager.claimedByOther(500L, HEARTH, RESIDENT, ALICE));
    }

    @Test
    void releasingFreesThePlayerImmediately() {
        HearthAssessmentClaimManager.claim(0L, HEARTH, ASSESSOR, ALICE);
        HearthAssessmentClaimManager.release(ASSESSOR);

        assertFalse(HearthAssessmentClaimManager.claimedByOther(0L, HEARTH, RESIDENT, ALICE));
    }

    @Test
    void switchingTargetsDoesNotLeaveTheOldOneClaimed() {
        HearthAssessmentClaimManager.claim(0L, HEARTH, ASSESSOR, ALICE);
        HearthAssessmentClaimManager.claim(1L, HEARTH, ASSESSOR, BOB);

        assertFalse(HearthAssessmentClaimManager.claimedByOther(1L, HEARTH, RESIDENT, ALICE));
        assertTrue(HearthAssessmentClaimManager.claimedByOther(1L, HEARTH, RESIDENT, BOB));
    }

    @Test
    void anUnclaimedHearthNeverFilters() {
        List<UUID> both = List.of(ALICE, BOB);

        assertEquals(both, HearthAssessmentClaimManager.filterClaimed(
                0L, HEARTH, ASSESSOR, both, SELF));
    }

    @Test
    void aMissingHearthIdDisablesTheClaim() {
        HearthAssessmentClaimManager.claim(0L, null, ASSESSOR, ALICE);

        assertFalse(HearthAssessmentClaimManager.claimedByOther(0L, null, RESIDENT, ALICE));
        assertEquals(List.of(ALICE, BOB), HearthAssessmentClaimManager.filterClaimed(
                0L, null, RESIDENT, List.of(ALICE, BOB), SELF));
    }
}
