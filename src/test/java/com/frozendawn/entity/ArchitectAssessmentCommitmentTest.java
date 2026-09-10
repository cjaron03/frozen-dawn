package com.frozendawn.entity;

import com.frozendawn.homo.HearthTargetPolicy.Candidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ArchitectAssessmentCommitmentTest {
    private static final UUID ALICE = new UUID(0L, 1L);
    private static final UUID BOB = new UUID(0L, 2L);
    private static final UUID CARA = new UUID(0L, 3L);
    private static final Set<UUID> EVERYONE = Set.of(ALICE, BOB, CARA);

    private static final int LEATHER = 7;
    private static final int IRON = 15;
    private static final int ACHERONITE = 24;

    @Test
    void theWeakestPlayerInRangeGetsTheCommitment() {
        ArchitectAssessmentCommitment commitment = new ArchitectAssessmentCommitment();
        assertEquals(ALICE, commitment.resolve(List.of(
                new Candidate(BOB, ACHERONITE, 16.0D),
                new Candidate(ALICE, LEATHER, 400.0D)), EVERYONE));
    }

    @Test
    void anEmptyWatchRadiusYieldsNoTarget() {
        ArchitectAssessmentCommitment commitment = new ArchitectAssessmentCommitment();
        assertNull(commitment.resolve(List.of(), EVERYONE));
    }

    @Test
    void twoPlayersTradingPlacesNeverResetTheAssessment() {
        ArchitectAssessmentCommitment commitment = new ArchitectAssessmentCommitment();
        for (int tick = 0; tick < 120; tick++) {
            boolean aliceIsNearer = tick % 2 == 0;
            UUID target = commitment.resolve(List.of(
                    new Candidate(ALICE, IRON, aliceIsNearer ? 100.0D : 324.0D),
                    new Candidate(BOB, IRON, aliceIsNearer ? 324.0D : 100.0D)), EVERYONE);
            assertEquals(ALICE, target, "commitment flipped on tick " + tick);
            commitment.advanceTicks();
        }
        assertEquals(120, commitment.ticks());
    }

    @Test
    void closingTheDistanceOnItsOwnTargetDoesNotRestartTheCount() {
        ArchitectAssessmentCommitment commitment = new ArchitectAssessmentCommitment();
        for (int tick = 0; tick < 59; tick++) {
            assertEquals(ALICE, commitment.resolve(
                    List.of(new Candidate(ALICE, IRON, 576.0D - tick * 8.0D)), EVERYONE));
            commitment.advanceTicks();
        }
        assertEquals(59, commitment.ticks());
    }

    @Test
    void aFarWeakerNewcomerStealsTheCommitment() {
        ArchitectAssessmentCommitment commitment = new ArchitectAssessmentCommitment();
        assertEquals(BOB, commitment.resolve(
                List.of(new Candidate(BOB, ACHERONITE, 100.0D)), EVERYONE));
        commitment.advanceTicks();
        assertEquals(ALICE, commitment.resolve(List.of(
                new Candidate(BOB, ACHERONITE, 100.0D),
                new Candidate(ALICE, LEATHER, 400.0D)), EVERYONE));
        assertEquals(0, commitment.ticks());
    }

    @Test
    void aMarginallyWeakerNewcomerDoesNot() {
        ArchitectAssessmentCommitment commitment = new ArchitectAssessmentCommitment();
        assertEquals(BOB, commitment.resolve(
                List.of(new Candidate(BOB, IRON, 400.0D)), EVERYONE));
        commitment.advanceTicks();
        assertEquals(BOB, commitment.resolve(List.of(
                new Candidate(BOB, IRON, 400.0D),
                new Candidate(ALICE, IRON - 1, 16.0D)), EVERYONE));
        assertEquals(1, commitment.ticks());
    }

    @Test
    void walkingOutOfRangeSuspendsTheTargetInsteadOfForgettingIt() {
        ArchitectAssessmentCommitment commitment = new ArchitectAssessmentCommitment();
        commitment.resolve(List.of(new Candidate(ALICE, LEATHER, 400.0D)), EVERYONE);

        assertEquals(BOB, commitment.resolve(
                List.of(new Candidate(BOB, ACHERONITE, 100.0D)), EVERYONE));
        assertEquals(ALICE, commitment.suspendedId());
    }

    @Test
    void aSuspendedTargetReclaimsTheCommitmentWithoutBeingRescored() {
        ArchitectAssessmentCommitment commitment = new ArchitectAssessmentCommitment();
        commitment.resolve(List.of(new Candidate(ALICE, ACHERONITE, 400.0D)), EVERYONE);
        commitment.resolve(List.of(new Candidate(BOB, LEATHER, 100.0D)), EVERYONE);

        assertEquals(ALICE, commitment.resolve(List.of(
                new Candidate(BOB, LEATHER, 100.0D),
                new Candidate(ALICE, ACHERONITE, 400.0D)), EVERYONE));
        assertNull(commitment.suspendedId());
    }

    @Test
    void resumingRestartsTheAssessmentRatherThanBankingProgress() {
        ArchitectAssessmentCommitment commitment = new ArchitectAssessmentCommitment();
        List<Candidate> inRange = List.of(new Candidate(ALICE, LEATHER, 400.0D));
        for (int tick = 0; tick < 30; tick++) {
            commitment.resolve(inRange, EVERYONE);
            commitment.advanceTicks();
        }
        assertEquals(30, commitment.ticks());

        assertNull(commitment.resolve(List.of(), EVERYONE));
        assertEquals(ALICE, commitment.resolve(inRange, EVERYONE));
        assertEquals(0, commitment.ticks());
    }

    @Test
    void aDisconnectedTargetIsForgottenSoTheArchitectMovesOn() {
        ArchitectAssessmentCommitment commitment = new ArchitectAssessmentCommitment();
        commitment.resolve(List.of(new Candidate(ALICE, LEATHER, 400.0D)), EVERYONE);

        assertEquals(BOB, commitment.resolve(
                List.of(new Candidate(BOB, ACHERONITE, 100.0D)), Set.of(BOB, CARA)));
        assertNull(commitment.suspendedId());
        assertEquals(BOB, commitment.committedId());
    }

    @Test
    void aDisconnectedBookmarkNeverStallsALaterCommitment() {
        ArchitectAssessmentCommitment commitment = new ArchitectAssessmentCommitment();
        commitment.resolve(List.of(new Candidate(ALICE, LEATHER, 400.0D)), EVERYONE);
        commitment.resolve(List.of(), EVERYONE);
        assertEquals(ALICE, commitment.suspendedId());

        assertEquals(BOB, commitment.resolve(
                List.of(new Candidate(BOB, ACHERONITE, 100.0D)), Set.of(BOB, CARA)));
        assertNull(commitment.suspendedId());
    }

    @Test
    void onlyTheMostRecentDepartureIsBookmarked() {
        ArchitectAssessmentCommitment commitment = new ArchitectAssessmentCommitment();
        commitment.resolve(List.of(new Candidate(ALICE, LEATHER, 400.0D)), EVERYONE);
        commitment.resolve(List.of(new Candidate(BOB, LEATHER, 400.0D)), EVERYONE);
        assertEquals(ALICE, commitment.suspendedId());

        commitment.resolve(List.of(new Candidate(CARA, LEATHER, 400.0D)), EVERYONE);
        assertEquals(BOB, commitment.suspendedId());
        assertEquals(CARA, commitment.committedId());
    }

    @Test
    void damageReleasesBothTheCommitmentAndTheBookmark() {
        ArchitectAssessmentCommitment commitment = new ArchitectAssessmentCommitment();
        commitment.resolve(List.of(new Candidate(ALICE, LEATHER, 400.0D)), EVERYONE);
        commitment.resolve(List.of(new Candidate(BOB, LEATHER, 400.0D)), EVERYONE);
        commitment.advanceTicks();

        commitment.release();
        assertNull(commitment.committedId());
        assertNull(commitment.suspendedId());
        assertEquals(0, commitment.ticks());
    }
}
