package com.frozendawn.entity.ai;

import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Custom A* node evaluator for the Architect entity.
 *
 * Three traversal strategies fed into A* so it makes all decisions via cost:
 *
 *   BREACH — breakable block. Cost = block hardness * multiplier.
 *            A* decides "mine through or walk around" via cost comparison.
 *
 *   SCAFFOLD_UP — pillar up next to a wall. Cost = SCAFFOLD_COST.
 *                 Requires adjacent wall and 2-high air clearance.
 *
 *   SCAFFOLD_BRIDGE — place ice and walk across a gap. Cost = BRIDGE_COST.
 *                     Chains across multiple air blocks by recognizing planned
 *                     bridge predecessors. Bounded by MAX_BRIDGE_SPAN from
 *                     nearest solid surface (per-node, not per-chain — a bridge
 *                     can span up to ~12 blocks if both ends are near walls).
 *                     Rejected over void/lava.
 *
 *   DIG_DOWN — mine downward through solid blocks. Cost ramps with depth
 *              from a fixed surface reference Y (entity spawn Y, persisted
 *              in NBT) to prevent unlimited downward exploration.
 *
 * The allowBreach flag remains as a safety net against A* explosion during
 * non-approach actions. Dig-down is also gated by allowBreach to avoid
 * unnecessary node generation during retreat/trap pathfinding.
 */
public class ArchitectNodeEvaluator extends WalkNodeEvaluator {

    private static final float MAX_BREAKABLE_HARDNESS = 25.0F;
    public static final float SCAFFOLD_COST = 5.0F;
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchitectNodeEvaluator.class);
    private static final float BRIDGE_COST = 5.5F;
    private static final int MAX_BRIDGE_SPAN = 6;
    private static final float DIG_DOWN_BASE_COST = 6.0F;
    private static final float DIG_DOWN_DEPTH_PENALTY = 2.0F;
    private static final int MAX_DIG_DEPTH = 20;

    /** Converts break-time-in-seconds to A* cost units.
     *  At 5.0: a 1-second break = 5.0 cost ≈ walking 5 blocks. */
    private static final float BREACH_TIME_MULTIPLIER = 5.0F;

    @Nullable
    private PlayerPlacedBlockTracker tracker;

    private final Set<Long> immuneBlocks = new java.util.HashSet<>();

    /** Safety net: when false, getPathTypeOfMob returns BLOCKED for breakable blocks.
     *  Also gates dig-down node generation. Prevents A* explosion during
     *  non-approach actions (trap, retreat, etc.). */
    private boolean allowBreach = false;

    /** Fixed reference Y for depth penalty calculation. Set from the entity's
     *  persisted surface Y (spawn Y), NOT current Y. This prevents the penalty
     *  from resetting as the mob digs deeper. */
    private int surfaceY = 64;

    public void setAllowBreach(boolean allow) {
        this.allowBreach = allow;
    }

    public void setSurfaceY(int y) {
        this.surfaceY = y;
    }

    public void addImmuneBlock(BlockPos pos) {
        immuneBlocks.add(pos.asLong());
    }

    @Override
    public void prepare(net.minecraft.world.level.PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        // Auto-detect: enable BREACH only during APPROACH action.
        // This replaces the old setAllowBreach() call which was overwritten by
        // prepare() running after the flag was set (prepare runs inside moveTo).
        if (mob instanceof com.frozendawn.entity.ArchitectEntity architect) {
            this.allowBreach = architect.getCurrentAction()
                    == com.frozendawn.entity.ArchitectEntity.ACTION_APPROACH
                    || architect.isProbing();
            this.surfaceY = architect.getSurfaceY();
        } else {
            this.allowBreach = false;
        }
        if (mob.level() instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            tracker = PlayerPlacedBlockTracker.get(server);
        } else {
            tracker = null;
        }
    }

    @Override
    public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
        PathType vanillaType = super.getPathTypeOfMob(context, x, y, z, mob);
        if (vanillaType != PathType.BLOCKED) {
            return vanillaType;
        }

        if (!allowBreach) return PathType.BLOCKED;

        long packed = BlockPos.asLong(x, y, z);
        if (immuneBlocks.contains(packed)) return PathType.BLOCKED;

        BlockState state = context.getBlockState(new BlockPos(x, y, z));

        if (isAcheroniteOrTransponder(state)) return PathType.BLOCKED;

        float hardness = state.getDestroySpeed(context.level(), new BlockPos(x, y, z));
        if (hardness < 0 || hardness >= MAX_BREAKABLE_HARDNESS) return PathType.BLOCKED;

        return PathType.BREACH;
    }

    @Override
    public int getNeighbors(Node[] results, Node node) {
        int count = super.getNeighbors(results, node);

        if (this.currentContext == null || this.mob == null) return count;

        // Override flat BREACH costs with tool-aware break times
        for (int i = 0; i < count; i++) {
            if (results[i].type == PathType.BREACH) {
                BlockPos pos = results[i].asBlockPos();
                BlockState state = this.currentContext.getBlockState(pos);
                float breakTime = ArchitectBlockBreaker.getEffectiveBreakTime(
                        state, pos, this.currentContext.level());
                float breachCost = breakTime * BREACH_TIME_MULTIPLIER;
                results[i].costMalus = Math.max(results[i].costMalus, breachCost);
            }
        }

        BlockPos nodePos = node.asBlockPos();

        // Scaffold UP — pillar up next to a wall
        count = tryAddScaffoldUpNode(results, count, node);

        // Scaffold BRIDGE — walk across air gaps on placed ice
        count = tryAddScaffoldBridgeNodes(results, count, node);

        // Dig DOWN — gated by allowBreach to avoid unnecessary node generation
        // during non-approach pathfinding. Cost ramps with depth from surfaceY.
        if (allowBreach) {
            count = tryAddDigDownNode(results, count, nodePos);
        }

        // DROP-THROUGH: when expanding a BREACH node (inside solid) and the block
        // below is air, add a drop node. This handles the ceiling case — mob breaches
        // through the floor/ceiling and falls into the room below. Without this, A*
        // dead-ends at the BREACH node because vanilla can't generate neighbors from
        // inside a solid block. Only fires for "breach into air" (ceiling over room),
        // not "breach into solid" (multi-layer dig-down, which chains normally).
        if (allowBreach && count < results.length
                && node.type == PathType.BREACH
                && this.currentContext.getBlockState(nodePos).isSolid()) {
            BlockPos below = nodePos.below();
            BlockState belowState = this.currentContext.getBlockState(below);
            if (belowState.isAir() || !belowState.isSolid()) {
                Node dropNode = this.getNode(below.getX(), below.getY(), below.getZ());
                if (!dropNode.closed) {
                    dropNode.type = PathType.WALKABLE;
                    dropNode.costMalus = Math.max(dropNode.costMalus, 2.0F);
                    results[count] = dropNode;
                    count++;
                    LOGGER.info("[NodeEval] DROP-THROUGH from BREACH at " + nodePos
                            + " to " + below);
                }
            }
        }

        return count;
    }

    // ========================
    //  SCAFFOLD UP
    // ========================

    /**
     * Pillar-up chaining: A* plans multiple scaffold-up nodes before any ice is
     * placed. The second scaffold level has air below (ice not placed yet), so a
     * naive solid-ground check fails.
     *
     * Solution: accept the current node as valid pillar origin if EITHER:
     *   (a) there's solid ground below (first level), OR
     *   (b) the current node is a planned scaffold node — identified by
     *       air below, costMalus >= SCAFFOLD_COST (type is unreliable —
     *       vanilla may overwrite WALKABLE → OPEN during expansion).
     */
    private int tryAddScaffoldUpNode(Node[] results, int count, Node node) {
        if (count >= results.length || this.currentContext == null) return count;

        BlockPos nodePos = node.asBlockPos();
        BlockPos above = nodePos.above();
        BlockPos twoAbove = nodePos.above(2);

        if (!this.currentContext.getBlockState(above).isAir()) return count;
        // Allow scaffold-up even if twoAbove is a breakable block (entity will mine headroom).
        // This handles roofs overhanging walls — the architect can pillar up AND breach the ceiling.
        BlockState twoAboveState = this.currentContext.getBlockState(twoAbove);
        if (!twoAboveState.isAir()) {
            if (!allowBreach) return count;
            if (isAcheroniteOrTransponder(twoAboveState)) return count;
            float hardness = twoAboveState.getDestroySpeed(this.currentContext.level(), twoAbove);
            if (hardness < 0 || hardness >= MAX_BREAKABLE_HARDNESS) return count;
            // Breakable ceiling — allow scaffold with extra cost
        }

        boolean onSolidGround = this.currentContext.getBlockState(nodePos.below()).isSolid();

        // Recognize planned scaffold predecessors: air below, with scaffold-level
        // cost. This lets A* chain pillar-up across multiple levels.
        // Use costMalus (never decreases) instead of type (vanilla may overwrite
        // WALKABLE → OPEN during node expansion).
        boolean onPlannedScaffold = !onSolidGround
                && node.costMalus >= SCAFFOLD_COST
                && this.currentContext.getBlockState(nodePos).isAir();

        if (!onSolidGround && !onPlannedScaffold) return count;

        // Check for adjacent wall at the target level. The cresting case (pillaring
        // to one above a wall top) is only allowed when already on a planned scaffold
        // node — this prevents flat ground (grass next to grass) from triggering
        // scaffold-up everywhere.
        boolean hasAdjacentWall = false;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (this.currentContext.getBlockState(above.relative(dir)).isSolid()) {
                hasAdjacentWall = true;
                break;
            }
            // Cresting: allow only when already elevated on scaffold
            if (onPlannedScaffold
                    && this.currentContext.getBlockState(nodePos.relative(dir)).isSolid()) {
                hasAdjacentWall = true;
                break;
            }
        }
        if (!hasAdjacentWall) return count;

        Node scaffoldNode = this.getNode(above.getX(), above.getY(), above.getZ());
        if (scaffoldNode.closed) return count; // Don't modify already-explored nodes
        scaffoldNode.type = PathType.WALKABLE;
        scaffoldNode.costMalus = Math.max(scaffoldNode.costMalus, SCAFFOLD_COST);
        results[count] = scaffoldNode;
        return count + 1;
    }

    // ========================
    //  SCAFFOLD BRIDGE
    // ========================

    /**
     * Generate bridge nodes for horizontal air gaps.
     *
     * Bridge chaining: A* plans ahead of execution — ice hasn't been placed yet.
     * When expanding a bridge node, nodePos.below() is air, so a naive solid-ground
     * check fails and bridges can't extend past 1 block.
     *
     * Solution: accept the current node as valid bridge origin if EITHER:
     *   (a) there's solid ground below (normal case), OR
     *   (b) the current node is itself a planned bridge node — identified by
     *       air below, costMalus >= BRIDGE_COST (type is unreliable —
     *       vanilla may overwrite WALKABLE → OPEN during expansion).
     *
     * Chain length is bounded by isWithinBridgeSpan() which checks proximity to
     * solid surfaces per-node. A bridge can span ~12 blocks if both ends are
     * near walls (6 from each side).
     */
    private int tryAddScaffoldBridgeNodes(Node[] results, int count, Node node) {
        if (this.currentContext == null) return count;

        BlockPos nodePos = node.asBlockPos();
        boolean onSolidGround = this.currentContext.getBlockState(nodePos.below()).isSolid();

        // Recognize planned bridge predecessors: air below, with bridge-level
        // cost. This lets A* chain bridges across multi-block gaps.
        // Use costMalus (never decreases) instead of type (vanilla may overwrite
        // WALKABLE → OPEN during node expansion).
        boolean onPlannedBridge = !onSolidGround
                && node.costMalus >= BRIDGE_COST
                && this.currentContext.getBlockState(nodePos).isAir();

        if (!onSolidGround && !onPlannedBridge) return count;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (count >= results.length) break;

            BlockPos neighbor = nodePos.relative(dir);
            BlockState neighborState = this.currentContext.getBlockState(neighbor);
            BlockState belowNeighbor = this.currentContext.getBlockState(neighbor.below());

            // Only bridge over air gaps — solid neighbors handled by vanilla
            if (!neighborState.isAir()) continue;
            // If there's ground below neighbor, vanilla walking handles it
            if (belowNeighbor.isSolid()) continue;

            // Reject: over void
            if (neighbor.getY() <= this.currentContext.level().getMinBuildHeight() + 1) continue;

            // Reject: dangerous blocks below (lava, fire)
            if (isDangerousBelow(neighbor)) continue;

            // Bound: must be within MAX_BRIDGE_SPAN of a solid surface
            if (!isWithinBridgeSpan(neighbor)) continue;

            // Need 2-high clearance for entity to walk through
            if (!this.currentContext.getBlockState(neighbor.above()).isAir()) continue;

            Node bridgeNode = this.getNode(neighbor.getX(), neighbor.getY(), neighbor.getZ());
            if (bridgeNode.closed) continue; // Don't modify already-explored nodes
            bridgeNode.type = PathType.WALKABLE;
            bridgeNode.costMalus = Math.max(bridgeNode.costMalus, BRIDGE_COST);
            results[count] = bridgeNode;
            count++;
        }

        return count;
    }

    private boolean isDangerousBelow(BlockPos pos) {
        for (int dy = 1; dy <= 3; dy++) {
            BlockState below = this.currentContext.getBlockState(pos.below(dy));
            if (below.is(Blocks.LAVA) || below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE)) {
                return true;
            }
            if (below.isSolid()) return false;
        }
        return false;
    }

    private boolean isWithinBridgeSpan(BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int i = 1; i <= MAX_BRIDGE_SPAN; i++) {
                BlockPos probe = pos.relative(dir, i);
                if (this.currentContext.getBlockState(probe).isSolid()
                        || this.currentContext.getBlockState(probe.below()).isSolid()) {
                    return true;
                }
            }
        }
        for (int dy = 1; dy <= MAX_BRIDGE_SPAN; dy++) {
            if (this.currentContext.getBlockState(pos.below(dy)).isSolid()) {
                return true;
            }
        }
        return false;
    }

    // ========================
    //  DIG DOWN
    // ========================

    /**
     * Generates dig-down nodes when block below is solid and breakable.
     * Cost ramps with depth from surfaceY (entity's persisted spawn Y):
     *
     *   Cost = DIG_DOWN_BASE_COST + hardness * 2.0 + depthBelow * DIG_DOWN_DEPTH_PENALTY
     *
     * surfaceY is set once at entity spawn and persisted in NBT. It does NOT
     * reset on each pathfind, so the penalty accumulates as the mob digs deeper.
     * Hard cap at MAX_DIG_DEPTH blocks below surfaceY.
     */
    private int tryAddDigDownNode(Node[] results, int count, BlockPos nodePos) {
        if (count >= results.length || this.currentContext == null) return count;

        BlockPos below = nodePos.below();

        // Depth measured from the block we're digging into (fixes off-by-one)
        int depthBelow = surfaceY - below.getY();
        if (depthBelow > MAX_DIG_DEPTH) return count;

        BlockState belowState = this.currentContext.getBlockState(below);

        if (belowState.isAir() || !belowState.isSolid()) return count;
        if (isAcheroniteOrTransponder(belowState)) return count;

        long packed = below.asLong();
        if (immuneBlocks.contains(packed)) return count;

        float hardness = belowState.getDestroySpeed(this.currentContext.level(), below);
        if (hardness < 0 || hardness >= MAX_BREAKABLE_HARDNESS) return count;

        float depthCost = Math.max(0, depthBelow) * DIG_DOWN_DEPTH_PENALTY;
        // Directional penalty: digging down when target is above is wrong direction.
        // Makes scaffold-up (5.0) much cheaper than dig-down (~21+) in this case.
        float directionPenalty = 0;
        if (this.mob instanceof com.frozendawn.entity.ArchitectEntity architect) {
            net.minecraft.world.entity.LivingEntity target = architect.getTarget();
            if (target != null && target.getY() >= nodePos.getY()) {
                directionPenalty = 15.0F;
            }
        }
        float breakTime = ArchitectBlockBreaker.getEffectiveBreakTime(
                belowState, below, this.currentContext.level());
        float totalCost = DIG_DOWN_BASE_COST + breakTime * BREACH_TIME_MULTIPLIER + depthCost + directionPenalty;

        Node digNode = this.getNode(below.getX(), below.getY(), below.getZ());
        if (digNode.closed) return count;
        digNode.type = PathType.BREACH;
        digNode.costMalus = Math.max(digNode.costMalus, totalCost);
        results[count] = digNode;
        return count + 1;
    }

    // ========================
    //  HELPERS
    // ========================

    private static boolean isAcheroniteOrTransponder(BlockState state) {
        return state.is(ModBlocks.ACHERONITE_BLOCK.get())
                || state.is(ModBlocks.ACHERONITE_CRYSTAL.get())
                || state.is(ModBlocks.TRANSPONDER.get());
    }
}
