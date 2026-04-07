package com.frozendawn.entity.architect;

import com.frozendawn.entity.ai.DStarLitePathfinder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative mutable state used only by the Architect's approach and path orchestration.
 * Behavior still lives in {@code ArchitectEntity}; this class narrows ownership of the related state.
 */
public final class ArchitectApproachState {

    public int unreachableTicks;
    public int walkStuckTicks;
    @Nullable public BlockPos lastWalkStepPos;
    @Nullable public BlockPos lastWalkFromPos;
    @Nullable public BlockPos currentWalkCellPos;
    @Nullable public BlockPos previousWalkCellPos;
    @Nullable public BlockPos committedWalkWaypoint;
    @Nullable public BlockPos committedWalkFirstStepPos;
    @Nullable public BlockPos committedWalkStartPos;
    @Nullable public BlockPos committedWalkBacktrackPos;
    @Nullable public BlockPos committedWalkTargetSnapshot;
    @Nullable public Vec3 committedWalkStartVec;
    public final List<BlockPos> committedWalkCorridor = new ArrayList<>();
    public int committedWalkCorridorIndex;
    @Nullable public BlockPos pendingWalkBacktrackPos;
    @Nullable public BlockPos lastCompletedWalkWaypointPos;
    @Nullable public BlockPos lastCompletedWalkBacktrackPos;
    @Nullable public BlockPos lastUnstickBreakCandidate;
    public int repeatedUnstickBreakAttempts;
    public int committedWalkTicks;
    public int committedWalkAgeTicks;
    public int committedWalkNoProgressTicks;
    public double committedWalkLastDistSqr = Double.MAX_VALUE;
    public int fallbackBreakCooldown;
    @Nullable public BlockPos lastFallbackBreakPos;
    public int surfaceY = 64;
    @Nullable public BlockPos ceilingBreachPos;
    @Nullable public Vec3 stepOffStart;
    @Nullable public BlockPos stepOffTarget;
    public int stepOffProgress;
    public int scaffoldDelay;
    @Nullable public BlockPos scaffoldTarget;
    public final DStarLitePathfinder dstar = new DStarLitePathfinder();
    public boolean dstarPrecomputed;
    public boolean sprintRequested;
}
