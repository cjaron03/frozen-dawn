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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loaded-only surface routing for the colony's underground movement. */
public final class FrostwritheBurrowController {
    public static final int MAX_SEARCH_NODES = 128;
    public static final int MAX_ROUTE_BLOCKS = 18;
    public static final TagKey<Block> BURROWABLE = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "frostwrithe_burrowable"));

    private final List<BlockPos> route = new ArrayList<>();
    private int routeIndex;

    public boolean buildRoute(ServerLevel level, BlockPos startProbe,
                              BlockPos desiredProbe) {
        route.clear();
        routeIndex = 0;
        BlockPos start = surface(level, startProbe);
        BlockPos goal = surface(level, clampGoal(start, desiredProbe));
        if (start == null || goal == null || start.equals(goal)) return false;

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
            if (distance <= 1) {
                best = current;
                break;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = surface(level, current.relative(direction));
                if (next == null || Math.abs(next.getY() - current.getY()) > 2
                        || start.distManhattan(next) > MAX_ROUTE_BLOCKS
                        || !visited.add(next.asLong())) {
                    continue;
                }
                parent.put(next.asLong(), current.asLong());
                open.addLast(next);
            }
        }
        if (best.equals(start)) return false;

        ArrayDeque<BlockPos> reversed = new ArrayDeque<>();
        BlockPos cursor = best;
        while (!cursor.equals(start)) {
            reversed.addFirst(cursor);
            Long previous = parent.get(cursor.asLong());
            if (previous == null) return false;
            cursor = BlockPos.of(previous);
        }
        route.addAll(reversed);
        return !route.isEmpty();
    }

    public boolean hasRoute() {
        return !route.isEmpty() && !complete();
    }

    @Nullable
    public BlockPos currentWaypoint() {
        return routeIndex < route.size() ? route.get(routeIndex) : null;
    }

    public void advanceIfReached(double x, double z) {
        BlockPos waypoint = currentWaypoint();
        if (waypoint == null) return;
        double dx = waypoint.getX() + 0.5D - x;
        double dz = waypoint.getZ() + 0.5D - z;
        if (dx * dx + dz * dz < 0.52D) routeIndex++;
    }

    public boolean complete() {
        return routeIndex >= route.size();
    }

    public void clear() {
        route.clear();
        routeIndex = 0;
    }

    public static double surfaceHeight(ServerLevel level, BlockPos surface) {
        BlockState state = level.getBlockState(surface);
        if (state.is(ModBlocks.ACHERONITE_CRYSTAL.get())) {
            return surface.getY();
        }
        if (isThinSurfaceCover(state)) {
            VoxelShape shape = state.getCollisionShape(level, surface);
            return surface.getY() + (shape.isEmpty() ? 0.125D
                    : shape.max(Direction.Axis.Y));
        }
        return surface.getY();
    }

    @Nullable
    public static BlockPos surfaceAt(ServerLevel level, BlockPos probe) {
        return surface(level, probe);
    }

    private static BlockPos clampGoal(@Nullable BlockPos start, BlockPos desired) {
        if (start == null) return desired;
        double dx = desired.getX() - start.getX();
        double dz = desired.getZ() - start.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= MAX_ROUTE_BLOCKS) return desired;
        double scale = MAX_ROUTE_BLOCKS / distance;
        return new BlockPos((int) Math.floor(start.getX() + dx * scale),
                desired.getY(), (int) Math.floor(start.getZ() + dz * scale));
    }

    @Nullable
    private static BlockPos surface(ServerLevel level, BlockPos probe) {
        if (!level.isLoaded(probe)) return null;
        int min = Math.max(level.getMinBuildHeight() + 1, probe.getY() - 4);
        int max = Math.min(level.getMaxBuildHeight() - 2, probe.getY() + 4);
        for (int y = max; y >= min; y--) {
            BlockPos feet = new BlockPos(probe.getX(), y, probe.getZ());
            BlockState atFeet = level.getBlockState(feet);
            if (isThinSurfaceCover(atFeet)
                    && validGround(level, feet.below())
                    && empty(level, feet.above())) {
                return feet;
            }
            if (empty(level, feet) && empty(level, feet.above())
                    && validGround(level, feet.below())) {
                return feet;
            }
        }
        return null;
    }

    private static boolean empty(ServerLevel level, BlockPos position) {
        return level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }

    private static boolean isThinSurfaceCover(BlockState state) {
        return state.is(ModBlocks.FROZEN_ATMOSPHERE.get())
                || state.is(ModBlocks.BLOOM_CRUST.get())
                || state.is(ModBlocks.ACHERONITE_CRYSTAL.get())
                || state.is(Blocks.SNOW);
    }

    private static boolean validGround(ServerLevel level, BlockPos ground) {
        if (!level.isLoaded(ground) || !level.isLoaded(ground.above())) return false;
        BlockState state = level.getBlockState(ground);
        if (!state.is(BURROWABLE) || state.is(ModBlocks.SEALED_LATTICE.get())
                || state.is(Blocks.BEDROCK) || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (ChunkCatchUpManager.isBloomOrsaProtected(level, ground)
                || HearthProtectionPolicy.protectedTargetAt(
                ReturnedHearthSavedData.get(level.getServer()), ground).isPresent()) {
            return false;
        }
        PlayerPlacedBlockTracker tracker = PlayerPlacedBlockTracker.get(level.getServer());
        for (int offset = -2; offset <= 3; offset++) {
            if (tracker.isPlayerPlaced(ground.offset(0, offset, 0))) return false;
        }
        return true;
    }
}
