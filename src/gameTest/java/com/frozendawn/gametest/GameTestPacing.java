package com.frozendawn.gametest;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectLabGameTest;
import com.frozendawn.maeve.BlindArchitectGameTest;
import com.frozendawn.maeve.LabBridgeGameTest;
import com.frozendawn.maeve.MaeveConvergenceGameTest;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
 *
 * <p>{@value #SHARD_PROPERTY}: {@code index/count}. The gate runs one server per shard side by side, and each keeps
 * only its share of whole batches, cut as vanilla cuts them, so every batch holds the same tests as in a single run.
 * Each shard records what it kept beside its report, for the gate to prove the shards cover every test exactly once.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class GameTestPacing {
    public static final String SPRINT_PROPERTY = "frozendawn.gametest.sprint";
    public static final String ONLY_PROPERTY = "frozendawn.gametest.only";
    public static final String SHARD_PROPERTY = "frozendawn.gametest.shard";
    /** GameTestBatchFactory's batch size. */
    private static final int BATCH_SIZE = 50;
    private static final String DEFAULT_BATCH = "defaultBatch";
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
        String shard = System.getProperty(SHARD_PROPERTY, "");
        if (!shard.isBlank()) keepShard(tests, shard);
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
        // A selection or shard may hold no defaultBatch test, whose before-batch hook would otherwise install the report.
        if (!System.getProperty(ONLY_PROPERTY, "").isBlank() || !System.getProperty(SHARD_PROPERTY, "").isBlank())
            GameTestReporting.installReporter(server.overworld());
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

    /**
     * Keeps this shard's whole batches. Default batches go round-robin; named batches go whole, largest first, to the
     * shard holding the fewest tests so far. Batch names break ties, so every shard, each in its own JVM, computes the
     * same split. Writes what it kept beside the report, before any test runs.
     */
    private static void keepShard(List<TestFunction> tests, String spec) {
        String[] parts = spec.split("/");
        int index = Integer.parseInt(parts[0].trim());
        int count = Integer.parseInt(parts[1].trim());
        if (count < 1 || index < 0 || index >= count) throw new IllegalArgumentException("No such shard: " + spec);
        Map<String, List<TestFunction>> batches = new TreeMap<>();
        for (TestFunction test : tests) batches.computeIfAbsent(test.batchName(), name -> new ArrayList<>()).add(test);
        int[] load = new int[count];
        Set<String> kept = new HashSet<>();
        // Cut as GameTestBatchFactory cuts it, in list order: what a shard keeps, it cuts back into the same batches.
        List<TestFunction> unnamed = batches.remove(DEFAULT_BATCH);
        List<List<TestFunction>> defaults = Lists.partition(unnamed == null ? List.of() : unnamed, BATCH_SIZE);
        for (int i = 0; i < defaults.size(); i++) assign(defaults.get(i), i % count, index, load, kept);
        batches.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String, List<TestFunction>> batch) -> -batch.getValue().size())
                        .thenComparing(Map.Entry::getKey))
                .forEach(batch -> assign(batch.getValue(), lightest(load), index, load, kept));
        int total = tests.size();
        String all = HexFormat.of().formatHex(sha256(tests.stream().map(TestFunction::testName).sorted().collect(Collectors.joining("\n"))));
        tests.removeIf(test -> !kept.contains(test.testName()));
        String report = System.getProperty(GameTestReporting.REPORT_PROPERTY, "");
        if (report.isBlank()) throw new IllegalStateException("A game test shard needs " + GameTestReporting.REPORT_PROPERTY);
        try {
            Files.writeString(Path.of(report + ".shard"), "index=%d%ncount=%d%ntotal=%d%nkept=%d%nall=%s%n"
                    .formatted(index, count, total, tests.size(), all));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot record game test shard " + spec, e);
        }
        FrozenDawn.LOGGER.info("Game test shard {}: {} of {} tests", spec, tests.size(), total);
    }

    private static void assign(List<TestFunction> batch, int shard, int index, int[] load, Set<String> kept) {
        load[shard] += batch.size();
        if (shard == index) batch.forEach(test -> kept.add(test.testName()));
    }

    private static int lightest(int[] load) {
        int best = 0;
        for (int i = 1; i < load.length; i++) if (load[i] < load[best]) best = i;
        return best;
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
