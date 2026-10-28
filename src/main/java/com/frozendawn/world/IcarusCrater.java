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

/** Carves queued Icarus craters a chunk at a time, nearest the centre first, under a per-tick time budget. A chunk that is
 * not loaded waits in its job until it is; the job ends when every chunk in reach is done. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class IcarusCrater {
    public static final long BUDGET_NANOS = 10_000_000L;
    private static final int MELT_DEPTH = 8;
    private static final float MAGMA_CHANCE = 0.15f;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private IcarusCrater() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel l)) return;
        var data = IcarusCraterData.get(l);
        if (!data.jobs().isEmpty()) run(l, data, BUDGET_NANOS);
    }

    /** Carves loaded pending chunks until the budget is spent, always at least one when any is loaded; returns how many. */
    public static int run(ServerLevel l, IcarusCraterData data, long budgetNanos) {
        long start = System.nanoTime();
        int carved = 0;
        while (true) {
            IcarusCraterData.Job best = null; long bestChunk = 0; double bestDistance = Double.MAX_VALUE;
            for (var job : data.jobs()) {
                int cx = job.center().getX() >> 4, cz = job.center().getZ() >> 4;
                for (long chunk : job.pending()) {
                    int x = ChunkPos.getX(chunk), z = ChunkPos.getZ(chunk);
                    double distance = (double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz);
                    if (distance < bestDistance && l.hasChunk(x, z)) { best = job; bestChunk = chunk; bestDistance = distance; }
                }
            }
            if (best == null) return carved;
            processChunk(l, best, new ChunkPos(bestChunk));
            data.carved(best, bestChunk);
            carved++;
            if (System.nanoTime() - start >= budgetNanos) return carved;
        }
    }

    /** Carves the crater columns of one chunk and thaws its melt-ring columns; returns how many blocks changed. */
    public static int processChunk(ServerLevel l, IcarusCraterData.Job job, ChunkPos c) {
        var center = job.center(); double radius = job.radius(), melt = IcarusBlast.meltRadius(radius);
        int changed = 0, bottom = l.getMinBuildHeight();
        var pos = new BlockPos.MutableBlockPos();
        for (int x = c.getMinBlockX(); x <= c.getMaxBlockX(); x++) for (int z = c.getMinBlockZ(); z <= c.getMaxBlockZ(); z++) {
            int dx = x - center.getX(), dz = z - center.getZ();
            double d = Math.hypot(dx, dz);
            if (d > melt) continue;
            int surface = l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            if (d <= IcarusBlast.edge(radius, job.seed(), Math.atan2(dz, dx))) {
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
                if (!ground.isAir() && !ground.is(Blocks.BEDROCK)
                        && RandomSource.create(job.seed() ^ BlockPos.asLong(x, floor, z)).nextFloat() < MAGMA_CHANCE) {
                    Clearable.tryClear(l.getBlockEntity(pos));
                    l.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), FLAGS);
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
        }
        return changed;
    }

    private static BlockState thaw(BlockState state) {
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) return Blocks.AIR.defaultBlockState();
        if (state.is(BlockTags.ICE)) return Blocks.WATER.defaultBlockState();
        return null;
    }
}
