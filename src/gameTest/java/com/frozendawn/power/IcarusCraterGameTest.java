package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.world.IcarusCrater;
import com.frozendawn.world.IcarusCraterData;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;

/** The Icarus crater: carved chunk by chunk under a time budget, saved between chunks, and ringed by thawed ground.
 * Every test has sky access, so the framework's barrier ceiling does not count as the surface. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IcarusCraterGameTest {
    @BeforeBatch(batch = "icarus_crater")
    public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }

    private static final BlockPos CENTER = new BlockPos(10, 10, 10);
    private static final int TOP = 9;

    /** Stone to y 9 on a bedrock floor, with obsidian, a filled chest and a bedrock block inside the crater's reach. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_crater", timeoutTicks = 200, skyAccess = true)
    public static void icarusCraterCarvesBoundedSmallRadiusAndKeepsBedrock(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l = h.getLevel();
            for (int x = 0; x <= 20; x++) for (int z = 0; z <= 20; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.BEDROCK);
                for (int y = 2; y <= TOP; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
            h.setBlock(new BlockPos(12, 8, 10), Blocks.OBSIDIAN);
            h.setBlock(new BlockPos(10, 8, 12), Blocks.CHEST);
            ((Container) l.getBlockEntity(h.absolutePos(new BlockPos(10, 8, 12)))).setItem(0, new ItemStack(Items.DIAMOND, 5));
            h.setBlock(new BlockPos(10, 8, 10), Blocks.BEDROCK);
            var core = h.absolutePos(CENTER);
            IcarusCraterData.get(l).queue(core, 8, 42L);
            h.succeedWhen(() -> {
                h.assertTrue(IcarusCraterData.get(l).jobs().stream().noneMatch(j -> j.center().equals(core)), "The crater job is finished");
                boolean magma = false, glassed = false;
                for (int x = 0; x <= 20; x++) for (int z = 0; z <= 20; z++) {
                    double d = Math.hypot(x - CENTER.getX(), z - CENTER.getZ());
                    for (int y = 2; y <= TOP; y++) {
                        var at = new BlockPos(x, y, z); var state = l.getBlockState(h.absolutePos(at));
                        if (state.is(Blocks.MAGMA_BLOCK)) magma = true;
                        if (state.is(Blocks.TINTED_GLASS)) glassed = true;
                        // The floor sits at least four below the surface, so the top three layers inside 0.9r are gone.
                        if (d <= 0.9 * 8 && y >= TOP - 2 && !at.equals(new BlockPos(10, 8, 10)))
                            h.assertTrue(state.isAir() || state.is(Blocks.FIRE), "Inside the crater at " + at + " found " + state);
                        if (d > 8) h.assertTrue(state.is(Blocks.STONE), "Outside the crater at " + at + " found " + state);
                    }
                }
                h.assertTrue(l.getBlockState(h.absolutePos(new BlockPos(10, 8, 10))).is(Blocks.BEDROCK), "Bedrock inside the crater stays");
                for (int x = 0; x <= 20; x++) for (int z = 0; z <= 20; z++)
                    h.assertTrue(l.getBlockState(h.absolutePos(new BlockPos(x, 1, z))).is(Blocks.BEDROCK), "The bedrock floor stays");
                h.assertTrue(magma, "The crater floor has magma");
                h.assertTrue(glassed, "The crater floor is fused to glass in places");
                h.assertTrue(l.getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(30)).isEmpty(), "Nothing dropped");
            });
        });
    }

    /** The carve writes chunk sections directly and catches each chunk up afterwards: a deep crater's heightmap sits on
     * its floor and sky light reaches the floor. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_crater", timeoutTicks = 200, skyAccess = true)
    public static void icarusCraterLeavesHeightmapAndSkyLightOnTheFloor(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l = h.getLevel();
            for (int x = 0; x <= 20; x++) for (int z = 0; z <= 20; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.BEDROCK);
                for (int y = 2; y <= 14; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
            var data = new IcarusCraterData();
            data.queue(h.absolutePos(new BlockPos(10, 4, 10)), 8, 5L);
            IcarusCrater.run(l, data, Long.MAX_VALUE);
            h.assertTrue(data.jobs().isEmpty(), "The crater is carved in one unlimited run");
            int y = 14;
            while (y > 2 && l.getBlockState(h.absolutePos(new BlockPos(10, y - 1, 10))).isAir()) y--;
            h.assertTrue(y < 12, "The centre column is cut deep, lowest air at y " + y);
            var floor = h.absolutePos(new BlockPos(10, y, 10));
            for (var type : new Heightmap.Types[] {Heightmap.Types.WORLD_SURFACE, Heightmap.Types.MOTION_BLOCKING,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR})
                h.assertValueEqual(l.getHeight(type, floor.getX(), floor.getZ()), floor.getY(), type + " height at the centre");
            h.succeedWhen(() -> h.assertValueEqual(l.getBrightness(LightLayer.SKY, floor), 15, "sky light just above the crater floor"));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_crater", timeoutTicks = 40, skyAccess = true)
    public static void icarusCraterRespectsTheBudget(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l = h.getLevel(); var data = new IcarusCraterData();
            data.queue(h.absolutePos(CENTER), 8, 1L);
            int before = data.jobs().get(0).pending().size();
            h.assertTrue(before > 1, "A radius 8 crater with its melt ring spans more than one chunk, got " + before);
            // The budget is checked after every column: one deep chunk near the centre took 180 ms in the full-load run.
            h.assertValueEqual(IcarusCrater.run(l, data, 0), 1, "columns carved in one tick with no budget");
            h.assertValueEqual(data.jobs().get(0).pending().size(), before, "a chunk stays pending until its last column");
            int ticks = 1;
            while (data.jobs().get(0).pending().size() == before && ticks < 1000) { IcarusCrater.run(l, data, 0); ticks++; }
            h.assertValueEqual(ticks, IcarusCrater.COLUMNS, "no-budget ticks to finish the first chunk");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_crater", timeoutTicks = 40, skyAccess = true)
    public static void icarusCraterResumesFromSavedData(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l = h.getLevel(); var data = new IcarusCraterData();
            data.queue(h.absolutePos(CENTER), 8, 7L);
            IcarusCrater.run(l, data, 0);
            var job = data.jobs().get(0);
            var loaded = IcarusCraterData.load(data.save(new CompoundTag(), l.registryAccess()), l.registryAccess());
            h.assertValueEqual(loaded.jobs().size(), 1, "jobs after a reload");
            var again = loaded.jobs().get(0);
            h.assertValueEqual(again.pending(), job.pending(), "pending chunks after a reload");
            h.assertTrue(job.columns().size() == 1 && job.columns().values().iterator().nextInt() == 1, "One column of the first chunk is done, got " + job.columns());
            h.assertValueEqual(again.columns(), job.columns(), "column cursors after a reload");
            h.assertTrue(again.center().equals(job.center()) && again.radius() == job.radius() && again.seed() == job.seed(), "The job's centre, radius and seed survive");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_crater", timeoutTicks = 40, skyAccess = true)
    public static void icarusCraterSkipsUnloadedChunksUntilLoaded(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l = h.getLevel(); var data = new IcarusCraterData();
            data.queue(h.absolutePos(CENTER), 2, 3L);
            var near = new ChunkPos(h.absolutePos(CENTER));
            long far = ChunkPos.asLong(near.x + 60_000, near.z + 60_000);
            h.assertTrue(!l.hasChunk(near.x + 60_000, near.z + 60_000), "The far chunk is not loaded");
            data.jobs().get(0).pending().add(far);
            for (int tick = 0; tick < 10; tick++) IcarusCrater.run(l, data, IcarusCrater.BUDGET_NANOS);
            h.assertValueEqual(data.jobs().size(), 1, "jobs while a chunk is unloaded");
            h.assertValueEqual(data.jobs().get(0).pending(), new LongOpenHashSet(new long[] {far}), "only the unloaded chunk is pending");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_crater", timeoutTicks = 200, skyAccess = true)
    public static void icarusMeltRingThawsSnowAndIce(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l = h.getLevel();
            for (int x = 0; x <= 20; x++) for (int z = 0; z <= 20; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            // Radius 4: the melt ring runs from about 4 to 6.
            h.setBlock(new BlockPos(15, 2, 10), Blocks.SNOW_BLOCK);
            h.setBlock(new BlockPos(15, 3, 10), Blocks.SNOW);
            h.setBlock(new BlockPos(10, 2, 15), Blocks.PACKED_ICE);
            h.setBlock(new BlockPos(5, 2, 10), Blocks.ICE);
            h.setBlock(new BlockPos(10, 2, 5), Blocks.POWDER_SNOW);
            h.setBlock(new BlockPos(18, 2, 10), Blocks.SNOW_BLOCK);
            var core = h.absolutePos(new BlockPos(10, 3, 10));
            IcarusCraterData.get(l).queue(core, 4, 11L);
            h.succeedWhen(() -> {
                h.assertTrue(IcarusCraterData.get(l).jobs().stream().noneMatch(j -> j.center().equals(core)), "The crater job is finished");
                h.assertBlockPresent(Blocks.AIR, new BlockPos(15, 2, 10));
                h.assertBlockPresent(Blocks.AIR, new BlockPos(15, 3, 10));
                h.assertBlockPresent(Blocks.WATER, new BlockPos(10, 2, 15));
                h.assertBlockPresent(Blocks.WATER, new BlockPos(5, 2, 10));
                h.assertBlockPresent(Blocks.AIR, new BlockPos(10, 2, 5));
                h.assertBlockPresent(Blocks.SNOW_BLOCK, new BlockPos(18, 2, 10));
            });
        });
    }
}
