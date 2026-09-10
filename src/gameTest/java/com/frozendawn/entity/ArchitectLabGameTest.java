package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.debug.architect.ArchitectDebugReports;
import com.frozendawn.debug.architect.ArchitectLab;
import com.frozendawn.debug.architect.ArchitectLabFrame;
import com.frozendawn.debug.architect.ArchitectLabRun;
import com.frozendawn.debug.architect.ArchitectLabScenario;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArchitectLabGameTest {
    private static final java.util.Set<ArchitectLabRun> EXPORTED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private ArchitectLabGameTest() { }

    @GameTestGenerator
    public static Collection<TestFunction> architectCases() {
        List<TestFunction> cases = new ArrayList<>();
        for (long seed : new long[]{1, 1337}) {
            for (ArchitectLabScenario scenario : new ArchitectLabScenario[]{ArchitectLabScenario.CLEAR_CORRIDOR,
                    ArchitectLabScenario.LOW_CEILING, ArchitectLabScenario.UNREACHABLE_TARGET, ArchitectLabScenario.SEALED_POCKET}) {
                Rotation[] rotations = scenario == ArchitectLabScenario.LOW_CEILING ? Rotation.values() : new Rotation[]{Rotation.NONE};
                for (Rotation rotation : rotations) {
                    String name = "architectlab." + scenario.id + "_s" + seed + "_r" + rotation.ordinal();
                    cases.add(new TestFunction("defaultBatch", name, scenario.template().toString(), rotation,
                            scenario.timeout + 10, 0, true, h -> runScenario(h, scenario, seed, false)));
                }
            }
            cases.add(new TestFunction("defaultBatch", "architectlab.clear_corridor_live_s" + seed,
                    ArchitectLabScenario.CLEAR_CORRIDOR.template().toString(), 310, 0, true,
                    h -> runScenario(h, ArchitectLabScenario.CLEAR_CORRIDOR, seed, true)));
        }
        for (Rotation rotation : Rotation.values()) {
            cases.add(new TestFunction("defaultBatch", "architectlab.slab_low_roof_live_s2026_r" + rotation.ordinal(),
                    ArchitectLabScenario.SLAB_LOW_ROOF.template().toString(), rotation, 610, 0, true,
                    h -> runScenario(h, ArchitectLabScenario.SLAB_LOW_ROOF, 2026, true)));
        }
        return cases;
    }

    static ArchitectLabFrame frame(GameTestHelper helper) {
        return new ArchitectLabFrame(helper.absolutePos(new BlockPos(0, 1, 0)), helper.getTestRotation());
    }

    static void runScenario(GameTestHelper helper, ArchitectLabScenario scenario, long seed, boolean live) {
        // Natural target selection must not see villagers in a neighboring test arena.
        boolean isolated = scenario == ArchitectLabScenario.TARGET_TURNOVER;
        ArchitectLabFrame arena = frame(helper);
        java.util.List<net.minecraft.world.level.ChunkPos> forced = new ArrayList<>();
        if (isolated) {
            int index = (seed == 7 ? 0 : 4) + helper.getTestRotation().ordinal();
            arena = new ArchitectLabFrame(arena.origin().offset(0, 0, 4096 + index * 512), arena.rotation());
            var bounds = arena.bounds().inflate(16);
            for (int x = net.minecraft.util.Mth.floor(bounds.minX) >> 4; x <= net.minecraft.util.Mth.floor(bounds.maxX) >> 4; x++)
                for (int z = net.minecraft.util.Mth.floor(bounds.minZ) >> 4; z <= net.minecraft.util.Mth.floor(bounds.maxZ) >> 4; z++) {
                    helper.getLevel().setChunkForced(x, z, true);
                    forced.add(new net.minecraft.world.level.ChunkPos(x, z));
                }
        }
        ArchitectLabRun run = ArchitectLabRun.prepare(helper.getLevel(), scenario, arena, seed, live, isolated);
        watchReport(helper, run);
        if (isolated) helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) { }
            @Override public void testAddedForRerun(GameTestInfo old, GameTestInfo next, GameTestRunner runner) { }
            private void cleanup() {
                run.dispose();
                for (var chunk : forced) helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
            }
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
        });
        run.begin();
        helper.onEachTick(() -> {
            run.tick();
            if (run.status() == ArchitectLabRun.Status.FAILED) helper.fail(run.reason());
            if (run.status() == ArchitectLabRun.Status.PASSED) {
                exportOrFail(helper, run);
                helper.succeed();
            }
        });
    }

    /** Also captures a trace when the framework times out before our normal end condition. */
    static void watchReport(GameTestHelper helper, ArchitectLabRun run) {
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) { }
            @Override public void testAddedForRerun(GameTestInfo old, GameTestInfo next, GameTestRunner runner) { }
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) {
                if (!run.done()) run.finish(ArchitectLabRun.Status.PASSED, "Harness assertions passed");
                exportOrFail(helper, run);
            }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) {
                if (!run.done()) run.finish(ArchitectLabRun.Status.FAILED, String.valueOf(info.getError()));
                try { export(helper, run); }
                catch (IOException error) { FrozenDawn.LOGGER.error("Could not export failed test {}", info.getTestName(), error); }
            }
        });
    }

    private static void exportOrFail(GameTestHelper helper, ArchitectLabRun run) {
        try { export(helper, run); }
        catch (IOException error) { helper.fail("Report export failed: " + error.getMessage()); }
    }

    private static void export(GameTestHelper helper, ArchitectLabRun run) throws IOException {
        if (EXPORTED.contains(run)) return;
        String destination = System.getProperty("frozendawn.architect.reports");
        Path directory = destination == null ? ArchitectLab.reports(helper.getLevel()) : Path.of(destination);
        var context = new LinkedHashMap<String, Object>(run.context());
        context.put("testName", helper.testInfo.getTestName());
        ArchitectDebugReports.export(directory, run.architect.decisionJournal(), context);
        EXPORTED.add(run);
    }

    @GameTest(template = "lab/slab_low_roof", timeoutTicks = 40)
    public static void partialSurfaceClearanceRejectsRealObstructions(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos from = frame(helper).block(new BlockPos(4, 1, 4));
        BlockPos to = frame(helper).block(new BlockPos(4, 1, 5));
        helper.assertTrue(com.frozendawn.entity.architect.ArchitectWalkGeometry.canWalkPartialTransition(level, from, to),
                "The Architect must fit between the bottom floor slabs and top ceiling slabs");
        level.setBlockAndUpdate(to.above(2), Blocks.STONE_SLAB.defaultBlockState());
        helper.assertTrue(!com.frozendawn.entity.architect.ArchitectWalkGeometry.canWalkPartialTransition(level, from, to),
                "A lowered ceiling must block the body sweep");
        level.setBlockAndUpdate(to.above(2), Blocks.STONE_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
        level.setBlockAndUpdate(to.above(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(!com.frozendawn.entity.architect.ArchitectWalkGeometry.canWalkPartialTransition(level, from, to),
                "A real wall must remain blocked");
        level.setBlockAndUpdate(to.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(to, Blocks.OAK_FENCE.defaultBlockState());
        helper.assertTrue(!com.frozendawn.entity.architect.ArchitectWalkGeometry.canWalkPartialTransition(level, from, to),
                "A fence must not be treated as a partial walking surface");
        level.setBlockAndUpdate(to, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(to.below(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(!com.frozendawn.entity.architect.ArchitectWalkGeometry.canWalkPartialTransition(level, from, to),
                "Missing support must not become a free walk edge");
        helper.succeed();
    }

    @GameTest(template = "lab/clear_corridor", timeoutTicks = 40)
    public static void labCommandsResetAndPublishReports(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var ticks = server.tickRateManager();
        boolean wasFrozen = ticks.isFrozen();
        int stepping = ticks.frozenTicksToRun();
        var rules = level.getGameRules().copy();
        var difficulty = level.getDifficulty();
        long dayTime = level.getDayTime();
        var frame = frame(helper);
        var bounds = new net.minecraft.world.phys.AABB(Vec3.atLowerCornerOf(frame.origin()),
                Vec3.atLowerCornerOf(frame.origin().offset(21, 16, 21)));
        var source = server.createCommandSourceStack().withLevel(level)
                .withPosition(Vec3.atLowerCornerOf(frame.origin().above())).withPermission(2);
        var dispatcher = server.getCommands().getDispatcher();
        try {
            helper.assertTrue(dispatcher.execute("fd architect lab setup", source) == 1, "Setup command failed");
            helper.assertTrue(ticks.isFrozen(), "Setup did not freeze ticks");
            helper.assertTrue(dispatcher.execute("fd architect lab scenario clear_corridor", source) == 1, "Scenario command failed");
            var old = level.getEntitiesOfClass(ArchitectEntity.class, bounds, a -> a.getTags().contains("fd_lab")).getFirst();
            var oldTarget = level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, bounds,
                    a -> a.getTags().contains("fd_lab")).getFirst();
            old.setHealth(7);
            level.setBlockAndUpdate(frame.block(new BlockPos(4, 0, 5)), Blocks.AIR.defaultBlockState());
            helper.assertTrue(dispatcher.execute("fd architect lab reset", source) == 1, "Reset command failed");
            var fresh = level.getEntitiesOfClass(ArchitectEntity.class, bounds, a -> a.getTags().contains("fd_lab")).getFirst();
            var freshTarget = level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, bounds,
                    a -> a.getTags().contains("fd_lab")).getFirst();
            helper.assertTrue(old.isRemoved() && oldTarget.isRemoved() && !old.getUUID().equals(fresh.getUUID())
                    && !oldTarget.getUUID().equals(freshTarget.getUUID()), "Command reset reused actors");
            helper.assertTrue(fresh.isNoAi() && fresh.getHealth() == fresh.getMaxHealth(), "Command reset left actor active or damaged");
            helper.assertTrue(level.getBlockState(frame.block(new BlockPos(4, 0, 5))).is(Blocks.STONE), "Command reset retained floor damage");
            helper.assertTrue(dispatcher.execute("fd architect lab run", source) == 1, "Run command failed");
            helper.assertTrue(ticks.isFrozen() && fresh.decisionJournal().enabled(), "Run did not begin recording while frozen");
            helper.assertTrue(dispatcher.execute("fd architect lab dump", source) == 1, "Dump command failed");
            var directory = ArchitectLab.reports(level);
            var pointer = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(directory.resolve("architect-latest.json"))).getAsJsonObject();
            helper.assertTrue(pointer.get("status").getAsString().equals("COMPLETE"), "Dump did not publish a completed export");
            var trace = directory.resolve(pointer.get("trace").getAsString());
            byte[] bytes = java.nio.file.Files.readAllBytes(trace);
            helper.assertTrue(ArchitectDebugReports.sha256(bytes).equals(pointer.get("traceSha256").getAsString()), "Export pointer hash is wrong");
            helper.assertTrue(new String(bytes, java.nio.charset.StandardCharsets.UTF_8).contains("\n0\tRECORD_START\t"), "Command trace did not start at tick zero");
            helper.assertTrue(dispatcher.execute("fd architect lab reset", source) == 1, "Second reset command failed");
            var resetPointer = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(directory.resolve("architect-latest.json"))).getAsJsonObject();
            helper.assertTrue(resetPointer.get("status").getAsString().equals("PREPARED"), "Reset left old COMPLETE pointer");
            helper.assertTrue(!java.nio.file.Files.exists(directory.resolve("architect-latest.tsv")), "Reset left a stale latest trace");
            helper.assertTrue(java.nio.file.Files.isRegularFile(trace), "Reset destroyed the immutable earlier dump");
            helper.succeed();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | IOException error) {
            helper.fail("Lab command/export integration failed: " + error.getMessage());
        } finally {
            // Even a failed assertion must not leave the whole GameTest server paused.
            ArchitectLab.command(source, "reset", "");
            level.getGameRules().assignFrom(rules, server);
            server.setDifficulty(difficulty, true);
            level.setDayTime(dayTime);
            ticks.setFrozen(wasFrozen);
            ticks.setFrozenTicksToRun(stepping);
        }
    }

    @GameTest(template = "lab/clear_corridor", timeoutTicks = 110)
    public static void labWaitsForRecording(GameTestHelper helper) {
        var run = ArchitectLabRun.prepare(helper.getLevel(), ArchitectLabScenario.CLEAR_CORRIDOR, frame(helper), 1337, false, false);
        Vec3 start = run.architect.position();
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(run.architect.position().distanceToSqr(start) < 0.000001, "Prepared actor moved");
            helper.assertTrue(run.architect.successfulBreakCount() == 0, "Prepared actor mined before recording");
            helper.assertTrue(run.architect.decisionJournal().entries().isEmpty(), "Preparation leaked into the journal");
            run.begin();
            watchReport(helper, run);
            var first = run.architect.decisionJournal().entries().getFirst();
            helper.assertTrue(first.event().equals("RECORD_START") && first.noProgress() == 0 && first.breaks() == 0,
                    "Run did not start with clean counters");
            helper.assertTrue(run.architect.tickCount == 0, "Preparation time changed the actor's starting age");
            run.finish(ArchitectLabRun.Status.PASSED, "Prepared actor remained idle for 80 ticks");
            exportOrFail(helper, run);
            helper.succeed();
        });
    }

    @GameTest(template = "lab/clear_corridor", timeoutTicks = 80)
    public static void labResetReplacesActorsAndTerrain(GameTestHelper helper) {
        var frame = frame(helper);
        var old = ArchitectLabRun.prepare(helper.getLevel(), ArchitectLabScenario.CLEAR_CORRIDOR, frame, 1337, false, false);
        old.begin();
        // Seed a real committed walk before exercising the raw planner reset.
        old.architect.setOnGround(true);
        old.architect.executeVanillaWalkStep(new com.frozendawn.entity.ai.DStarLitePathfinder.NextStep(
                frame.block(new BlockPos(4, 1, 5)), com.frozendawn.entity.ai.DStarLitePathfinder.StepType.WALK), old.target);
        helper.assertTrue(old.architect.getCommittedWalkSteeringTarget() != null, "Test did not establish a committed walk");
        old.architect.debugResetApproach();
        helper.assertTrue(old.architect.getCommittedWalkSteeringTarget() == null, "Planner reset retained its old corridor");
        helper.assertTrue(old.architect.getNavigation().isDone(), "Planner reset retained navigation");
        old.architect.setHealth(7);
        helper.getLevel().setBlockAndUpdate(frame.block(new BlockPos(4, 0, 5)), Blocks.AIR.defaultBlockState());
        old.finish(ArchitectLabRun.Status.ABORTED, "Reset harness setup");
        old.dispose();
        var fresh = ArchitectLabRun.prepare(helper.getLevel(), ArchitectLabScenario.CLEAR_CORRIDOR, frame, 1337, false, true);
        helper.assertTrue(old.architect.isRemoved() && old.target.isRemoved(), "Reset left old entities loaded");
        helper.assertTrue(!old.architect.getUUID().equals(fresh.architect.getUUID())
                && !old.target.getUUID().equals(fresh.target.getUUID()), "Reset reused an entity");
        helper.assertTrue(helper.getLevel().getBlockState(frame.block(new BlockPos(4, 0, 5))).is(Blocks.STONE),
                "Reset did not restore the mineable floor");
        helper.assertTrue(fresh.architect.getHealth() == fresh.architect.getMaxHealth() && fresh.architect.isNoAi(), "Fresh actor was not healthy and paused");
        fresh.begin();
        watchReport(helper, fresh);
        fresh.finish(ArchitectLabRun.Status.PASSED, "Reset restored terrain and replaced both actors");
        exportOrFail(helper, fresh);
        helper.succeed();
    }
}
