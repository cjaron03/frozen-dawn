package com.frozendawn.flora;

import com.frozendawn.init.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Natural Cold Flora from Phase 3, appearing near players wherever the ground is cold enough, in any biome. Rimewood
 * groves are rare and kept apart; Frostcap seeds onto nearby Rimewood trunks, and only rarely onto bare snow or ice.
 */
public final class ColdFloraSpawner {
    private ColdFloraSpawner() { }

    /** Every 30 s, each player has a 1 in 40 chance (about once in 20 minutes) to try a grove 48-112 blocks away. */
    public static final int GROVE_INTERVAL_TICKS = 600, GROVE_ONE_IN = 40, GROVE_MIN = 48, GROVE_MAX = 112, GROVE_SPACING = 48;
    /** Every 10 s, each player has a 1 in 30 chance (about once in five minutes) to try a Frostcap patch 16-64 blocks away. */
    public static final int CAP_INTERVAL_TICKS = 200, CAP_ONE_IN = 30, CAP_MIN = 16, CAP_MAX = 64, CAP_CLEAR_RADIUS = 16;
    /** Without Rimewood near the chosen spot, a ground patch is tried only 1 time in 4. */
    public static final int GROUND_PATCH_ONE_IN = 4;
    /** A new grove's trees each carry Frostcap shelves with this chance. */
    public static final float GROVE_SHELF_CHANCE = 0.6f;

    private static BlockPos around(BlockPos center, RandomSource random, int min, int max) {
        double angle = random.nextDouble() * Math.PI * 2;
        int distance = min + random.nextInt(max - min + 1);
        return new BlockPos(center.getX() + (int) Math.round(Math.cos(angle) * distance), center.getY(), center.getZ() + (int) Math.round(Math.sin(angle) * distance));
    }

    /** Ground level at x/z, under any snow layer. */
    private static BlockPos surface(WorldGenLevel level, int x, int z) {
        var top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
        return level.getBlockState(top.below()).is(Blocks.SNOW) ? top.below() : top;
    }

    /** The open spot on Rimewood soil within six blocks above or below {@code near}, searching downward. */
    static BlockPos groundNear(WorldGenLevel level, BlockPos near) {
        for (int dy = 6; dy >= -6; dy--) {
            var p = near.above(dy);
            var state = level.getBlockState(p);
            if ((state.isAir() || state.is(Blocks.SNOW)) && ColdFlora.rimewoodSoil(level.getBlockState(p.below()))) return p;
        }
        return null;
    }

    /** A spawn try's outcome: where it grew, or why it did not. */
    public record Attempt(BlockPos at, String failure, int placed) {
        static Attempt failed(String why) { return new Attempt(null, why, 0); }
        public boolean grew() { return failure == null; }
    }

    /** One grove try near a player: cold open ground, and no other Rimewood within 48 blocks. */
    public static Attempt tryGrove(ServerLevel level, BlockPos player) {
        var spot = around(player, level.random, GROVE_MIN, GROVE_MAX);
        if (!level.hasChunkAt(spot)) return Attempt.failed("chunk not loaded");
        var ground = surface(level, spot.getX(), spot.getZ());
        var soil = level.getBlockState(ground.below());
        if (!ColdFlora.rimewoodSoil(soil)) return Attempt.failed("ground is " + soil.getBlock().getName().getString());
        if (!level.canSeeSky(ground)) return Attempt.failed("no open sky");
        float celsius = ColdFlora.temperature(level, ground);
        if (celsius > ColdFloraPolicy.GROW_AT_OR_BELOW) return Attempt.failed("too warm (" + Math.round(celsius) + "C)");
        if (rimewoodNear(level, ground, GROVE_SPACING)) return Attempt.failed("Rimewood within " + GROVE_SPACING + " blocks");
        var trunks = plantGrove(level, ground, level.random, true);
        return trunks.isEmpty() ? Attempt.failed("no room for a tree") : new Attempt(ground, null, trunks.size());
    }

    /** Samples surface columns every three blocks for Rimewood needles or logs. */
    static boolean rimewoodNear(ServerLevel level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx += 3) for (int dz = -radius; dz <= radius; dz += 3) {
            var at = new BlockPos(center.getX() + dx, 0, center.getZ() + dz);
            if (!level.hasChunkAt(at)) continue;
            var top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, at);
            if (level.getBlockState(top.below()).is(ColdFlora.COLD_FLORA) || level.getBlockState(top.below(2)).is(ColdFlora.COLD_FLORA)) return true;
        }
        return false;
    }

    /**
     * Two to four Rimewoods within a few blocks of {@code center}. With {@code shelves}, each tree has a 60% chance to
     * carry one to three Frostcap shelves on its trunk. Returns the trunk bases grown.
     */
    public static List<BlockPos> plantGrove(WorldGenLevel level, BlockPos center, RandomSource random, boolean shelves) {
        int want = 2 + random.nextInt(3);
        var trunks = new ArrayList<BlockPos>();
        for (int attempt = 0; attempt < want * 4 && trunks.size() < want; attempt++) {
            var base = groundNear(level, center.offset(random.nextInt(11) - 5, 0, random.nextInt(11) - 5));
            if (base != null && RimewoodTreeFeature.grow(level, base, random)) trunks.add(base);
        }
        if (shelves) for (var trunk : trunks) if (random.nextFloat() < GROVE_SHELF_CHANCE) shelve(level, trunk, random, 1 + random.nextInt(3));
        return trunks;
    }

    /** Up to {@code count} Frostcap shelves on open sides of the trunk standing on {@code base}. Returns how many. */
    public static int shelve(WorldGenLevel level, BlockPos base, RandomSource random, int count) {
        var spots = new ArrayList<BlockPos>();
        var facings = new ArrayList<Direction>();
        for (int y = 0; level.getBlockState(base.above(y)).is(ColdFlora.RIMEWOOD_LOGS); y++)
            for (var facing : Direction.Plane.HORIZONTAL) {
                var spot = base.above(y).relative(facing);
                if (level.isEmptyBlock(spot)) { spots.add(spot); facings.add(facing); }
            }
        int placed = 0;
        while (placed < count && !spots.isEmpty()) {
            int i = random.nextInt(spots.size());
            level.setBlock(spots.remove(i), ModBlocks.FROSTCAP.get().defaultBlockState().setValue(FrostcapBlock.FACING, facings.remove(i))
                    .setValue(FrostcapBlock.AGE, random.nextInt(3)), 3);
            placed++;
        }
        return placed;
    }

    /**
     * One Frostcap try near a player: shelves on a Rimewood trunk near the chosen spot if there is one; otherwise, one
     * time in four, a small patch on snow or ice. Needs -20C or colder and no Frostcap within 16 blocks.
     */
    public static Attempt tryFrostcap(ServerLevel level, BlockPos player) {
        var spot = around(player, level.random, CAP_MIN, CAP_MAX);
        if (!level.hasChunkAt(spot)) return Attempt.failed("chunk not loaded");
        var ground = surface(level, spot.getX(), spot.getZ());
        float celsius = ColdFlora.temperature(level, ground);
        if (celsius > ColdFloraPolicy.GROW_AT_OR_BELOW) return Attempt.failed("too warm (" + Math.round(celsius) + "C)");
        for (var p : BlockPos.betweenClosed(ground.offset(-CAP_CLEAR_RADIUS, -6, -CAP_CLEAR_RADIUS), ground.offset(CAP_CLEAR_RADIUS, 10, CAP_CLEAR_RADIUS)))
            if (level.getBlockState(p).is(ModBlocks.FROSTCAP.get())) return Attempt.failed("Frostcap within " + CAP_CLEAR_RADIUS + " blocks");
        var trunk = findTrunk(level, ground, 8);
        if (trunk != null) {
            int placed = shelve(level, trunk, level.random, 2 + level.random.nextInt(3));
            return placed > 0 ? new Attempt(trunk, null, placed) : Attempt.failed("Rimewood trunk has no open side");
        }
        if (level.random.nextInt(GROUND_PATCH_ONE_IN) != 0) return Attempt.failed("no Rimewood near, and the 1-in-" + GROUND_PATCH_ONE_IN + " ground roll missed");
        var top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, ground);
        if (!FrostcapBlock.supported(level, top, Direction.UP) || !level.isEmptyBlock(top))
            return Attempt.failed("no Rimewood near, and the ground is " + level.getBlockState(top.below()).getBlock().getName().getString());
        int placed = ColdFloraEvents.plant(level, top, 3 + level.random.nextInt(4));
        return placed > 0 ? new Attempt(top, null, placed) : Attempt.failed("no room on the snow or ice");
    }

    /** The base of the nearest standing Rimewood trunk within {@code radius} of {@code center}, if any. */
    public static BlockPos findTrunk(ServerLevel level, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            for (int dy = 10; dy >= -10; dy--) {
                var p = center.offset(dx, dy, dz);
                if (!level.getBlockState(p).is(ColdFlora.RIMEWOOD_LOGS) || level.getBlockState(p.below()).is(ColdFlora.RIMEWOOD_LOGS)) continue;
                if (!ColdFlora.rimewoodSoil(level.getBlockState(p.below()))) continue;
                double d = p.distSqr(center);
                if (d < bestDistance) { bestDistance = d; best = p.immutable(); }
                break;
            }
        }
        return best;
    }
}
