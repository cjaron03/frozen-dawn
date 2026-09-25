package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MacsIntegratedReplayGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void macsFrozenCampBuildsRealTerrainWithoutErasingHistory(GameTestHelper helper) {
        var level = helper.getLevel();
        var acquired = new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for (int x = 124; x <= 131; x++) for (int z = 124; z <= 133; z++) {
            if (z > 131 && (x < 127 || x > 128)) continue;
            var chunk = new net.minecraft.world.level.ChunkPos(x, z);
            if (!level.getForcedChunks().contains(chunk.toLong())) {
                level.setChunkForced(x, z, true);
                acquired.add(chunk);
            }
            level.getChunk(x, z);
        }
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo old,
                                         net.minecraft.gametest.framework.GameTestInfo next,
                                         net.minecraft.gametest.framework.GameTestRunner runner) { }
            private void release() { acquired.forEach(c -> level.setChunkForced(c.x, c.z, false)); }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo info,
                                   net.minecraft.gametest.framework.GameTestRunner runner) { release(); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo info,
                                   net.minecraft.gametest.framework.GameTestRunner runner) { release(); }
        });
        // The real `execute if loaded` gate requires entity-ticking chunks, not
        // just generated terrain. Wait before borrowing the shared SavedData.
        helper.startSequence().thenWaitUntil(() -> {
            for (var chunk : acquired) helper.assertTrue(
                    level.isPositionEntityTicking(chunk.getMiddleBlockPosition(100))
                            && level.areEntitiesLoaded(chunk.toLong()), "Waiting for camp chunk " + chunk);
        }).thenExecute(() -> MaeveObservationGameTest.withScene(helper, 131, scene -> {
            var player = scene.player("frozen_camp", 8, 4);
            var observer = scene.architect(2, 4);
            scene.hit(observer, player, true, 2);
            helper.assertTrue(!scene.beliefs(player).isEmpty(), "Start with actual witnessed history");
            var before = MaeveSavedData.get(scene.server).save(new net.minecraft.nbt.CompoundTag(), scene.level.registryAccess());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
            var commands = scene.server.getCommands();
            var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            var scoreboard = scene.server.getScoreboard();
            commands.performPrefixedCommand(source, "function macs_trial:load");
            var objective = scoreboard.getObjective("mt");
            java.util.function.ToIntFunction<String> score = name -> scoreboard.getOrCreatePlayerScore(
                    net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective).get();
            for (String set : java.util.List.of("#initialized 1", "#stage 1", "#round 19", "#lane 1", "#timer 123", "#terrain -1")) {
                String[] fields = set.split(" ");
                commands.performPrefixedCommand(source, "scoreboard players set " + fields[0] + " mt " + fields[1]);
            }
            commands.performPrefixedCommand(source, "function macs_trial:frozen");
            helper.assertTrue(score.applyAsInt("#stage") == 1 && score.applyAsInt("#terrain") == -1,
                    "The live command must refuse rebuilding during an encounter");
            commands.performPrefixedCommand(source, "scoreboard players set #stage mt 2");
            commands.performPrefixedCommand(source, "function macs_trial:frozen");
            helper.assertTrue(score.applyAsInt("#stage") == 4, "The live command enters guarded construction");
            int batches = 0;
            while (score.applyAsInt("#stage") == 4 && batches++ < 200) {
                int previous = score.applyAsInt("#terrain");
                commands.performPrefixedCommand(source, "function macs_trial:frozen_build_if_ready");
                helper.assertTrue(score.applyAsInt("#terrain") == previous + 1,
                        "One dispatch must complete exactly one bounded terrain batch: "
                                + previous + " -> " + score.applyAsInt("#terrain"));
            }
            helper.assertTrue(batches < 200 && score.applyAsInt("#stage") == 2 && score.applyAsInt("#timer") == 123
                            && score.applyAsInt("#round") == 19 && score.applyAsInt("#lane") == 1,
                    "Terrain completion preserves encounter order and the unfinished quiet gap");
            helper.assertTrue(before.equals(MaeveSavedData.get(scene.server).save(new net.minecraft.nbt.CompoundTag(), scene.level.registryAccess()))
                            && player.getMainHandItem().is(net.minecraft.world.item.Items.BOW),
                    "The rebuild must preserve tactical history and the existing kit");
            int snow = 0, crystals = 0, ice = 0;
            var layers = new java.util.HashSet<Integer>();
            for (int x = 2001; x <= 2095; x++) for (int z = 2001; z <= 2095; z++) {
                helper.assertTrue(!scene.level.getBlockState(new net.minecraft.core.BlockPos(x, 100, z))
                        .is(net.minecraft.world.level.block.Blocks.DIRT_PATH), "Late-phase ground has no dirt paths");
                for (int y = 100; y <= 107; y++) {
                    var pos = new net.minecraft.core.BlockPos(x, y, z); var state = scene.level.getBlockState(pos);
                    if (state.is(net.minecraft.world.level.block.Blocks.SNOW)) {
                        snow++; layers.add(state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS));
                        helper.assertTrue(state.canSurvive(scene.level, pos), "Snow must have real support at " + pos);
                    }
                    if (state.is(com.frozendawn.init.ModBlocks.ACHERONITE_CRYSTAL.get())) {
                        crystals++;
                        helper.assertTrue(state.canSurvive(scene.level, pos), "Crystal must have real support at " + pos);
                    }
                    if (state.is(net.minecraft.world.level.block.Blocks.PACKED_ICE)
                            || state.is(net.minecraft.world.level.block.Blocks.BLUE_ICE)) ice++;
                }
            }
            helper.assertTrue(snow > 1000 && layers.size() == 7 && crystals >= 30 && ice > 100,
                    "The executed terrain must contain varied real snow, supported crystals and ice: " + snow + "/" + layers + "/" + crystals + "/" + ice);
            for (int x : new int[]{2038, 2058}) helper.assertTrue(scene.level.getBlockState(
                    new net.minecraft.core.BlockPos(x, 102, 2048)).isAir(), "Both remembered camp entrances remain open");
            commands.performPrefixedCommand(source, "scoreboard players set #stage mt 0");
        }));
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 80)
    public static void macsIntegratedCampFunctionsParseAtClientPermission(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var functions = server.getResourceManager().listResources("function", id ->
                id.getNamespace().equals("macs_trial") && id.getPath().endsWith(".mcfunction"));
        helper.assertTrue(!functions.isEmpty(), "The generated camp functions must be in the actual test resources");
        var calls = java.util.regex.Pattern.compile("(?:^| )function (macs_trial:[a-z0-9_/]+)");
        functions.forEach((file, resource) -> {
            var id = ResourceLocation.fromNamespaceAndPath("macs_trial",
                    file.getPath().substring("function/".length()).replace(".mcfunction", ""));
            helper.assertTrue(server.getFunctions().get(id).isPresent(), "Native function loaded: " + id);
            try (var reader = resource.openAsReader()) {
                var lines = reader.lines().toList();
                CommandFunction.fromLines(id, server.getCommands().getDispatcher(),
                        server.createCommandSourceStack().withPermission(2), lines);
                for (String line : lines) {
                    var matcher = calls.matcher(line);
                    while (matcher.find()) helper.assertTrue(server.getFunctions().get(ResourceLocation.parse(matcher.group(1))).isPresent(),
                            "Referenced function exists: " + matcher.group(1));
                    // Brigadier parses fill coordinates but checks its volume only at execution.
                    if (line.startsWith("fill ")) {
                        String[] fields = line.split(" ");
                        long volume = 1;
                        for (int axis = 1; axis <= 3; axis++) volume *= 1L + Math.abs(
                                Integer.parseInt(fields[axis]) - Integer.parseInt(fields[axis + 3]));
                        helper.assertTrue(volume <= 32768, "Camp fill fits the vanilla command limit: " + line);
                    }
                }
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        });
        helper.succeed();
    }
}
