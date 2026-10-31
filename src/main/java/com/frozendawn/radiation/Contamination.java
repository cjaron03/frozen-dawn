package com.frozendawn.radiation;

import com.frozendawn.init.ModBlocks;
import com.frozendawn.world.CombustionAtmosphere;
import com.frozendawn.world.RoomAtmosphere;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;

/** Contamination goes only where air, water or a blast carries it: a flood fill through open space from the
 * release, settling fallout on every floor it reaches. Sealed walls and closed doors stop it. In vacuum there is no
 * air to carry it, so it settles within a few blocks. Vegetation in the zone dies. Never loads chunks. */
public final class Contamination {
    public static final int AIR_RADIUS = 12, VACUUM_RADIUS = 3, BLAST_RADIUS = 8, MAX_CELLS = 6000;
    private Contamination() {}

    public static int releaseRadius(ServerLevel level, BlockPos origin) { return CombustionAtmosphere.canBurnAt(level, origin) ? AIR_RADIUS : VACUUM_RADIUS; }

    /** Returns the number of fallout layers placed. */
    public static int spread(ServerLevel level, Collection<BlockPos> starts, BlockPos center, int radius) { return spread(level, starts, center, radius, false); }

    public static int spread(ServerLevel level, Collection<BlockPos> starts, BlockPos center, int radius, boolean severe) {
        var queue = new ArrayDeque<BlockPos>(); var seen = new HashSet<BlockPos>();
        for (var s : starts) if (open(level, s) && seen.add(s.immutable())) queue.add(s.immutable());
        int placed = 0; long r2 = (long) radius * radius;
        while (!queue.isEmpty() && seen.size() < MAX_CELLS) {
            var p = queue.poll();
            placed += settle(level, p, severe);
            for (var d : Direction.values()) {
                var n = p.relative(d);
                if (n.distSqr(center) <= r2 && !seen.contains(n) && open(level, n)) { seen.add(n); queue.add(n); }
            }
        }
        return placed;
    }
    private static boolean open(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p)) return false;
        var s = level.getBlockState(p);
        return s.getBlock() instanceof com.frozendawn.block.FalloutBlock || RoomAtmosphere.isPassage(level, p, s);
    }
    private static int settle(ServerLevel level, BlockPos p, boolean severe) {
        var below = p.below(); var floor = level.getBlockState(below);
        if (floor.is(Blocks.GRASS_BLOCK)) level.setBlock(below, ModBlocks.DEAD_GRASS_BLOCK.get().defaultBlockState(), 3);
        var s = level.getBlockState(p);
        if (s.is(BlockTags.LEAVES)) { level.setBlock(p, Blocks.AIR.defaultBlockState(), 3); return 0; }
        if (s.getBlock() instanceof com.frozendawn.block.FalloutBlock) {
            if (severe && !s.getValue(com.frozendawn.block.FalloutBlock.SEVERE)) { level.setBlock(p, s.setValue(com.frozendawn.block.FalloutBlock.SEVERE, true), 3); return 1; }
            return 0;
        }
        if (!s.getFluidState().isEmpty()) return 0;
        if (!(s.isAir() || s.canBeReplaced())) return 0;
        var fallout = ModBlocks.FALLOUT.get().defaultBlockState().setValue(com.frozendawn.block.FalloutBlock.SEVERE, severe);
        if (!fallout.canSurvive(level, p)) return 0;
        level.setBlock(p, fallout, 3);
        return 1;
    }
}
