package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.mojang.authlib.GameProfile;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArchitectSpawnBoundaryGameTest {
    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 200)
    public static void regularArchitectSearchExcludesZeroAndNegativeInBothRoutes(GameTestHelper h) {
        var level = h.getLevel();
        var base = h.absolutePos(new BlockPos(4, 0, 4));
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "spawn_boundary"));
        player.setPos(base.getX() + .5, -5, base.getZ() + .5);
        var original = new LinkedHashMap<BlockPos, BlockState>();
        try {
            for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                var pos = new BlockPos(base.getX(), y, base.getZ());
                original.put(pos, level.getBlockState(pos));
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            for (int floor : new int[]{-4, -1, 0})
                level.setBlock(new BlockPos(base.getX(), floor, base.getZ()), Blocks.STONE.defaultBlockState(), 3);
            for (boolean postMaeve : new boolean[]{false, true}) {
                var pos = search(level, player, postMaeve, ArchitectSpawner.MIN_SPAWN_Y);
                h.assertTrue(pos != null && pos.getY() == 1, "Both regular routes can find the above-zero floor");
            }
            level.setBlock(new BlockPos(base.getX(), 0, base.getZ()), Blocks.AIR.defaultBlockState(), 3);
            for (boolean postMaeve : new boolean[]{false, true}) {
                h.assertTrue(search(level, player, postMaeve, ArchitectSpawner.MIN_SPAWN_Y) == null,
                        "Neither regular route may use an otherwise valid zero-height floor");
                h.assertTrue(search(level, player, postMaeve, Integer.MIN_VALUE).getY() == 0,
                        "Unrestricted callers retain their existing zero-height candidate");
            }
            level.setBlock(new BlockPos(base.getX(), -1, base.getZ()), Blocks.AIR.defaultBlockState(), 3);
            for (boolean postMaeve : new boolean[]{false, true}) {
                h.assertTrue(search(level, player, postMaeve, ArchitectSpawner.MIN_SPAWN_Y) == null,
                        "Neither regular route may use an otherwise valid underground floor");
                h.assertTrue(search(level, player, postMaeve, Integer.MIN_VALUE).getY() == -3,
                        "Other late threats retain their underground candidate");
            }
            level.setBlock(new BlockPos(base.getX(), 64, base.getZ()), Blocks.STONE.defaultBlockState(), 3);
            settle(h, original.keySet());
            for (boolean postMaeve : new boolean[]{false, true})
                h.assertTrue(search(level, player, postMaeve, ArchitectSpawner.MIN_SPAWN_Y).getY() == 65,
                        "An underground player still permits a valid regular surface fallback");
        } finally {
            original.forEach((pos, state) -> level.setBlock(pos, state, 3));
        }
        h.succeed();
    }

    private static BlockPos search(net.minecraft.server.level.ServerLevel level, FakePlayer player,
                                   boolean postMaeve, int minY) {
        return postMaeve ? LateThreatSpawnHelper.findUnrestrictedHybridSpawn(level, player, RandomSource.create(1337),
                0, 0, 1, LateThreatSpawnHelper.NO_LIGHT_LIMIT, minY)
                : LateThreatSpawnHelper.findHybridSpawn(level, player, RandomSource.create(1337),
                0, 0, 1, LateThreatSpawnHelper.NO_LIGHT_LIMIT, minY);
    }

    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 200)
    public static void blindArchitectRealCandidateIncludesZeroAndNeverCrossesAbove(GameTestHelper h) {
        var level = h.getLevel();
        var base = h.absolutePos(new BlockPos(4, 0, 4));
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "blind_zero"));
        player.setPos(base.getX() + .5, 0, base.getZ() + .5);
        long seed = 0;
        BlockPos candidate;
        while (true) {
            var random = RandomSource.create(seed);
            double angle = random.nextDouble() * Math.PI * 2, radius = 24 + random.nextDouble() * 40;
            int offset = random.nextInt(25) - 12;
            if (offset > 0) {
                candidate = BlockPos.containing(player.getX() + Math.cos(angle) * radius, 0,
                        player.getZ() + Math.sin(angle) * radius);
                break;
            }
            seed++;
        }
        var original = new LinkedHashMap<BlockPos, BlockState>();
        try {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 2; dy++) {
                var pos = candidate.offset(dx, dy, dz);
                original.put(pos, level.getBlockState(pos));
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            }
            level.setBlock(candidate, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(candidate.above(), Blocks.AIR.defaultBlockState(), 3);
            settle(h, original.keySet());
            h.assertTrue(level.getMaxLocalRawBrightness(candidate) == 0,
                    "The actual candidate is fully dark: " + candidate
                            + " brightness=" + level.getMaxLocalRawBrightness(candidate));
            level.random.setSeed(seed);
            var found = BlindArchitectSpawner.position(level, player);
            h.assertTrue(candidate.equals(found) && found.getY() == 0,
                    "A positive sampled offset clamps to a real eligible Y=0 Blind candidate");
        } finally {
            original.forEach((pos, state) -> level.setBlock(pos, state, 3));
        }
        h.succeed();
    }

    /** The generated Scribe arena must supply real ordinary spawn space above the BA boundary. */
    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 300)
    public static void scribeCheckArenaSupportsAboveZeroNaturalSpawn(GameTestHelper h) {
        var level = h.getLevel();
        var resources = level.getServer().getResourceManager();
        java.util.List<String> build;
        java.util.List<String> await;
        try (var a = resources.getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                     "macs_scribe", "function/build.mcfunction")).orElseThrow().openAsReader();
             var b = resources.getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                     "macs_scribe", "function/await.mcfunction")).orElseThrow().openAsReader()) {
            build = a.lines().toList();
            await = b.lines().toList();
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        var floor = build.stream().filter(line -> line.startsWith("fill ")
                && line.endsWith(" minecraft:smooth_stone")).findFirst().orElseThrow().split(" ");
        int minX = Integer.parseInt(floor[1]), y = Integer.parseInt(floor[2]), minZ = Integer.parseInt(floor[3]);
        int maxX = Integer.parseInt(floor[4]), maxZ = Integer.parseInt(floor[6]);
        var arrival = await.stream().filter(line -> line.startsWith("tp @s ")).findFirst().orElseThrow().split(" ");
        double px = Double.parseDouble(arrival[2]), py = Double.parseDouble(arrival[3]), pz = Double.parseDouble(arrival[4]);
        h.assertTrue(py > 0 && py == y + 1, "Actual Scribe waiting spot stands on an above-zero arena floor");
        h.assertTrue(y == Integer.parseInt(floor[5]), "Spawn pad is a horizontal floor");
        var anchor = h.absolutePos(new BlockPos(4096, 0, 4096));
        var original = new LinkedHashMap<BlockPos, BlockState>();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "scribe_height"));
        player.setPos(anchor.getX() + px, py, anchor.getZ() + pz);
        try {
            // Recreate the generated floor and actual mob clearance in an isolated translated patch.
            // Ordinary spawn search is invoked unchanged, including its real 48-64 block distance.
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) for (int dy = 0; dy < 3; dy++) {
                var pos = new BlockPos(anchor.getX() + x, y + dy, anchor.getZ() + z);
                original.put(pos, level.getBlockState(pos));
                var state = (dy == 0 ? Blocks.SMOOTH_STONE : Blocks.AIR).defaultBlockState();
                if (!state.equals(level.getBlockState(pos))) level.setBlock(pos, state, 3);
            }
            var method = ArchitectSpawner.class.getDeclaredMethod("findSpawnPos", net.minecraft.server.level.ServerLevel.class,
                    net.minecraft.server.level.ServerPlayer.class, RandomSource.class);
            method.setAccessible(true);
            var found = (BlockPos) method.invoke(null, level, player, RandomSource.create(1337));
            h.assertTrue(found != null && found.getY() == py && found.getY() > 0,
                    "Production ordinary spawn search finds the generated Scribe pad: " + found);
            h.assertTrue(found.getX() >= anchor.getX() + minX && found.getX() <= anchor.getX() + maxX
                            && found.getZ() >= anchor.getZ() + minZ && found.getZ() <= anchor.getZ() + maxZ,
                    "Candidate uses the actual generated pad, not unrelated terrain");
            double distance = Math.hypot(found.getX() + .5 - player.getX(), found.getZ() + .5 - player.getZ());
            h.assertTrue(distance >= 47 && distance <= 65, "Natural spawn distance is retained: " + distance);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        finally { original.forEach((pos, state) -> { if (!state.equals(level.getBlockState(pos))) level.setBlock(pos, state, 3); }); }
        h.succeed();
    }

    private static void settle(GameTestHelper h, java.util.Collection<BlockPos> edited) {
        var light = h.getLevel().getChunkSource().getLightEngine();
        var chunks = new java.util.LinkedHashSet<net.minecraft.world.level.ChunkPos>();
        edited.forEach(pos -> chunks.add(new net.minecraft.world.level.ChunkPos(pos)));
        // The Y=0 cavity crosses a chunk border. A single chunk's POST_UPDATE
        // barrier can finish before its neighbor's roof/sky updates. Await every
        // edited chunk and its neighboring lighting queues, as the observation
        // fixtures do, without changing brightness or the production predicate.
        for (var chunk : java.util.List.copyOf(chunks))
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                chunks.add(new net.minecraft.world.level.ChunkPos(chunk.x + x, chunk.z + z));
        var pending = chunks.stream().map(chunk -> light.waitForPendingTasks(chunk.x, chunk.z));
        var complete = java.util.concurrent.CompletableFuture.allOf(
                        pending.toArray(java.util.concurrent.CompletableFuture[]::new))
                .orTimeout(5, java.util.concurrent.TimeUnit.SECONDS);
        h.getLevel().getServer().managedBlock(() -> { light.tryScheduleUpdate(); return complete.isDone(); });
        complete.join();
    }
}
