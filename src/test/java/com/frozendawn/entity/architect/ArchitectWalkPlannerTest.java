package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectWalkPlannerTest {

    @Test
    void thinSurfaceHeightStillTriggersAFullBlockJump() {
        ArchitectApproachState state = new ArchitectApproachState();
        BlockPos waypoint = new BlockPos(1, 65, 0);
        state.committedWalkCorridor.add(waypoint);
        state.committedWalkTicks = 12;

        ArchitectWalkMotionPlanner.MotionStep motion =
                ArchitectWalkMotionPlanner.planCommittedWalkStep(
                        state,
                        0.7D,
                        64.125D,
                        0.5D,
                        65.75D,
                        true,
                        2,
                        0.90D,
                        0.90D,
                        (from, to) -> Direction.EAST,
                        pos -> pos.getY() + 0.125D);

        assertNotNull(motion);
        assertTrue(motion.shouldJump());
    }

    @Test
    void corridorLookaheadDoesNotSuppressImmediateStepUpJump() {
        ArchitectApproachState state = new ArchitectApproachState();
        BlockPos waypoint = new BlockPos(1, 65, 0);
        state.committedWalkCorridor.add(waypoint);
        state.committedWalkCorridor.add(new BlockPos(2, 65, 0));
        state.committedWalkCorridor.add(new BlockPos(3, 65, 0));
        state.committedWalkTicks = 12;

        ArchitectWalkMotionPlanner.MotionStep motion =
                ArchitectWalkMotionPlanner.planCommittedWalkStep(
                        state,
                        0.7D,
                        64.125D,
                        0.5D,
                        65.75D,
                        true,
                        2,
                        0.90D,
                        0.90D,
                        (from, to) -> Direction.EAST,
                        pos -> pos.getY() + 0.125D);

        assertNotNull(motion);
        assertTrue(motion.shouldJump());
        assertTrue(motion.moveTarget().x > waypoint.getX() + 0.5D);
    }

    @Test
    void upwardUnstickCandidatesNeverUndercutTheDestinationLedge() {
        BlockPos from = new BlockPos(0, 64, 0);
        BlockPos step = new BlockPos(1, 65, 0);

        Set<BlockPos> candidates = ArchitectWalkBreakPlanner.collectUnstickBreakCandidates(
                from, step, Direction.EAST);

        assertFalse(candidates.contains(step.below()));
        assertTrue(candidates.contains(step));
        assertTrue(candidates.contains(step.above()));
    }
}
