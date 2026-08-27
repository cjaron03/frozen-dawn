package com.frozendawn.entity.architect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectEngagementPolicyTest {

    @Test
    void solidWallCannotUseCloseRangeMeleeGrace() {
        assertFalse(ArchitectMeleeEngagement.canCommitWithoutLineOfSight(
                0.8D, 1.25D, false));
    }

    @Test
    void briefOcclusionCanKeepMeleeWhenTheApproachRemainsReachable() {
        assertTrue(ArchitectMeleeEngagement.canCommitWithoutLineOfSight(
                0.8D, 1.25D, true));
        assertFalse(ArchitectMeleeEngagement.canCommitWithoutLineOfSight(
                1.25D, 1.25D, true));
    }

    @Test
    void blockedCloseTargetPrefersContactBreach() {
        assertTrue(ArchitectBreachPlanner.shouldAttemptContactBreach(
                false, false, 1.1D, 0.0D));
        assertFalse(ArchitectBreachPlanner.shouldAttemptContactBreach(
                true, false, 1.1D, 0.0D));
        assertFalse(ArchitectBreachPlanner.shouldAttemptContactBreach(
                false, true, 1.1D, 0.0D));
        assertFalse(ArchitectBreachPlanner.shouldAttemptContactBreach(
                false, false, 5.0D, 0.0D));
    }
}
