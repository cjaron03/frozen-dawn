package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.power.IcarusBlast;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Carves queued Icarus craters a column at a time, a part-carved chunk first and then the chunk nearest the centre, under a
 * per-tick time budget. A chunk that is not loaded waits in its job until it is; the job ends when every chunk in reach is
 * done. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class IcarusCrater {
    public static final long BUDGET_NANOS = 10_000_000L;
    /** Columns in a chunk. The budget is checked after each one: a deep chunk near the centre took 180 ms whole. */
    public static final int COLUMNS = 256;
    private static final int MELT_DEPTH = 8;
    /** The crater floor: this share turns to magma, then glass fused from the rock, obsidian and violet-glinting crying
     * obsidian, cumulatively; the rest stays as it was. Near the rim, where there is air, fires burn on the floor. */
    private static final float MAGMA_CHANCE = 0.15f, GLASS_CHANCE = 0.40f, OBSIDIAN_CHANCE = 0.58f, CRYING_CHANCE = 0.63f, FIRE_CHANCE = 0.3f;
    private static final double FIRE_FROM_EDGE = 0.8;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private IcarusCrater() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel l)) return;
        var data = IcarusCraterData.get(l);
        if (!data.jobs().isEmpty()) run(l, data, BUDGET_NANOS);
    }

    /** Carves columns of loaded pending chunks until the budget is spent, always at least one when any chunk is loaded;
     * returns how many columns it visited. */
    public static int run(ServerLevel l, IcarusCraterData data, long budgetNanos) {
        long start = System.nanoTime();
        int visited = 0;
        while (true) {
            IcarusCraterData.Job best = null; long bestChunk = 0; double bestDistance = Double.MAX_VALUE;
            for (var job : data.jobs()) {
                int cx = job.center().getX() >> 4, cz = job.center().getZ() >> 4;
                for (long chunk : job.pending()) {
                    int x = ChunkPos.getX(chunk), z = ChunkPos.getZ(chunk);
                    double distance = job.columns().containsKey(chunk) ? -1 : (double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz);
                    if (distance < bestDistance && carvable(l, x, z)) { best = job; bestChunk = chunk; bestDistance = distance; }
                }
            }
            if (best == null) return visited;
            var c = new ChunkPos(bestChunk);
            int column = best.columns().get(bestChunk);
            boolean spent = false;
            while (column < COLUMNS && !spent) {
                processColumn(l, best, c.getMinBlockX() + (column & 15), c.getMinBlockZ() + (column >> 4));
                column++; visited++;
                spent = System.nanoTime() - start >= budgetNanos;
            }
            if (column == COLUMNS) data.carved(best, bestChunk); else data.progress(best, bestChunk, column);
            if (spent) return visited;
        }
    }

    /** True for a chunk inside the block-ticking range. A chunk can still be cached while the server unloads it, and
     * touching that one blocks the tick until it is loaded again; a 16 s tick in the full-load perf run came from it. */
    public static boolean carvable(ServerLevel l, int x, int z) {
        return l.shouldTickBlocksAt(ChunkPos.asLong(x, z)) && l.hasChunk(x, z);
    }

    /** Carves one crater column, or thaws one melt-ring column; returns how many blocks changed. */
    public static int processColumn(ServerLevel l, IcarusCraterData.Job job, int x, int z) {
        var center = job.center(); double radius = job.radius(), melt = IcarusBlast.meltRadius(radius);
        int changed = 0, bottom = l.getMinBuildHeight();
        var pos = new BlockPos.MutableBlockPos();
        int dx = x - center.getX(), dz = z - center.getZ();
        double d = Math.hypot(dx, dz);
        if (d > melt) return 0;
        int surface = l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        double edge = IcarusBlast.edge(radius, job.seed(), Math.atan2(dz, dx));
        if (d <= edge) {
            int floor = Math.max(bottom, Math.min(center.getY() - IcarusBlast.depth(d, radius, job.seed(), dx, dz),
                    surface - IcarusBlast.rimDepth(job.seed(), dx, dz)));
            for (int y = surface - 1; y > floor; y--) {
                var state = l.getBlockState(pos.set(x, y, z));
                if (state.isAir() || state.is(Blocks.BEDROCK)) continue;
                Clearable.tryClear(l.getBlockEntity(pos));
                l.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                changed++;
            }
            var ground = l.getBlockState(pos.set(x, floor, z));
            var random = RandomSource.create(job.seed() ^ BlockPos.asLong(x, floor, z));
            float roll = random.nextFloat();
            var fused = roll < MAGMA_CHANCE ? Blocks.MAGMA_BLOCK : roll < GLASS_CHANCE ? Blocks.TINTED_GLASS
                    : roll < OBSIDIAN_CHANCE ? Blocks.OBSIDIAN : roll < CRYING_CHANCE ? Blocks.CRYING_OBSIDIAN : null;
            if (fused != null && !ground.isAir() && !ground.is(Blocks.BEDROCK)) {
                Clearable.tryClear(l.getBlockEntity(pos));
                l.setBlock(pos, fused.defaultBlockState(), FLAGS);
                changed++;
            }
            var above = pos.set(x, floor + 1, z).immutable();
            if (d > FIRE_FROM_EDGE * edge && random.nextFloat() < FIRE_CHANCE && l.getBlockState(above).isAir()
                    && !l.getBlockState(above.below()).isAir() && RoomAtmosphere.hasAir(l, above)) {
                l.setBlock(above, Blocks.FIRE.defaultBlockState(), FLAGS);
                changed++;
            }
        } else {
            for (int y = surface - 1; y >= Math.max(bottom, surface - MELT_DEPTH); y--) {
                var thawed = thaw(l.getBlockState(pos.set(x, y, z)));
                if (thawed == null) continue;
                l.setBlock(pos, thawed, FLAGS);
                changed++;
            }
        }
        return changed;
    }

    private static BlockState thaw(BlockState state) {
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) return Blocks.AIR.defaultBlockState();
        if (state.is(BlockTags.ICE)) return Blocks.WATER.defaultBlockState();
        return null;
    }
}
