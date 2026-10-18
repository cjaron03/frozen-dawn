package com.frozendawn.flora;

import com.frozendawn.init.ModBlocks;
import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * One Rimewood: a squat, wind-shaped conifer 5-7 blocks tall. A straight trunk carries two or three short bent
 * branches (about 10 logs on average, 7 at least) under a narrow, uneven teal canopy with gaps that show the branches.
 */
public final class RimewoodTreeFeature extends Feature<NoneFeatureConfiguration> {
    public RimewoodTreeFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return grow(context.level(), context.origin(), context.random());
    }

    /** Ground a tree can stand on. */
    static boolean soil(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(ModBlocks.FROZEN_DIRT.get()) || state.is(Blocks.SNOW_BLOCK);
    }

    private static boolean open(WorldGenLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS);
    }

    public static boolean grow(WorldGenLevel level, BlockPos origin, RandomSource random) {
        if (!soil(level.getBlockState(origin.below()))) return false;
        int height = 5 + random.nextInt(3);
        for (int y = 0; y <= height + 1; y++) if (!open(level, origin.above(y))) return false;
        var log = ModBlocks.RIMEWOOD_LOG.get().defaultBlockState();
        var logs = new HashSet<BlockPos>();
        for (int y = 0; y < height; y++) logs.add(origin.above(y));
        // Two or three short branches, each bent upward at its tip like a wind-shaped limb.
        int branches = 2 + random.nextInt(2);
        var sides = Direction.Plane.HORIZONTAL.shuffledCopy(random);
        for (int b = 0; b < branches; b++) {
            var side = sides.get(b);
            var at = origin.above(2 + random.nextInt(Math.max(1, height - 3))).relative(side);
            logs.add(at.immutable());
            if (random.nextInt(3) == 0) logs.add((at = at.relative(side)).immutable());
            if (random.nextBoolean()) logs.add(at.above().immutable());
        }
        var leaves = new HashSet<BlockPos>();
        int top = origin.getY() + height;
        for (int y = origin.getY() + 2; y <= top + 1; y++) {
            int fromTop = top - y, radius = fromTop <= 0 ? 0 : fromTop <= 2 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (radius == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue; // round the corners
                var p = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                boolean edge = Math.abs(dx) == radius || Math.abs(dz) == radius;
                if (edge && radius > 0 && random.nextInt(4) == 0) continue; // uneven, gappy canopy
                if (!logs.contains(p)) leaves.add(p);
            }
        }
        for (var p : logs) if (!open(level, p)) return false;
        for (var p : logs) {
            // The trunk and each branch's upturned tip stand upright; the rest of a branch lies along its side.
            var axis = isTrunk(origin, p) || logs.contains(p.below()) ? Direction.Axis.Y : p.getX() != origin.getX() ? Direction.Axis.X : Direction.Axis.Z;
            level.setBlock(p, log.setValue(RotatedPillarBlock.AXIS, axis), 19);
        }
        var distance = distances(logs, leaves);
        var leaf = ModBlocks.RIMEWOOD_LEAVES.get().defaultBlockState();
        for (var p : leaves) {
            Integer d = distance.get(p);
            if (d == null || d >= LeavesBlock.DECAY_DISTANCE || !open(level, p)) continue;
            level.setBlock(p, leaf.setValue(LeavesBlock.DISTANCE, d), 19);
        }
        if (level.getBlockState(origin.below()).is(Blocks.GRASS_BLOCK)) level.setBlock(origin.below(), Blocks.DIRT.defaultBlockState(), 19);
        return true;
    }

    private static boolean isTrunk(BlockPos origin, BlockPos p) { return p.getX() == origin.getX() && p.getZ() == origin.getZ(); }

    /** Leaf distance to the nearest log through other leaves, as vanilla trees set it. */
    static Map<BlockPos, Integer> distances(Set<BlockPos> logs, Set<BlockPos> leaves) {
        var out = new HashMap<BlockPos, Integer>();
        var queue = new ArrayDeque<BlockPos>();
        for (var l : logs) for (var d : Direction.values()) {
            var n = l.relative(d);
            if (leaves.contains(n) && !out.containsKey(n)) { out.put(n, 1); queue.add(n); }
        }
        while (!queue.isEmpty()) {
            var p = queue.removeFirst();
            int next = out.get(p) + 1;
            if (next >= LeavesBlock.DECAY_DISTANCE) continue;
            for (var d : Direction.values()) {
                var n = p.relative(d);
                if (leaves.contains(n) && !out.containsKey(n)) { out.put(n, next); queue.add(n); }
            }
        }
        return out;
    }
}
