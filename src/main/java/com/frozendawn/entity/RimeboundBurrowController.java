package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.homo.HearthProtectionPolicy;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.world.ChunkCatchUpManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded loaded-only surface route finder for the Rimeburrow. */
public final class RimeboundBurrowController {
    public static final int MAX_SEARCH_NODES = 192;
    public static final int MAX_SEGMENT_BLOCKS = 20;
    public static final int MIN_RECALC_TICKS = 15;
    public static final TagKey<net.minecraft.world.level.block.Block> BURROWABLE =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,
                            "rimebound_burrowable"));
    public static final TagKey<net.minecraft.world.level.block.Block> BRITTLE_GROUND =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,
                            "rimebound_brittle_ground"));

    private final List<BlockPos> route = new ArrayList<>();
    private int routeIndex;
    private long lastSearchTick = Long.MIN_VALUE;

    public boolean buildRoute(ServerLevel level, BlockPos startSurface,
                              BlockPos desiredSurface) {
        long now = level.getGameTime();
        if (now - lastSearchTick < MIN_RECALC_TICKS) {
            return !route.isEmpty();
        }
        lastSearchTick = now;
        route.clear();
        routeIndex = 0;

        BlockPos start = surface(level, startSurface);
        if (start == null) {
            return false;
        }
        double dx = desiredSurface.getX() - start.getX();
        double dz = desiredSurface.getZ() - start.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        BlockPos segmentGoal = desiredSurface;
        if (horizontalDistance > MAX_SEGMENT_BLOCKS) {
            double scale = MAX_SEGMENT_BLOCKS / horizontalDistance;
            segmentGoal = new BlockPos(
                    Mth.floor(start.getX() + dx * scale),
                    desiredSurface.getY(),
                    Mth.floor(start.getZ() + dz * scale));
        }
        BlockPos goal = surface(level, segmentGoal);
        if (goal == null) {
            return false;
        }

        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        Map<Long, Long> parent = new HashMap<>();
        open.add(start);
        visited.add(start.asLong());
        BlockPos best = start;
        int bestDistance = start.distManhattan(goal);
        int searched = 0;

        while (!open.isEmpty() && searched++ < MAX_SEARCH_NODES) {
            BlockPos current = open.removeFirst();
            int distance = current.distManhattan(goal);
            if (distance < bestDistance) {
                best = current;
                bestDistance = distance;
            }
            if (distance <= 1 || start.distManhattan(current) >= MAX_SEGMENT_BLOCKS) {
                best = current;
                break;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = surface(level, current.relative(direction));
                if (next == null || Math.abs(next.getY() - current.getY()) > 2
                        || start.distManhattan(next) > MAX_SEGMENT_BLOCKS
                        || !visited.add(next.asLong())) {
                    continue;
                }
                parent.put(next.asLong(), current.asLong());
                open.addLast(next);
            }
        }

        if (best.equals(start)) {
            return false;
        }
        ArrayDeque<BlockPos> reversed = new ArrayDeque<>();
        BlockPos cursor = best;
        while (!cursor.equals(start)) {
            reversed.addFirst(cursor);
            Long previous = parent.get(cursor.asLong());
            if (previous == null) {
                return false;
            }
            cursor = BlockPos.of(previous);
        }
        route.addAll(reversed);
        return !route.isEmpty();
    }

    @Nullable
    public BlockPos currentWaypoint() {
        return routeIndex < route.size() ? route.get(routeIndex) : null;
    }

    public void advanceIfReached(double x, double z) {
        BlockPos waypoint = currentWaypoint();
        if (waypoint != null) {
            double dx = waypoint.getX() + 0.5D - x;
            double dz = waypoint.getZ() + 0.5D - z;
            if (dx * dx + dz * dz < 0.65D) {
                routeIndex++;
            }
        }
    }

    public boolean complete() {
        return routeIndex >= route.size();
    }

    public void clear() {
        route.clear();
        routeIndex = 0;
    }

    @Nullable
    public static BlockPos surface(ServerLevel level, BlockPos probe) {
        int min = Math.max(level.getMinBuildHeight() + 1, probe.getY() - 4);
        int max = Math.min(level.getMaxBuildHeight() - 2, probe.getY() + 4);
        for (int y = max; y >= min; y--) {
            BlockPos feet = new BlockPos(probe.getX(), y, probe.getZ());
            BlockPos ground = feet.below();
            if (validGround(level, ground) && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                return feet;
            }
        }
        return null;
    }

    public static boolean validDormantTerrain(ServerLevel level, BlockPos feet) {
        return feet != null && validGround(level, feet.below());
    }

    private static boolean validGround(ServerLevel level, BlockPos ground) {
        if (!level.isLoaded(ground) || !level.isLoaded(ground.above())) {
            return false;
        }
        BlockState state = level.getBlockState(ground);
        if (!state.is(BURROWABLE) || state.is(ModBlocks.SEALED_LATTICE.get())
                || state.is(Blocks.BEDROCK) || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (ChunkCatchUpManager.isBloomOrsaProtected(level, ground)
                || HearthProtectionPolicy.protectedInteriorAt(
                ReturnedHearthSavedData.get(level.getServer()), ground).isPresent()) {
            return false;
        }
        PlayerPlacedBlockTracker tracker = PlayerPlacedBlockTracker.get(level.getServer());
        for (int offset = -2; offset <= 3; offset++) {
            if (tracker.isPlayerPlaced(ground.offset(0, offset, 0))) {
                return false;
            }
        }
        return true;
    }
}
