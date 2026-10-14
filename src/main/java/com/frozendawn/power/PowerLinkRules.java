package com.frozendawn.power;

import com.frozendawn.block.GeothermalCoreBlock;
import com.frozendawn.block.PowerRelayBlock;
import com.frozendawn.world.RoomAtmosphere;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Range, chunk availability and sealed boundary crossings; ordinary solid blocks are transparent. */
public final class PowerLinkRules {
    public enum Result { OK, SELF, NOT_NODE, RANGE, UNLOADED, UNKNOWN_SEAL, FEEDTHROUGH, DUPLICATE, LIMIT }
    private PowerLinkRules() {}
    public static int range(BlockState state) {
        if (state.getBlock() instanceof PowerRelayBlock relay) return relay.linkRange();
        return state.getBlock() instanceof GeothermalCoreBlock
                || state.getBlock() instanceof com.frozendawn.block.PanelLightBlock ? 16 : 0;
    }
    private static boolean feedthrough(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof PowerRelayBlock relay && relay.isFeedthrough();
    }
    public static Result validate(ServerLevel level, BlockPos a, BlockPos b) {
        if (a.equals(b)) return Result.SELF;
        if (!level.isLoaded(a) || !level.isLoaded(b)) return Result.UNLOADED;
        int reach = Math.min(range(level.getBlockState(a)), range(level.getBlockState(b)));
        if (reach == 0) return Result.NOT_NODE;
        if (a.distSqr(b) > reach * reach) return Result.RANGE;
        var path = path(a, b);
        for (var pos : path) if (!level.isLoaded(pos)) return Result.UNLOADED;
        var inspected = new HashSet<BlockPos>();
        for (int sample = 0; sample < path.size(); sample++) {
            var pos = path.get(sample);
            if (inspected.contains(pos) || !RoomAtmosphere.isPassage(level, pos, level.getBlockState(pos))) continue;
            var geometry = RoomAtmosphere.inspect(level, pos);
            if (geometry.seal() == RoomAtmosphere.Seal.UNKNOWN) return Result.UNKNOWN_SEAL;
            if (geometry.seal() == RoomAtmosphere.Seal.OPEN) { inspected.addAll(geometry.cells()); continue; }
            inspected.addAll(geometry.cells());
            // Interior pillars can interrupt room membership without leaving the room.
            // An outside passage between two visits is a real exit and re-entry.
            for (int i = 0; i < path.size();) {
                if (geometry.cells().contains(path.get(i))) { i++; continue; }
                int first = i;
                boolean outsideAir = false;
                while (i < path.size() && !geometry.cells().contains(path.get(i))) {
                    outsideAir |= RoomAtmosphere.isPassage(level, path.get(i), level.getBlockState(path.get(i)));
                    i++;
                }
                boolean hasBefore = first > 0, hasAfter = i < path.size();
                if (hasBefore && hasAfter && !outsideAir) continue;
                if (hasBefore && !(first == path.size() - 1 && !outsideAir)
                        && !feedthrough(level, path.get(first))) return Result.FEEDTHROUGH;
                if (hasAfter && !(i == 1 && !outsideAir)
                        && !feedthrough(level, path.get(i - 1))) return Result.FEEDTHROUGH;
            }
        }
        return Result.OK;
    }
    /** Exact center-to-center voxel traversal, bounded by the 32 block link range. */
    public static List<BlockPos> path(BlockPos a, BlockPos b) {
        var result = new ArrayList<BlockPos>();
        var start = Vec3.atCenterOf(a); var delta = Vec3.atCenterOf(b).subtract(start);
        int x = a.getX(), y = a.getY(), z = a.getZ();
        int sx = Integer.signum(b.getX() - x), sy = Integer.signum(b.getY() - y), sz = Integer.signum(b.getZ() - z);
        double dx = sx == 0 ? Double.POSITIVE_INFINITY : 1 / Math.abs(delta.x);
        double dy = sy == 0 ? Double.POSITIVE_INFINITY : 1 / Math.abs(delta.y);
        double dz = sz == 0 ? Double.POSITIVE_INFINITY : 1 / Math.abs(delta.z);
        double tx = dx / 2, ty = dy / 2, tz = dz / 2;
        result.add(a.immutable());
        for (int steps = 0; steps < 100 && (x != b.getX() || y != b.getY() || z != b.getZ()); steps++) {
            double next = Math.min(tx, Math.min(ty, tz));
            // Advance tied axes together: a corner touch is not passage through a neighboring wall.
            if (tx <= next + 1e-10) { x += sx; tx += dx; }
            if (ty <= next + 1e-10) { y += sy; ty += dy; }
            if (tz <= next + 1e-10) { z += sz; tz += dz; }
            result.add(new BlockPos(x, y, z));
        }
        return List.copyOf(result);
    }
}
