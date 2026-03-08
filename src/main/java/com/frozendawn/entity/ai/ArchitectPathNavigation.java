package com.frozendawn.entity.ai;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Custom navigation for the Architect entity.
 * Wires up the ArchitectNodeEvaluator so A* treats breakable blocks
 * as high-cost passable nodes and supports scaffold routing.
 *
 * Key overrides vs GroundPathNavigation:
 * - canUpdatePath: always true (allows pathing mid-fall after dig-down)
 * - createPath(BlockPos): bypasses vanilla's "scan up to air" logic that
 *   prevents pathing to underground targets through solid blocks.
 *   Without this, targeting a mob inside stone resolves to the surface.
 * - createPath(Set, 5-arg): retries with boosted A* budget (800 nodes) when
 *   the initial 400-node attempt returns a garbage path (contains BLOCKED
 *   nodes). This lets A* exhaust horizontal dead ends against unbreakable
 *   walls and discover vertical scaffold paths.
 */
public class ArchitectPathNavigation extends GroundPathNavigation {

    private static final int NORMAL_BUDGET = 400;
    private static final int BOOST_BUDGET = 800;

    /** Higher-budget PathFinder for retry. Shares the same evaluator as the
     *  normal finder — safe because calls are sequential and prepare() resets
     *  context each time. Shared immuneBlocks is intentional. */
    private PathFinder boostFinder;

    public ArchitectPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        ArchitectNodeEvaluator evaluator = new ArchitectNodeEvaluator();
        this.nodeEvaluator = evaluator;
        evaluator.setCanOpenDoors(true);
        evaluator.setCanPassDoors(true);
        this.boostFinder = new PathFinder(evaluator, BOOST_BUDGET);
        return new PathFinder(evaluator, Math.min(maxVisitedNodes, NORMAL_BUDGET));
    }

    @Override
    protected boolean canUpdatePath() {
        // Always allow pathing — the Architect needs to repath mid-fall
        // after digging down through blocks.
        return true;
    }

    @Override
    public Path createPath(BlockPos pos, int accuracy) {
        // Bypass GroundPathNavigation.createPath which scans up to find the
        // first air block above the target when the target is inside solid blocks.
        // The Architect can BREACH through solid blocks, so A* must target
        // the actual position — not the surface above it.
        return this.createPath(ImmutableSet.of(pos), 8, false, accuracy);
    }

    @Override
    public Path createPath(Entity entity, int accuracy) {
        // Use regionOffset=16 and offsetUpward=true, same as vanilla's entity
        // targeting. The larger region and upward offset are needed for scaffold
        // paths that go over tall walls.
        return this.createPath(ImmutableSet.of(entity.blockPosition()), 16, true, accuracy);
    }

    /**
     * Override to add A* budget boost retry.
     * Calls super (400 nodes). If the path contains BLOCKED nodes (A* exhausted
     * budget against unbreakable walls), retry with 800 nodes via boostFinder.
     * Super handles targetPos, reachRange, resetStuckTimeout — we only intervene
     * when the result is garbage.
     */
    @Override
    @Nullable
    protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward,
                              int accuracy, float followRange) {
        // First attempt: normal budget via parent's pathFinder (400 nodes).
        // Super sets targetPos, reachRange, resetStuckTimeout — all the bookkeeping.
        Path path = super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange);

        // If path is clean (no BLOCKED nodes), use it as-is.
        if (!hasBlockedNodes(path)) return path;

        // Normal budget produced garbage — retry with boosted budget.
        // Build the same PathNavigationRegion that super used.
        float range = followRange + (float) regionOffset;
        BlockPos mobPos = offsetUpward ? this.mob.blockPosition().above() : this.mob.blockPosition();
        int reach = (int) (range + 8.0F);
        PathNavigationRegion region = new PathNavigationRegion(
                this.level,
                mobPos.offset(-reach, -reach, -reach),
                mobPos.offset(reach, reach, reach)
        );

        // Same evaluator, re-prepared by findPath() internally.
        Path boostPath = this.boostFinder.findPath(
                region, this.mob, targets, range, accuracy, 1.0F);

        if (boostPath != null && !hasBlockedNodes(boostPath)) {
            // Boost found a clean path — store it in the protected path field
            // so tick() walks it. Super already set targetPos/reachRange from
            // the first attempt (same targets), which is correct.
            this.path = boostPath;
            return boostPath;
        }

        // Both failed — return the original path. Entity fallback logic
        // (force scaffold, findBreakableWallBlock) handles it.
        return path;
    }

    private static boolean hasBlockedNodes(@Nullable Path path) {
        if (path == null) return true;
        for (int i = 0; i < path.getNodeCount(); i++) {
            if (path.getNode(i).type == PathType.BLOCKED) return true;
        }
        return false;
    }
}
