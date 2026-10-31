package com.frozendawn.entity.architect;

import com.frozendawn.entity.ai.DStarLitePathfinder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    /**
     * Exact positions of unsuccessful break attempts in this local plan. Saturates rather
     * than evicting old failures: rotating candidates must not reopen a failed position.
     */
    public final Set<BlockPos> blockedUnstickBreakCandidates = new LinkedHashSet<>();
    public int unstickReinitAttempts;
    @Nullable public UUID approachProgressTarget;
    @Nullable public Vec3 approachProgressAnchor;
    public int approachNoProgressTicks;
    @Nullable public UUID abandonedApproachTarget;
    public int approachRetryAfterTick;
    public int committedWalkTicks;
    public int committedWalkAgeTicks;
    public int committedWalkNoProgressTicks;
    public double committedWalkLastDistSqr = Double.MAX_VALUE;
    public int fallbackBreakCooldown;
    @Nullable public BlockPos lastFallbackBreakPos;
    public int surfaceY = 64;
    @Nullable public BlockPos ceilingBreachPos;
    /** Walking route accepted instead of a queued breach; valid only while navigation owns it. */
    @Nullable public net.minecraft.world.level.pathfinder.Path openRouteAfterBreak;
    @Nullable public UUID openRouteSubject;
    @Nullable public Vec3 stepOffStart;
    @Nullable public BlockPos stepOffTarget;
    public int stepOffProgress;
    public int scaffoldDelay;
    @Nullable public BlockPos scaffoldTarget;
    public final DStarLitePathfinder dstar = new DStarLitePathfinder();
    public boolean dstarPrecomputed;
    public boolean dstarObserveHandoffLogged;
    public boolean dstarApproachEntryLogged;
    @Nullable public String dstarTransitionSource;
    public boolean sprintRequested;
}
