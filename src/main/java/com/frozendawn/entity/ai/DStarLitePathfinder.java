package com.frozendawn.entity.ai;

import com.frozendawn.entity.architect.BreakChoice;
import com.frozendawn.entity.architect.ArchitectWalkGeometry;
import com.frozendawn.entity.architect.ArchitectBlockEnvironment;
import com.frozendawn.entity.architect.BreakReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;

/**
 * D* Lite pathfinder for the Architect entity.
 *
 * Searches backward from the goal (target entity) toward the start (mob).
 * Maintains an incremental cost map: block changes trigger local propagation,
 * not full recompute. No heuristic bias means scaffold-over and dig-down
 * routes are discovered naturally via cost comparison.
 *
 * Reference: Koenig & Likhachev (2002), "D* Lite"
 */
public class DStarLitePathfinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DStarLitePathfinder.class);

    private static final float INF = 1e9f;
    public static final float SCAFFOLD_COST = 5.0f;
    private static final float BRIDGE_COST = 5.5f;
    private static final float BREACH_MULTIPLIER = 5.0f;
    private static final float BASE_MOVE_COST = 1.0f;
    // Small negative bias to prefer using doors and gates over equivalent detours.
    // Still keeps all edge costs positive because BASE_MOVE_COST is 1.0f.
    private static final float PASSAGE_PREFERENCE_BONUS = -0.25f;
    private static final float DIG_DOWN_BASE = 6.0f;
    private static final float DIG_DOWN_DEPTH_PENALTY = 2.0f;
    private static final int MAX_DIG_DEPTH = 20;
    private static final int MAX_BRIDGE_SPAN = 6;
    private static final float MAX_BREAKABLE_HARDNESS = 25.0f;
    private static final int SEARCH_RADIUS = 64;
    private static final int EXTERNAL_BLOCK_CHANGE_SEED_RADIUS = 3;
    private static final int LOCAL_BLOCK_CHANGE_SEED_RADIUS = 2;
    private static final int MAX_INCREMENTAL_CELLS = 65_536;
    private static final int INCREMENTAL_CELL_NEAR_CAP = (MAX_INCREMENTAL_CELLS * 3) / 4;
    private static final int INCREMENTAL_CELL_CRITICAL_CAP = (MAX_INCREMENTAL_CELLS * 9) / 10;
    private static final int OVERSIZE_REBUILD_BUDGET = 160;
    private static final float IMMEDIATE_BACKTRACK_PENALTY = 0.25f;
    /**
     * Total drop from the departure feet to the landing surface. Larger drops hurt the Architect.
     * Three keeps the lab's {@code pit_side_steps} arena honest: at four deep it is the one pit the
     * planner must refuse, forcing the side staircase. Lab runs disable fall damage, so this limit
     * is the only thing that stops a direct descent. Every shallower pit stays a direct drop.
     */
    public static final int MAX_SAFE_FALL_DISTANCE = 3;
    private static final double MIN_STANDABLE_SUPPORT_HEIGHT = 0.5;
    private static final double MAX_STEP_SUPPORT_HEIGHT = 1.0;
    private static final double SUPPORT_HEIGHT_EPSILON = 1.0e-6;
    private static final float CLIMB_TRANSITION_PENALTY = 8.0f;
    private static final double UNKNOWN_TARGET_DISTANCE = -1.0;
    private static final String ACTION_UNKNOWN = "UNKNOWN";
    private static final String TRANSITION_UNKNOWN = "UNKNOWN";
    private static final String CHANGE_SOURCE_REAL_BLOCK = "REAL_BLOCK_CHANGE";
    private static final String CHANGE_SOURCE_APPROACH_LOCAL_RESEED = "APPROACH_LOCAL_RESEED";

    // --- Step types returned to the entity ---
    public enum StepType {
        WALK,
        BREACH,
        SCAFFOLD_UP,
        SCAFFOLD_BRIDGE,
        DIG_DOWN,
        UNREACHABLE
    }

    public record NextStep(BlockPos pos, StepType type,
                           @Nullable BreakChoice breakChoice) {
        public NextStep(BlockPos pos, StepType type) {
            this(pos, type, null);
        }
        @Nullable public BlockPos breakTarget() {
            return breakChoice == null ? null : breakChoice.pos();
        }
    }

    // --- Priority queue entry with lazy deletion ---
    private record QEntry(float k1, float k2, long packed, int gen)
            implements Comparable<QEntry> {
        @Override
        public int compareTo(QEntry o) {
            int c = Float.compare(k1, o.k1);
            return c != 0 ? c : Float.compare(k2, o.k2);
        }
    }

    // --- Cell storage: packed BlockPos -> [g, rhs] ---
    private final HashMap<Long, float[]> cells = new HashMap<>();

    // --- Priority queue with lazy deletion ---
    private final PriorityQueue<QEntry> queue = new PriorityQueue<>();
    private final HashMap<Long, Integer> cellGen = new HashMap<>();
    private final HashSet<Long> inQueue = new HashSet<>();
    private int genCounter = 0;

    // --- Immune blocks (acheronite that entity discovered at runtime) ---
    private java.util.function.Predicate<BlockPos> breakPermission = pos -> true;
    public void setBreakPermission(java.util.function.Predicate<BlockPos> permission) { breakPermission = permission; }

    private final Set<Long> immuneBlocks = new HashSet<>();

    // --- D* Lite state ---
    private long goalPacked;
    private long startPacked;
    private BlockPos goalPos;
    private BlockPos startPos;
    private float km = 0;

    private int surfaceY = 64;
    private boolean initialized = false;
    private boolean searchComplete = false;
    private boolean oversizeEventLogged = false;
    private boolean reinitEventLogged = false;
    private boolean searchAborted = false;
    private boolean searchAbortEventLogged = false;
    private boolean observedWalkOnly;
    private List<BlockPos> observedDangers = List.of();

    /** A short recoverable positioning route: loaded, level walking only, with observed danger costs. */
    public void configureObservedWalk(List<BlockPos> dangers) {
        observedWalkOnly = true;
        observedDangers = dangers.stream().limit(8).map(BlockPos::immutable).toList();
    }

    // --- Public accessors ---
    public boolean isInitialized() { return initialized; }
    public boolean isSearchComplete() { return searchComplete; }
    /** True when the last search gave up because the cell map hit MAX_INCREMENTAL_CELLS. */
    public boolean isSearchAborted() { return searchAborted; }
    /** Observation only: does not pop the queue, evaluate edges, or advance the search. */
    public record DebugState(boolean initialized, boolean complete, boolean aborted,
                             int cells, int queueEntries, BlockPos goal) { }
    public DebugState debugState() {
        return new DebugState(initialized, searchComplete, searchAborted, cells.size(), queue.size(), goalPos);
    }
    public void setSurfaceY(int y) { this.surfaceY = y; }
    public void addImmuneBlock(BlockPos pos) {
        immuneBlocks.add(pos.asLong());
    }

    public void resetOverflowInvestigationEvents() {
        oversizeEventLogged = false;
        reinitEventLogged = false;
        searchAbortEventLogged = false;
    }

    // ========================================
    //  Cell accessors
    // ========================================

    private float g(long packed) {
        float[] d = cells.get(packed);
        return d != null ? d[0] : INF;
    }

    private float rhs(long packed) {
        float[] d = cells.get(packed);
        return d != null ? d[1] : INF;
    }

    private void setG(long packed, float val) {
        cells.computeIfAbsent(packed, k -> new float[]{INF, INF})[0] = val;
    }

    private void setRhs(long packed, float val) {
        cells.computeIfAbsent(packed, k -> new float[]{INF, INF})[1] = val;
    }

    // ========================================
    //  Heuristic (Manhattan)
    // ========================================

    private float heuristic(long a, long b) {
        return Math.abs(BlockPos.getX(a) - BlockPos.getX(b))
                + Math.abs(BlockPos.getY(a) - BlockPos.getY(b))
                + Math.abs(BlockPos.getZ(a) - BlockPos.getZ(b));
    }

    // ========================================
    //  Queue operations
    // ========================================

    private float[] calcKey(long packed) {
        float minGRhs = Math.min(g(packed), rhs(packed));
        return new float[]{
                minGRhs + heuristic(startPacked, packed) + km,
                minGRhs
        };
    }

    private void queueInsert(long packed) {
        float[] key = calcKey(packed);
        int gen = ++genCounter;
        cellGen.put(packed, gen);
        queue.add(new QEntry(key[0], key[1], packed, gen));
        inQueue.add(packed);
    }

    private void queueRemove(long packed) {
        cellGen.put(packed, ++genCounter);
        inQueue.remove(packed);
    }

    private boolean queueContains(long packed) {
        return inQueue.contains(packed);
    }

    @Nullable
    private QEntry queuePop() {
        while (!queue.isEmpty()) {
            QEntry entry = queue.poll();
            Integer currentGen = cellGen.get(entry.packed);
            if (currentGen != null && currentGen == entry.gen) {
                inQueue.remove(entry.packed);
                return entry;
            }
        }
        return null;
    }

    private float[] queueTopKey() {
        while (!queue.isEmpty()) {
            QEntry entry = queue.peek();
            Integer currentGen = cellGen.get(entry.packed);
            if (currentGen != null && currentGen == entry.gen) {
                return new float[]{entry.k1, entry.k2};
            }
            queue.poll(); // stale
        }
        return new float[]{INF, INF};
    }

    private boolean keyLessThan(float[] a, float[] b) {
        return a[0] < b[0] || (a[0] == b[0] && a[1] < b[1]);
    }

    // ========================================
    //  D* Lite core
    // ========================================

    public void initialize(BlockPos goal, BlockPos start, Level level) {
        cells.clear();
        queue.clear();
        cellGen.clear();
        inQueue.clear();
        genCounter = 0;
        km = 0;

        goalPos = goal;
        startPos = start;
        goalPacked = goal.asLong();
        startPacked = start.asLong();

        setRhs(goalPacked, 0);
        queueInsert(goalPacked);

        initialized = true;
        searchComplete = false;
        searchAborted = false;
    }

    /**
     * Run D* Lite search for up to maxIterations cells.
     * Returns true when search is complete.
     */
    public boolean computePartial(int maxIterations, Level level) {
        if (!initialized) return false;

        int iterations = 0;

        while (iterations < maxIterations) {
            // Enforce the cell ceiling during the search itself, not only when a block
            // change arrives. Abort rather than reinitialize: the condition that grew
            // the map (an unreachable goal inside SEARCH_RADIUS) survives a rebuild, so
            // reinitializing here would spin forever and discard a plan the walk code
            // may still be following. Callers already treat "not complete" as a cue to
            // fall back to vanilla navigation.
            if (cells.size() > MAX_INCREMENTAL_CELLS) {
                if (!searchAbortEventLogged) {
                    LOGGER.info("[Architect][DStarDiag] event=DSTAR_SEARCH_ABORT cellCount={} iterations={} maxIterations={} goal={} start={} reason=CELL_CAP",
                            cells.size(),
                            iterations,
                            maxIterations,
                            goalPos,
                            startPos);
                    searchAbortEventLogged = true;
                }
                searchAborted = true;
                searchComplete = false;
                return false;
            }

            float[] topKey = queueTopKey();
            float[] startKey = calcKey(startPacked);

            boolean topLess = keyLessThan(topKey, startKey);
            boolean startInconsistent = g(startPacked) != rhs(startPacked);

            if (!topLess && !startInconsistent) {
                searchComplete = true;
                return true;
            }

            QEntry entry = queuePop();
            if (entry == null) {
                searchComplete = true;
                return true;
            }

            long u = entry.packed;
            float[] kOld = new float[]{entry.k1, entry.k2};
            float[] kNew = calcKey(u);

            if (keyLessThan(kOld, kNew)) {
                queueInsert(u);
            } else if (g(u) > rhs(u)) {
                setG(u, rhs(u));
                for (long pred : getNeighbors(u, level)) {
                    updateVertex(pred, level);
                }
            } else {
                setG(u, INF);
                for (long pred : getNeighbors(u, level)) {
                    updateVertex(pred, level);
                }
                updateVertex(u, level);
            }

            iterations++;
        }

        return false;
    }

    private void updateVertex(long u, Level level) {
        if (u != goalPacked) {
            float minCost = INF;
            for (long neighbor : getNeighbors(u, level)) {
                float cost = edgeCost(u, neighbor, level);
                if (cost < INF) {
                    float total = cost + g(neighbor);
                    if (total < minCost) minCost = total;
                }
            }
            setRhs(u, minCost);
        }

        if (queueContains(u)) {
            queueRemove(u);
        }

        if (g(u) != rhs(u)) {
            queueInsert(u);
        }
    }

    // ========================================
    //  Next step query
    // ========================================

    /**
     * Get the next position the mob should move to from currentPos.
     * Must be called after computePartial returns true.
     */
    public NextStep getNextStep(BlockPos currentPos, Level level) {
        return getNextStep(currentPos, level, null);
    }

    public NextStep getNextStep(BlockPos currentPos, Level level,
                                @Nullable BlockPos avoidImmediateBacktrackTo) {
        return computeNextStep(currentPos, level, avoidImmediateBacktrackTo, true);
    }

    public NextStep peekNextStep(BlockPos currentPos, Level level) {
        return peekNextStep(currentPos, level, null);
    }

    public NextStep peekNextStep(BlockPos currentPos, Level level,
                                 @Nullable BlockPos avoidImmediateBacktrackTo) {
        return computeNextStep(currentPos, level, avoidImmediateBacktrackTo, false);
    }

    private NextStep computeNextStep(BlockPos currentPos, Level level,
                                     @Nullable BlockPos avoidImmediateBacktrackTo,
                                     boolean updateStartState) {
        if (!initialized || !searchComplete) {
            return new NextStep(currentPos, StepType.UNREACHABLE);
        }

        long currentPacked = currentPos.asLong();
        if (updateStartState) {
            startPacked = currentPacked;
            startPos = currentPos;
        }

        if (g(currentPacked) >= INF) {
            return new NextStep(currentPos, StepType.UNREACHABLE);
        }

        float bestCost = INF;
        long bestNeighbor = -1;
        float bestNonBacktrackCost = INF;
        long bestNonBacktrackNeighbor = -1;
        long avoidPacked = avoidImmediateBacktrackTo != null
                ? avoidImmediateBacktrackTo.asLong()
                : Long.MIN_VALUE;

        for (long neighbor : getNeighbors(currentPacked, level)) {
            float cost = edgeCost(currentPacked, neighbor, level);
            if (cost < INF) {
                float total = cost + g(neighbor);
                if (total < bestCost) {
                    bestCost = total;
                    bestNeighbor = neighbor;
                }
                if (neighbor != avoidPacked && total < bestNonBacktrackCost) {
                    bestNonBacktrackCost = total;
                    bestNonBacktrackNeighbor = neighbor;
                }
            }
        }

        if (bestNeighbor == -1 || bestCost >= INF) {
            return new NextStep(currentPos, StepType.UNREACHABLE);
        }

        if (avoidImmediateBacktrackTo != null
                && bestNeighbor == avoidPacked
                && bestNonBacktrackNeighbor != -1
                && bestNonBacktrackCost <= bestCost + IMMEDIATE_BACKTRACK_PENALTY) {
            bestNeighbor = bestNonBacktrackNeighbor;
            bestCost = bestNonBacktrackCost;
        }

        BlockPos nextPos = BlockPos.of(bestNeighbor);
        StepType type = determineStepType(currentPos, nextPos, level);

        if (type == StepType.BREACH) {
            BreakChoice breakTarget = findBreachTarget(currentPos, nextPos, level);
            return new NextStep(nextPos, type, breakTarget);
        }

        if (type == StepType.DIG_DOWN) {
            return new NextStep(nextPos, type, new BreakChoice(nextPos, BreakReason.DIG_DOWN));
        }

        return new NextStep(nextPos, type);
    }

    private StepType determineStepType(BlockPos from, BlockPos to, Level level) {
        if (ArchitectWalkGeometry.canWalkPartialTransition(level, from, to)) return StepType.WALK;
        int dy = to.getY() - from.getY();
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();

        BlockState fromFeetState = level.getBlockState(from);
        BlockState feetState = level.getBlockState(to);
        BlockState headState = level.getBlockState(to.above());
        BlockPos groundPos = to.below();
        boolean fromClimbable = isClimbable(fromFeetState);
        boolean toClimbable = isClimbable(feetState);
        boolean feetPassage = isOpenablePassage(feetState);
        boolean headPassage = isOpenablePassage(headState);
        boolean nonPassageBlocked = isNonPassageObstruction(feetState, to, level)
                || isNonPassageObstruction(headState, to.above(), level);

        if (getStepUpClearanceBreakTarget(from, to, level) != null) {
            return StepType.BREACH;
        }

        // Vertical moves (same x/z)
        if (dx == 0 && dz == 0) {
            if (dy == 1) {
                if (fromClimbable || toClimbable) return StepType.WALK;
                if (feetPassage || headPassage) return StepType.WALK;
                if (nonPassageBlocked) return StepType.BREACH;
                return StepType.SCAFFOLD_UP;
            }
            if (dy == -1) {
                if (fromClimbable || toClimbable) return StepType.WALK;
                if (nonPassageBlocked) return StepType.DIG_DOWN;
                return StepType.WALK; // fall
            }
        }

        // Treat doors and fence gates as walk-through; the Architect opens them.
        if (feetPassage || headPassage) {
            return StepType.WALK;
        }

        // Breach: non-passage obstructions at feet or head level.
        if (nonPassageBlocked) {
            return StepType.BREACH;
        }

        // Descending next to an overhang can be physically impossible even when
        // the destination feet/head cells are open. Treat that as a breach so
        // the Architect clears the lip instead of walking forever into it.
        if (getStepDownClearanceBreakTarget(from, to, level) != null) {
            return StepType.BREACH;
        }

        if (fromClimbable || toClimbable) {
            return StepType.WALK;
        }

        // stepDownCost already checked a landing within the safe fall distance.
        // An open descent is movement, even when the next cell is above the landing.
        if (dy < 0) {
            return StepType.WALK;
        }

        // Scaffold bridge: no ground below
        if (!hasStandableSupport(groundPos, level)) {
            return StepType.SCAFFOLD_BRIDGE;
        }

        return StepType.WALK;
    }

    /** Find the first solid block to break at a BREACH position (feet or head). */
    @Nullable
    private BreakChoice findBreachTarget(BlockPos from, BlockPos pos, Level level) {
        BlockPos stepUpBreakTarget = getStepUpClearanceBreakTarget(from, pos, level);
        if (stepUpBreakTarget != null) return new BreakChoice(stepUpBreakTarget, BreakReason.STEP_UP_CEILING);
        BlockState feetState = level.getBlockState(pos);
        if (isNonPassageObstruction(feetState, pos, level)) return new BreakChoice(pos, BreakReason.CORRIDOR_NODE);
        BlockState headState = level.getBlockState(pos.above());
        if (isNonPassageObstruction(headState, pos.above(), level)) return new BreakChoice(pos.above(), BreakReason.HEAD_CLEARANCE);
        BlockPos stepDownBreakTarget = getStepDownClearanceBreakTarget(from, pos, level);
        if (stepDownBreakTarget != null) return new BreakChoice(stepDownBreakTarget, BreakReason.STEP_DOWN_CLEARANCE);
        return null;
    }

    // ========================================
    //  Start/goal updates
    // ========================================

    public void updateStart(BlockPos newStart) {
        if (!initialized) return;
        long newPacked = newStart.asLong();
        if (newPacked != startPacked) {
            km += heuristic(startPacked, newPacked);
            startPacked = newPacked;
            startPos = newStart;
            // Don't mark searchComplete=false for every mob step.
            // The start key changes with km, but the cost map is still valid.
            // computePartial will re-check the termination condition.
        }
    }

    /**
     * D* Lite is designed for moving start (mob) + fixed goal.
     * Only reinitialize when the goal moves significantly (player relocated).
     * Small goal movements are handled by the existing cost map — the mob
     * will approach the old goal position which is close enough.
     */
    public boolean needsReinitialize(BlockPos newGoal) {
        if (!initialized) return true;
        return newGoal.distManhattan(goalPos) > 16;
    }

    /**
     * The normal goal tolerance is useful while travelling, but not after reaching
     * the old target. Following its cost map here can circle that abandoned spot.
     */
    public boolean isNearOutdatedGoal(BlockPos currentPos, BlockPos newGoal) {
        return initialized
                && currentPos.distSqr(goalPos) <= 16.0
                && newGoal.distSqr(goalPos) > 36.0;
    }

    // ========================================
    //  World change handler
    // ========================================

    public void onBlockChanged(BlockPos pos, Level level) {
        onBlockChanged(
                pos,
                level,
                EXTERNAL_BLOCK_CHANGE_SEED_RADIUS,
                CHANGE_SOURCE_REAL_BLOCK,
                ACTION_UNKNOWN,
                TRANSITION_UNKNOWN,
                UNKNOWN_TARGET_DISTANCE
        );
    }

    public void onLocalBlockChanged(BlockPos pos, Level level) {
        onBlockChanged(
                pos,
                level,
                LOCAL_BLOCK_CHANGE_SEED_RADIUS,
                CHANGE_SOURCE_APPROACH_LOCAL_RESEED,
                ACTION_UNKNOWN,
                TRANSITION_UNKNOWN,
                UNKNOWN_TARGET_DISTANCE
        );
    }

    public void onBlockChanged(
            BlockPos pos,
            Level level,
            String changeSource,
            String action,
            String transitionSource,
            double targetDistance
    ) {
        onBlockChanged(pos, level, EXTERNAL_BLOCK_CHANGE_SEED_RADIUS, changeSource, action, transitionSource, targetDistance);
    }

    public void onLocalBlockChanged(
            BlockPos pos,
            Level level,
            String changeSource,
            String action,
            String transitionSource,
            double targetDistance
    ) {
        onBlockChanged(pos, level, LOCAL_BLOCK_CHANGE_SEED_RADIUS, changeSource, action, transitionSource, targetDistance);
    }

    private void onBlockChanged(
            BlockPos pos,
            Level level,
            int seedRadius,
            String changeSource,
            String action,
            String transitionSource,
            double targetDistance
    ) {
        if (!initialized) return;
        if (!isWithinSearchRadius(pos)) return;

        if (cells.size() > MAX_INCREMENTAL_CELLS) {
            if (!oversizeEventLogged) {
                LOGGER.info("[Architect][DStarDiag] event=DSTAR_OVERSIZE_FIRST cellCount={} searchComplete={} targetDistance={} initialized={} action={} transitionSource={} seedRadius={} changeSource={}",
                        cells.size(),
                        searchComplete,
                        targetDistance,
                        initialized,
                        action,
                        transitionSource,
                        seedRadius,
                        changeSource);
                oversizeEventLogged = true;
            }
            if (!reinitEventLogged) {
                LOGGER.info("[Architect][DStarDiag] event=DSTAR_REINIT_FIRST cellCount={} searchComplete={} targetDistance={} initialized={} action={} transitionSource={} seedRadius={} changeSource={} reason=OVERSIZE_MAP",
                        cells.size(),
                        searchComplete,
                        targetDistance,
                        initialized,
                        action,
                        transitionSource,
                        seedRadius,
                        changeSource);
                reinitEventLogged = true;
            }
            initialize(goalPos, startPos, level);
            // Avoid long server stalls on oversized maps; rebuild incrementally.
            computePartial(OVERSIZE_REBUILD_BUDGET, level);
            return;
        }

        int effectiveSeedRadius = seedRadius;
        int cellCount = cells.size();
        if (cellCount > INCREMENTAL_CELL_CRITICAL_CAP) {
            effectiveSeedRadius = 1;
        } else if (cellCount > INCREMENTAL_CELL_NEAR_CAP) {
            effectiveSeedRadius = Math.max(1, seedRadius - 1);
        }

        // Seed the changed block and nearby cells into the incremental search.
        // External/player world changes use a larger radius; local Architect-driven
        // churn uses a tighter radius to avoid ballooning the cell map mid-chase.
        int px = pos.getX(), py = pos.getY(), pz = pos.getZ();
        int radiusSqr = effectiveSeedRadius * effectiveSeedRadius;
        for (int dx = -effectiveSeedRadius; dx <= effectiveSeedRadius; dx++) {
            for (int dy = -effectiveSeedRadius; dy <= effectiveSeedRadius; dy++) {
                for (int dz = -effectiveSeedRadius; dz <= effectiveSeedRadius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSqr) {
                        continue;
                    }
                    int x = px + dx;
                    int y = py + dy;
                    int z = pz + dz;
                    if (!isWithinSearchRadius(x, y, z)) continue;
                    long packed = BlockPos.asLong(x, y, z);
                    if (!shouldSeedChangedCell(packed, dx, dy, dz)) {
                        continue;
                    }
                    updateVertex(packed, level);
                }
            }
        }
        // Step-up edges also depend on the departure ceiling, two cells overhead.
        // Preserve that dependency when map pressure shrinks the generic seed radius.
        long departurePacked = pos.below(2).asLong();
        if (effectiveSeedRadius < 2 && cells.containsKey(departurePacked)) {
            updateVertex(departurePacked, level);
        }
        searchComplete = false;
    }

    private boolean shouldSeedChangedCell(long packed, int dx, int dy, int dz) {
        if (cells.containsKey(packed)) {
            return true;
        }
        // Limit new cell creation to the immediate changed neighborhood.
        int manhattan = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
        return manhattan <= 1;
    }

    private boolean isWithinSearchRadius(BlockPos pos) {
        return isWithinSearchRadius(pos.getX(), pos.getY(), pos.getZ());
    }

    private boolean isWithinSearchRadius(int x, int y, int z) {
        int gx = BlockPos.getX(goalPacked);
        int gy = BlockPos.getY(goalPacked);
        int gz = BlockPos.getZ(goalPacked);
        int dx = x - gx;
        int dy = y - gy;
        int dz = z - gz;
        return dx * dx + dy * dy + dz * dz <= SEARCH_RADIUS * SEARCH_RADIUS;
    }

    // ========================================
    //  Graph: neighbors
    // ========================================

    private static final int[][] HORIZONTAL = {{1,0}, {-1,0}, {0,1}, {0,-1}};

    private List<Long> getNeighbors(long packed, Level level) {
        int x = BlockPos.getX(packed);
        int y = BlockPos.getY(packed);
        int z = BlockPos.getZ(packed);
        int gx = BlockPos.getX(goalPacked);
        int gy = BlockPos.getY(goalPacked);
        int gz = BlockPos.getZ(goalPacked);

        List<Long> neighbors = new ArrayList<>(14);

        for (int[] h : HORIZONTAL) {
            int nx = x + h[0], nz = z + h[1];
            // Flat walk
            addIfInRange(neighbors, nx, y, nz, gx, gy, gz);
            // Step up
            addIfInRange(neighbors, nx, y + 1, nz, gx, gy, gz);
            // Step down
            addIfInRange(neighbors, nx, y - 1, nz, gx, gy, gz);
        }

        // Pillar up
        addIfInRange(neighbors, x, y + 1, z, gx, gy, gz);
        // Fall / dig down
        addIfInRange(neighbors, x, y - 1, z, gx, gy, gz);

        return neighbors;
    }

    private void addIfInRange(List<Long> list, int x, int y, int z,
                              int gx, int gy, int gz) {
        int dx = x - gx, dy = y - gy, dz = z - gz;
        if (dx * dx + dy * dy + dz * dz <= SEARCH_RADIUS * SEARCH_RADIUS) {
            list.add(BlockPos.asLong(x, y, z));
        }
    }

    // ========================================
    //  Graph: edge costs
    // ========================================

    private float edgeCost(long fromPacked, long toPacked, Level level) {
        if (observedWalkOnly) {
            BlockPos from = BlockPos.of(fromPacked), to = BlockPos.of(toPacked);
            if (from.getY() != to.getY() || to.distSqr(goalPos) > 24 * 24
                    || !level.hasChunkAt(to) || !level.hasChunkAt(from)
                    || !level.getBlockState(to.below()).isFaceSturdy(level, to.below(), Direction.UP)) return INF;
            for (int y = 0; y < 3; y++) {
                var block = to.above(y); var state = level.getBlockState(block);
                if (!state.getCollisionShape(level, block).isEmpty() || !state.getFluidState().isEmpty()
                        || state.is(BlockTags.FIRE)) return INF;
            }
            var ground = level.getBlockState(to.below());
            if (ground.is(Blocks.MAGMA_BLOCK) || ground.is(Blocks.CACTUS)) return INF;
            float penalty = 0;
            for (BlockPos danger : observedDangers) if (danger.distSqr(to) <= 9) penalty += 4;
            return BASE_MOVE_COST + Math.min(16, penalty);
        }
        int fx = BlockPos.getX(fromPacked), fy = BlockPos.getY(fromPacked), fz = BlockPos.getZ(fromPacked);
        int tx = BlockPos.getX(toPacked), ty = BlockPos.getY(toPacked), tz = BlockPos.getZ(toPacked);
        BlockPos fromPos = new BlockPos(fx, fy, fz);
        BlockPos toPos = new BlockPos(tx, ty, tz);

        int dx = tx - fx, dy = ty - fy, dz = tz - fz;
        boolean climbTransition = isClimbable(level.getBlockState(fromPos))
                || isClimbable(level.getBlockState(toPos));

        // --- Vertical (same column) ---
        if (dx == 0 && dz == 0) {
            if (dy == 1) {
                return climbTransition
                        ? climbTransitionCost(tx, ty, tz, level)
                        : scaffoldUpCost(fx, fy, fz, tx, ty, tz, level);
            }
            if (dy == -1) {
                return climbTransition
                        ? climbTransitionCost(tx, ty, tz, level)
                        : verticalDownCost(tx, ty, tz, level);
            }
            return INF;
        }

        // Must be cardinal horizontal
        if (Math.abs(dx) + Math.abs(dz) != 1) return INF;

        if (ArchitectWalkGeometry.canWalkPartialTransition(level, fromPos, toPos)) return BASE_MOVE_COST;

        float cost;
        if (dy == 0) {
            cost = flatMoveCost(tx, ty, tz, level);
        } else if (dy == 1) {
            cost = stepUpCost(fx, fy, fz, tx, ty, tz, level);
        } else if (dy == -1) {
            cost = stepDownCost(fx, fy, fz, tx, ty, tz, level);
        } else {
            return INF;
        }
        if (cost >= INF) return INF;
        if (climbTransition) {
            cost += CLIMB_TRANSITION_PENALTY;
        }
        return cost;
    }

    private float climbTransitionCost(int tx, int ty, int tz, Level level) {
        float cost = BASE_MOVE_COST + CLIMB_TRANSITION_PENALTY;
        BlockPos toPos = new BlockPos(tx, ty, tz);
        if (hasHazardAtOrAbove(toPos, level)) return INF;

        BlockState feetState = level.getBlockState(toPos);
        if (isOpenablePassage(feetState)) {
            cost += PASSAGE_PREFERENCE_BONUS;
        } else if (isNonPassageObstruction(feetState, toPos, level)) {
            float breach = breachCost(feetState, toPos, level);
            if (breach >= INF) return INF;
            cost += breach;
        }

        BlockState headState = level.getBlockState(toPos.above());
        if (isOpenablePassage(headState)) {
            cost += PASSAGE_PREFERENCE_BONUS;
        } else if (isNonPassageObstruction(headState, toPos.above(), level)) {
            float breach = breachCost(headState, toPos.above(), level);
            if (breach >= INF) return INF;
            cost += breach;
        }

        return cost;
    }

    private float flatMoveCost(int tx, int ty, int tz, Level level) {
        float cost = BASE_MOVE_COST;

        BlockPos toPos = new BlockPos(tx, ty, tz);
        if (hasHazardAtOrAbove(toPos, level)) return INF;
        BlockState feetState = level.getBlockState(toPos);
        if (isOpenablePassage(feetState)) {
            cost += PASSAGE_PREFERENCE_BONUS;
        } else if (isNonPassageObstruction(feetState, toPos, level)) {
            float bc = breachCost(feetState, toPos, level);
            if (bc >= INF) return INF;
            cost += bc;
        }

        BlockState headState = level.getBlockState(toPos.above());
        if (isOpenablePassage(headState)) {
            cost += PASSAGE_PREFERENCE_BONUS;
        } else if (isNonPassageObstruction(headState, toPos.above(), level)) {
            float bc = breachCost(headState, toPos.above(), level);
            if (bc >= INF) return INF;
            cost += bc;
        }

        if (!hasStandableSupport(toPos.below(), level)) {
            if (isDangerousBelow(toPos, level)) return INF;
            if (!isWithinBridgeSpan(toPos, level)) return INF;
            cost += BRIDGE_COST;
        }

        return cost;
    }

    private float stepUpCost(int fx, int fy, int fz, int tx, int ty, int tz, Level level) {
        // Step block (at target x/z, from y) must be solid
        BlockPos stepBlock = new BlockPos(tx, fy, tz);
        if (!hasStandableSupport(stepBlock, level)) return INF;

        float cost = BASE_MOVE_COST * 1.5f;

        BlockPos departureCeiling = getStepUpClearanceBreakTarget(
                new BlockPos(fx, fy, fz), new BlockPos(tx, ty, tz), level);
        if (departureCeiling != null) {
            float bc = breachCost(level.getBlockState(departureCeiling), departureCeiling, level);
            if (bc >= INF) return INF;
            cost += bc;
        }

        BlockPos toPos = new BlockPos(tx, ty, tz);
        if (hasHazardAtOrAbove(toPos, level)) return INF;
        BlockState feetState = level.getBlockState(toPos);
        if (isOpenablePassage(feetState)) {
            cost += PASSAGE_PREFERENCE_BONUS;
        } else if (isNonPassageObstruction(feetState, toPos, level)) {
            float bc = breachCost(feetState, toPos, level);
            if (bc >= INF) return INF;
            cost += bc;
        }

        BlockState headState = level.getBlockState(toPos.above());
        if (isOpenablePassage(headState)) {
            cost += PASSAGE_PREFERENCE_BONUS;
        } else if (isNonPassageObstruction(headState, toPos.above(), level)) {
            float bc = breachCost(headState, toPos.above(), level);
            if (bc >= INF) return INF;
            cost += bc;
        }

        return cost;
    }

    private float stepDownCost(int fx, int fy, int fz, int tx, int ty, int tz, Level level) {
        float cost = BASE_MOVE_COST;

        BlockPos fromPos = new BlockPos(fx, fy, fz);
        BlockPos toPos = new BlockPos(tx, ty, tz);
        if (hasHazardAtOrAbove(toPos, level)) return INF;
        // Head at target = to.above() = from.y level
        BlockState headState = level.getBlockState(toPos.above());
        if (isOpenablePassage(headState)) {
            cost += PASSAGE_PREFERENCE_BONUS;
        } else if (isNonPassageObstruction(headState, toPos.above(), level)) {
            float bc = breachCost(headState, toPos.above(), level);
            if (bc >= INF) return INF;
            cost += bc;
        }

        BlockState feetState = level.getBlockState(toPos);
        if (isOpenablePassage(feetState)) {
            cost += PASSAGE_PREFERENCE_BONUS;
        } else if (isNonPassageObstruction(feetState, toPos, level)) {
            float bc = breachCost(feetState, toPos, level);
            if (bc >= INF) return INF;
            cost += bc;
        }

        BlockPos stepDownBreakTarget = getStepDownClearanceBreakTarget(fromPos, toPos, level);
        if (stepDownBreakTarget != null) {
            float bc = breachCost(level.getBlockState(stepDownBreakTarget), stepDownBreakTarget, level);
            if (bc >= INF) return INF;
            cost += bc;
        }

        if (!hasStandableSupport(toPos.below(), level)) {
            if (isDangerousBelow(toPos, level)) return INF;
            // Check for ground within safe fall distance
            for (int dy = 2; dy <= MAX_SAFE_FALL_DISTANCE; dy++) {
                BlockPos belowPos = toPos.below(dy);
                BlockState below = level.getBlockState(belowPos);
                if (isHazardous(below)) return INF;
                if (hasStandableSupport(belowPos, level)) {
                    return cost + dy * 0.5f;
                }
            }
            return INF;
        }

        return cost;
    }

    private float scaffoldUpCost(int fx, int fy, int fz, int tx, int ty, int tz, Level level) {
        BlockPos fromPos = new BlockPos(fx, fy, fz);

        // Must be adjacent to a wall at MOB FEET level.
        // On flat terrain, feet level is air → no scaffold.
        // Next to wall, feet level has the wall → scaffold allowed.
        boolean hasWall = false;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(fromPos.relative(dir)).isSolid()) {
                hasWall = true;
                break;
            }
        }
        if (!hasWall) return INF;

        float cost = SCAFFOLD_COST;

        BlockPos toPos = new BlockPos(tx, ty, tz);
        if (hasHazardAtOrAbove(toPos, level)) return INF;
        BlockState feetState = level.getBlockState(toPos);
        if (isOpenablePassage(feetState)) {
            cost += PASSAGE_PREFERENCE_BONUS;
        } else if (isNonPassageObstruction(feetState, toPos, level)) {
            float bc = breachCost(feetState, toPos, level);
            if (bc >= INF) return INF;
            cost += bc;
        }

        BlockState headState = level.getBlockState(toPos.above());
        if (isOpenablePassage(headState)) {
            cost += PASSAGE_PREFERENCE_BONUS;
        } else if (isNonPassageObstruction(headState, toPos.above(), level)) {
            float bc = breachCost(headState, toPos.above(), level);
            if (bc >= INF) return INF;
            cost += bc;
        }

        return cost;
    }

    private float verticalDownCost(int tx, int ty, int tz, Level level) {
        BlockPos toPos = new BlockPos(tx, ty, tz);
        BlockState toState = level.getBlockState(toPos);
        if (isHazardous(toState)) return INF;

        if (!isNonPassageObstruction(toState, toPos, level)) {
            // Falling
            BlockPos groundPos = toPos.below();
            BlockState ground = level.getBlockState(groundPos);
            if (isHazardous(ground)) return INF;
            if (hasStandableSupport(groundPos, level)) return BASE_MOVE_COST;
            for (int dy = 2; dy <= MAX_SAFE_FALL_DISTANCE; dy++) {
                BlockPos belowPos = toPos.below(dy);
                BlockState below = level.getBlockState(belowPos);
                if (isHazardous(below)) return INF;
                if (hasStandableSupport(belowPos, level)) {
                    return BASE_MOVE_COST + dy * 0.5f;
                }
            }
            return INF;
        }

        // Dig down through solid block
        if (wouldExposeHazard(toPos, level)) return INF;
        if (isUnbreakable(toState, toPos, level)) return INF;

        int depthBelow = surfaceY - ty;
        if (depthBelow > MAX_DIG_DEPTH) return INF;

        float depthPenalty = Math.max(0, depthBelow) * DIG_DOWN_DEPTH_PENALTY;
        float breakTime = ArchitectBlockBreaker.getEffectiveBreakTime(toState, toPos, level);
        float digDownCost = DIG_DOWN_BASE + breakTime * BREACH_MULTIPLIER + depthPenalty;
        return ArchitectBreakPolicy.applyLastResortPenalty(toState, digDownCost);
    }

    // ========================================
    //  Block cost helpers
    // ========================================

    private float breachCost(BlockState state, BlockPos pos, Level level) {
        if (wouldExposeHazard(pos, level)) return INF;
        if (isUnbreakable(state, pos, level)) return INF;
        float breakTime = ArchitectBlockBreaker.getEffectiveBreakTime(state, pos, level);
        float baseCost = breakTime * BREACH_MULTIPLIER;
        return ArchitectBreakPolicy.applyLastResortPenalty(state, baseCost);
    }

    @Nullable
    private BlockPos getStepUpClearanceBreakTarget(BlockPos from, BlockPos to, Level level) {
        if (to.getY() != from.getY() + 1
                || Math.abs(to.getX() - from.getX()) + Math.abs(to.getZ() - from.getZ()) != 1) {
            return null;
        }
        BlockPos ceiling = from.above(2);
        return isNonPassageObstruction(level.getBlockState(ceiling), ceiling, level) ? ceiling : null;
    }

    @Nullable
    private BlockPos getStepDownClearanceBreakTarget(BlockPos from, BlockPos to, Level level) {
        int dx = Integer.compare(to.getX(), from.getX());
        int dz = Integer.compare(to.getZ(), from.getZ());
        if (to.getY() != from.getY() - 1 || Math.abs(dx) + Math.abs(dz) != 1) {
            return null;
        }

        BlockPos upperFront = to.above().above();
        if (isNonPassageObstruction(level.getBlockState(upperFront), upperFront, level)) {
            return upperFront;
        }

        return null;
    }

    private boolean isUnbreakable(BlockState state, BlockPos pos, Level level) {
        if (immuneBlocks.contains(pos.asLong()) || !breakPermission.test(pos)) return true;
        if (ArchitectBreakPolicy.isProtectedBlock(state)) return true;
        float hardness = state.getDestroySpeed(level, pos);
        return hardness < 0 || hardness >= MAX_BREAKABLE_HARDNESS;
    }

    private boolean isOpenablePassage(BlockState state) {
        return ArchitectBlockEnvironment.isOpenablePassage(state);
    }

    private boolean isClimbable(BlockState state) {
        return state.is(BlockTags.CLIMBABLE);
    }

    private boolean isNonPassageObstruction(BlockState state, BlockPos pos, Level level) {
        if (isOpenablePassage(state)) {
            return false;
        }
        return ArchitectBreakPolicy.isObstructiveForArchitect(state, level, pos);
    }

    private boolean hasStandableSupport(BlockPos pos, Level level) {
        BlockState state = level.getBlockState(pos);
        if (isClimbable(state)) {
            return false;
        }
        if (isOpenablePassage(state)) {
            return false;
        }
        if (state.isFaceSturdy(level, pos, Direction.UP)) {
            return true;
        }
        VoxelShape supportShape = state.getBlockSupportShape(level, pos);
        if (supportShape.isEmpty()) {
            return false;
        }
        double supportHeight = supportShape.max(Direction.Axis.Y);
        // Graph Y coordinates advance in whole blocks. A fence or wall reaches
        // 1.5 blocks high, so treating it as a one-block step produces a route
        // whose destination height the movement controller cannot occupy.
        return supportHeight >= MIN_STANDABLE_SUPPORT_HEIGHT
                && supportHeight <= MAX_STEP_SUPPORT_HEIGHT + SUPPORT_HEIGHT_EPSILON;
    }

    private boolean isWithinBridgeSpan(BlockPos pos, Level level) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int i = 1; i <= MAX_BRIDGE_SPAN; i++) {
                BlockPos probe = pos.relative(dir, i);
                if (hasStandableSupport(probe, level)
                        || hasStandableSupport(probe.below(), level)) {
                    return true;
                }
            }
        }
        for (int dy = 1; dy <= MAX_BRIDGE_SPAN; dy++) {
            if (hasStandableSupport(pos.below(dy), level)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHazardAtOrAbove(BlockPos pos, Level level) {
        return isHazardous(level.getBlockState(pos))
                || isHazardous(level.getBlockState(pos.above()));
    }

    private boolean isDangerousBelow(BlockPos pos, Level level) {
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos belowPos = pos.below(dy);
            BlockState below = level.getBlockState(belowPos);
            if (isHazardous(below)) return true;
            if (hasStandableSupport(belowPos, level)) return false;
        }
        return false;
    }

    private boolean wouldExposeHazard(BlockPos breakPos, Level level) {
        for (Direction dir : Direction.values()) {
            if (isHazardous(level.getBlockState(breakPos.relative(dir)))) {
                return true;
            }
        }
        return false;
    }

    private boolean isHazardous(BlockState state) {
        return state.is(Blocks.LAVA)
                || state.getFluidState().is(FluidTags.LAVA)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE);
    }

    // ========================================
    //  Debug
    // ========================================

    public float getStartG() { return g(startPacked); }
    public int getCellCount() { return cells.size(); }

    public void cleanup() {
        cells.clear();
        queue.clear();
        cellGen.clear();
        inQueue.clear();
        immuneBlocks.clear();
        initialized = false;
        searchComplete = false;
        searchAborted = false;
        oversizeEventLogged = false;
        reinitEventLogged = false;
        searchAbortEventLogged = false;
    }
}
