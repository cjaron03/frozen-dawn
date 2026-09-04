package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectWalkBreakPlannerTest {

    /** Solid ground plane at y=62, everything at or above y=63 is air. */
    private static final Predicate<BlockPos> FLAT_GROUND = pos -> pos.getY() <= 62;

    private static final Predicate<BlockPos> NEVER_LAST_RESORT = pos -> false;

    @Test
    void flatStepNeverTargetsDestinationSupport() {
        BlockPos from = new BlockPos(563, 63, 996);
        BlockPos stepPos = new BlockPos(562, 63, 996);

        Set<BlockPos> candidates =
                ArchitectWalkBreakPlanner.collectUnstickBreakCandidates(from, stepPos, Direction.WEST);

        assertFalse(candidates.contains(stepPos.below()),
                "flat move must not mine the block the destination stands on");
    }

    @Test
    void descendingStepNeverTargetsDestinationSupport() {
        BlockPos from = new BlockPos(563, 64, 996);
        BlockPos stepPos = new BlockPos(562, 63, 996);

        Set<BlockPos> candidates =
                ArchitectWalkBreakPlanner.collectUnstickBreakCandidates(from, stepPos, Direction.WEST);

        assertFalse(candidates.contains(stepPos.below()),
                "descending move must not mine the block the destination stands on");
        assertFalse(candidates.contains(stepPos.below(2)),
                "descending move must not mine below the destination support either");
    }

    @Test
    void steppingUpStillProtectsTheLedge() {
        BlockPos from = new BlockPos(563, 63, 996);
        BlockPos stepPos = new BlockPos(562, 64, 996);

        Set<BlockPos> candidates =
                ArchitectWalkBreakPlanner.collectUnstickBreakCandidates(from, stepPos, Direction.WEST);

        assertFalse(candidates.contains(from.relative(Direction.WEST)),
                "step-up must not mine the ledge supporting the destination");
        assertFalse(candidates.contains(stepPos.below()),
                "step-up must not mine the block the destination stands on");
    }

    /**
     * Reproduces the observed thrash: the Architect stalls on open flat ground with a clear
     * destination. Nothing is actually obstructing it, so the planner must report no candidate
     * and let the caller fall through to a D* replan instead of mining its own floor.
     */
    @Test
    void clearFlatPathYieldsNoBreakCandidate() {
        BlockPos from = new BlockPos(563, 63, 996);
        BlockPos stepPos = new BlockPos(562, 63, 996);

        Set<BlockPos> candidates =
                ArchitectWalkBreakPlanner.collectUnstickBreakCandidates(from, stepPos, Direction.WEST);
        BlockPos selected = ArchitectWalkBreakPlanner.selectPreferredBreakCandidate(
                candidates, null, FLAT_GROUND, NEVER_LAST_RESORT);

        assertNull(selected, "an unobstructed stall must not produce a break target");
    }

    @Test
    void genuineObstructionIsStillSelected() {
        BlockPos from = new BlockPos(563, 63, 996);
        BlockPos stepPos = new BlockPos(562, 63, 996);
        BlockPos wall = from.relative(Direction.WEST);

        Set<BlockPos> candidates =
                ArchitectWalkBreakPlanner.collectUnstickBreakCandidates(from, stepPos, Direction.WEST);
        BlockPos selected = ArchitectWalkBreakPlanner.selectPreferredBreakCandidate(
                candidates,
                null,
                pos -> FLAT_GROUND.test(pos) || pos.equals(wall),
                NEVER_LAST_RESORT);

        assertEquals(wall, selected, "a wall blocking the step must still be broken");
    }

    @Test
    void headroomObstructionIsStillSelected() {
        BlockPos from = new BlockPos(563, 63, 996);
        BlockPos stepPos = new BlockPos(562, 63, 996);
        BlockPos ceiling = from.above();

        Set<BlockPos> candidates =
                ArchitectWalkBreakPlanner.collectUnstickBreakCandidates(from, stepPos, Direction.WEST);
        BlockPos selected = ArchitectWalkBreakPlanner.selectPreferredBreakCandidate(
                candidates,
                null,
                pos -> FLAT_GROUND.test(pos) || pos.equals(ceiling),
                NEVER_LAST_RESORT);

        assertEquals(ceiling, selected, "a block trapping the Architect's head must still be broken");
    }

    @Test
    void tallCollisionBelowDestinationIsStillReachableAsLastResort() {
        BlockPos from = new BlockPos(563, 63, 996);
        BlockPos stepPos = new BlockPos(562, 63, 996);
        BlockPos blocking = stepPos.above();

        Set<BlockPos> candidates =
                ArchitectWalkBreakPlanner.collectUnstickBreakCandidates(from, stepPos, Direction.WEST);

        assertTrue(candidates.contains(stepPos), "the destination cell itself stays a candidate");
        assertTrue(candidates.contains(blocking), "destination headroom stays a candidate");
    }
}
