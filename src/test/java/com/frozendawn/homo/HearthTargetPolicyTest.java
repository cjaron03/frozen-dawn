package com.frozendawn.homo;

import com.frozendawn.homo.HearthTargetPolicy.Candidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthTargetPolicyTest {
    private static final UUID LOW = new UUID(0L, 1L);
    private static final UUID HIGH = new UUID(0L, 2L);

    private static final int UNARMORED = 0;
    private static final int LEATHER = 7;
    private static final int INSULATED = 10;
    private static final int REINFORCED = 14;
    private static final int IRON = 15;
    private static final int EVA = 16;
    private static final int DIAMOND = 20;
    private static final int ACHERONITE = 24;

    @Test
    void theLeastArmoredPlayerWinsNoMatterHowFarAwayTheyAre() {
        Candidate leather = new Candidate(LOW, LEATHER, 900.0D);
        Candidate acheronite = new Candidate(HIGH, ACHERONITE, 4.0D);
        assertEquals(leather, HearthTargetPolicy.mostVulnerable(List.of(acheronite, leather)));
    }

    @Test
    void modArmorSlotsIntoTheVanillaLadderByArmorValueAlone() {
        assertTrue(UNARMORED < LEATHER);
        assertTrue(LEATHER < INSULATED);
        assertTrue(INSULATED < REINFORCED);
        assertTrue(REINFORCED < IRON);
        assertTrue(IRON < EVA);
        assertTrue(EVA < DIAMOND);
        assertTrue(DIAMOND < ACHERONITE);

        List<Candidate> ladder = List.of(
                new Candidate(new UUID(0L, 9L), ACHERONITE, 1.0D),
                new Candidate(new UUID(0L, 8L), EVA, 1.0D),
                new Candidate(new UUID(0L, 7L), INSULATED, 1.0D),
                new Candidate(new UUID(0L, 6L), UNARMORED, 1.0D));
        assertEquals(UNARMORED, HearthTargetPolicy.mostVulnerable(ladder).armorValue());
    }

    @Test
    void distanceBreaksTiesBetweenEquallyArmoredPlayers() {
        Candidate near = new Candidate(HIGH, LEATHER, 16.0D);
        Candidate far = new Candidate(LOW, LEATHER, 256.0D);
        assertEquals(near, HearthTargetPolicy.mostVulnerable(List.of(far, near)));
    }

    @Test
    void uuidBreaksTiesSoIdenticalPlayersNeverFlicker() {
        Candidate low = new Candidate(LOW, LEATHER, 64.0D);
        Candidate high = new Candidate(HIGH, LEATHER, 64.0D);
        assertEquals(low, HearthTargetPolicy.mostVulnerable(List.of(low, high)));
        assertEquals(low, HearthTargetPolicy.mostVulnerable(List.of(high, low)));
    }

    @Test
    void anEmptyFieldHasNoTarget() {
        assertNull(HearthTargetPolicy.mostVulnerable(List.of()));
    }

    @Test
    void stealingACommitmentTakesAFullGapNotJustAnyAdvantage() {
        assertFalse(HearthTargetPolicy.warrantsRetarget(REINFORCED, LEATHER));
        assertTrue(HearthTargetPolicy.warrantsRetarget(IRON, LEATHER));
        assertTrue(HearthTargetPolicy.warrantsRetarget(EVA, LEATHER));
        assertTrue(HearthTargetPolicy.warrantsRetarget(INSULATED, UNARMORED));
    }

    @Test
    void theRetargetThresholdIsExclusiveBelowAndInclusiveAt() {
        assertFalse(HearthTargetPolicy.warrantsRetarget(
                LEATHER + HearthTargetPolicy.RETARGET_ARMOR_GAP - 1, LEATHER));
        assertTrue(HearthTargetPolicy.warrantsRetarget(
                LEATHER + HearthTargetPolicy.RETARGET_ARMOR_GAP, LEATHER));
    }

    @Test
    void aBetterArmoredNewcomerNeverStealsACommitment() {
        assertFalse(HearthTargetPolicy.warrantsRetarget(LEATHER, ACHERONITE));
        assertFalse(HearthTargetPolicy.warrantsRetarget(LEATHER, LEATHER));
    }
}
