package com.frozendawn.gametest;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectLabGameTest;
import com.frozendawn.maeve.BlindArchitectGameTest;
import com.frozendawn.maeve.LabBridgeGameTest;
import com.frozendawn.maeve.MaeveConvergenceGameTest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * How fast, and how much of, a game test run goes. Both are opt-in through system properties, so a plain
 * {@code runGameTestServer} behaves as vanilla does.
 *
 * <p>{@value #SPRINT_PROPERTY}: tests wait in ticks, never in seconds, so the server may tick as fast as it can
 * instead of at 20 per second. This is {@code /tick sprint} for the whole run; what each test sees is unchanged,
 * except where a test needs real time or the tick manager itself: see {@link #holdNormalPace}.
 *
 * <p>{@value #ONLY_PROPERTY}: a comma-separated list of name fragments. Only tests whose name contains one of them
 * run, for a fast loop while iterating. It is never set for the gate, which must run every required test.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class GameTestPacing {
    public static final String SPRINT_PROPERTY = "frozendawn.gametest.sprint";
    public static final String ONLY_PROPERTY = "frozendawn.gametest.only";
    /** These freeze, step or sprint the server themselves, or call the lab bridge, which refuses work mid-sprint. */
    private static final List<Class<?>> NORMAL_PACE = List.of(
            LabBridgeGameTest.class, ArchitectLabGameTest.class, BlindArchitectGameTest.class, MaeveConvergenceGameTest.class);

    private static int holds;

    private GameTestPacing() { }

    /**
     * Ticks at the normal rate until the returned release runs: a wait on chunk IO, which finishes in real time,
     * then costs a test no more ticks than it would unsprinted. Holds from concurrent tests nest; the release is
     * idempotent. Server thread only.
     */
    public static Runnable holdNormalPace(MinecraftServer server) {
        if (!Boolean.getBoolean(SPRINT_PROPERTY)) return () -> { };
        if (holds++ == 0) server.tickRateManager().stopSprinting();
        boolean[] released = {false};
        return () -> {
            if (released[0]) return;
            released[0] = true;
            if (--holds == 0) server.tickRateManager().requestGameToSprint(Integer.MAX_VALUE);
        };
    }

    /** Before the server groups its tests into batches, which it does straight after this event. */
    @SubscribeEvent
    static void prepare(ServerAboutToStartEvent event) {
        if (!(event.getServer() instanceof GameTestServer server)) return;
        List<TestFunction> tests = testFunctions(server);
        if (Boolean.getBoolean(SPRINT_PROPERTY)) {
            Set<String> paced = NORMAL_PACE.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                    .filter(method -> method.isAnnotationPresent(GameTest.class))
                    .map(method -> method.getName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            tests.replaceAll(test -> paced.contains(test.testName()) ? atNormalPace(test) : test);
        }
        String only = System.getProperty(ONLY_PROPERTY, "");
        if (only.isBlank()) return;
        var fragments = Arrays.stream(only.toLowerCase(Locale.ROOT).split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        int before = tests.size();
        tests.removeIf(test -> fragments.stream().noneMatch(test.testName().toLowerCase(Locale.ROOT)::contains));
        FrozenDawn.LOGGER.info("Game tests selected by {}: {} of {}", fragments, tests.size(), before);
        // An empty selection would let the server finish with nothing run and call it a pass.
        if (tests.isEmpty()) throw new IllegalStateException("No game test name contains any of " + fragments);
    }

    @SubscribeEvent
    static void sprint(ServerStartedEvent event) {
        if (!(event.getServer() instanceof GameTestServer server)) return;
        // A selection may hold no defaultBatch test, whose before-batch hook would otherwise install the report.
        if (!System.getProperty(ONLY_PROPERTY, "").isBlank()) GameTestReporting.installReporter(server.overworld());
        if (!Boolean.getBoolean(SPRINT_PROPERTY)) return;
        server.tickRateManager().requestGameToSprint(Integer.MAX_VALUE);
        FrozenDawn.LOGGER.info("Game test server sprinting: ticks run back to back");
    }

    /**
     * A level with no player and no forced chunk stops ticking entities and block entities after 300 empty ticks.
     * A sprint burns through those before the first batch starts, so a test that waits on a machine's own ticker
     * would wait forever. The full gate only escaped this through tests that happen to force chunks.
     */
    @SubscribeEvent
    static void keepAwake(net.neoforged.neoforge.event.tick.LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level && level.getServer() instanceof GameTestServer) level.resetEmptyTime();
    }

    /**
     * Runs the body once every chunk under the test's structure has its entities loaded. Until then a chunk ticks no
     * block entity, and a sprint can outrun that load, which finishes in real time; the wait holds normal pace.
     */
    public static void whenTicking(GameTestHelper helper, Runnable body) {
        Runnable resume = holdNormalPace(helper.getLevel().getServer());
        releaseAtEnd(helper, resume);
        awaitEntities(helper, () -> { resume.run(); body.run(); });
    }

    private static void awaitEntities(GameTestHelper helper, Runnable then) {
        var box = helper.getBounds();
        boolean loaded = ChunkPos.rangeClosed(new ChunkPos(BlockPos.containing(box.minX, 0, box.minZ)), new ChunkPos(BlockPos.containing(box.maxX, 0, box.maxZ)))
                .allMatch(chunk -> helper.getLevel().areEntitiesLoaded(chunk.toLong()));
        if (loaded) then.run(); else helper.runAfterDelay(1, () -> awaitEntities(helper, then));
    }

    /** The same test, holding normal pace from its first tick until it passes or fails. */
    private static TestFunction atNormalPace(TestFunction test) {
        return new TestFunction(test.batchName(), test.testName(), test.structureName(), test.rotation(), test.maxTicks(),
                test.setupTicks(), test.required(), test.manualOnly(), test.maxAttempts(), test.requiredSuccesses(),
                test.skyAccess(), helper -> {
                    releaseAtEnd(helper, holdNormalPace(helper.getLevel().getServer()));
                    test.function().accept(helper);
                });
    }

    private static void releaseAtEnd(GameTestHelper helper, Runnable resume) {
        helper.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo info) { }
            public void testPassed(GameTestInfo info, GameTestRunner runner) { resume.run(); }
            public void testFailed(GameTestInfo info, GameTestRunner runner) { resume.run(); }
            public void testAddedForRerun(GameTestInfo old, GameTestInfo next, GameTestRunner runner) { }
        });
    }

    /** The server copies the registry's list when it is made; that copy is the one it batches. */
    @SuppressWarnings("unchecked")
    private static List<TestFunction> testFunctions(GameTestServer server) {
        try {
            var field = GameTestServer.class.getDeclaredField("testFunctions");
            field.setAccessible(true);
            return (List<TestFunction>) field.get(server);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot select game tests", e);
        }
    }
}
