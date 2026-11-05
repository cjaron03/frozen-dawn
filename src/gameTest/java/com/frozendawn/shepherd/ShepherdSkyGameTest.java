package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.aurora.AuroraPolicy;
import com.frozendawn.cyclone.CycloneSavedData;
import com.frozendawn.cyclone.CycloneSeverity;
import com.frozendawn.cyclone.CycloneStage;
import com.frozendawn.cyclone.CycloneStorm;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.event.PlayerTickHandler;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.miller.Drives.Drive;
import com.frozendawn.miller.Heard;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.phase.PhaseManager;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.world.BlastPitWarmZoneRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The sky, and what she says about going without. Each test owns a batch, and every one moves the world's clock. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdSkyGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);
    private static final int TOP_Y = 112, BELOW_THE_TOP_Y = 100, A_LOOK = 25, MIDNIGHT = 18000, NOON = 6000;
    private static final int ASK = 0, KIND = 1, BLUNT = 2, SARCASTIC = 3, CONTINUE = -1;
    private static final float STARS = 0.8F;
    private static final int TRUSTED = 500, CLOSE = 850;

    private static long apocalypseTicks;
    private static final Set<BlockPos> warmed = new HashSet<>();

    private static void before(ServerLevel l) {
        ShepherdTestWorld.fresh(l);
        ShepherdTestWorld.keepThePit(l);
        apocalypseTicks = ApocalypseState.get(l.getServer()).getApocalypseTicks();
    }

    private static void after(ServerLevel l) {
        CycloneSavedData.get(l.getServer()).end();
        warmed.forEach(at -> BlastPitWarmZoneRegistry.unregister(l, at));
        warmed.clear();
        ApocalypseState.get(l.getServer()).setApocalypseTicks(apocalypseTicks, l.getServer());
        ShepherdTestWorld.restoreThePit(l);
        ShepherdTestWorld.clear(l);
    }

    @BeforeBatch(batch = "shepherd_t3_stars") public static void t3StarsBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_t3_stars") public static void t3StarsAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_t3_blunt") public static void t3BluntBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_t3_blunt") public static void t3BluntAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_top_night") public static void topNightBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_top_night") public static void topNightAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_top_height") public static void topHeightBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_top_height") public static void topHeightAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_top_cold") public static void topColdBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_top_cold") public static void topColdAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_top_cyclone") public static void topCycloneBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_top_cyclone") public static void topCycloneAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_top_lapsed") public static void topLapsedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_top_lapsed") public static void topLapsedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_top_once") public static void topOnceBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_top_once") public static void topOnceAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_top_real") public static void topRealBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_top_real") public static void topRealAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_aurora_seen") public static void auroraSeenBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_aurora_seen") public static void auroraSeenAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_aurora_pool") public static void auroraPoolBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_aurora_pool") public static void auroraPoolAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_stars_kept") public static void starsKeptBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_stars_kept") public static void starsKeptAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_stars_tonight") public static void starsTonightBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_stars_tonight") public static void starsTonightAfter(ServerLevel l) { after(l); }

    @BeforeBatch(batch = "shepherd_t4_first") public static void t4FirstBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_t4_first") public static void t4FirstAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_t4_days") public static void t4DaysBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_t4_days") public static void t4DaysAfter(ServerLevel l) { after(l); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    /** The world's clock at this much of the way through, on this time of day. It also sets the sky. */
    private static void skyAt(GameTestHelper h, float progress, int timeOfDay) {
        var state = ApocalypseState.get(h.getLevel().getServer());
        long day = (long) StrictMath.ceil(progress * state.getTotalDays());
        state.setApocalypseTicks(day * ShepherdPolicy.DAY_TICKS + timeOfDay, h.getLevel().getServer());
    }

    private static ServerPlayer player(GameTestHelper h, int y) {
        var at = h.absoluteVec(Vec3.atCenterOf(SPOT));
        return ShepherdTestWorld.onlinePlayer(h, new Vec3(at.x + 1, y, at.z));
    }

    /** She is up beside them, at this score. */
    private static ShepherdEntity met(GameTestHelper h, ServerPlayer player, int score) {
        data(h).stage(Stage.AWAKE);
        data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
        data(h).flag("t1_asked", true);
        data(h).flag("t2_asked", true);
        data(h).affinity(score);
        return ShepherdEntity.spawnAt(h.getLevel(), player.position().add(1, 1, 0), player.getUUID());
    }

    /** Warm enough to be asked anything, where the world at this point is not. */
    private static void warm(GameTestHelper h, ServerPlayer player) {
        var at = player.blockPosition();
        BlastPitWarmZoneRegistry.register(h.getLevel(), at);
        warmed.add(at);
    }

    /** At the top with her, on the way to the stars. */
    private static ShepherdEntity upThere(GameTestHelper h, ServerPlayer player) {
        var she = met(h, player, TRUSTED);
        data(h).flag("t3_asked", true);
        data(h).flag("t3_going", true);
        return she;
    }

    private static void say(ServerPlayer player, int... slots) {
        for (int slot : slots) ShepherdTalk.reply(player, ShepherdTalk.liveId(), slot);
    }

    private static void assertPrompt(GameTestHelper h, String node) {
        h.assertTrue(Objects.equals(node, data(h).promptNode()), "Waiting: " + data(h).promptNode());
    }

    private static void assertNoPrompt(GameTestHelper h, String why) {
        h.assertTrue(data(h).promptNode() == null, why + ": " + data(h).promptNode());
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t3_stars", timeoutTicks = 300)
    public static void shepherdT3WaitsForTheStars(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            skyAt(h, 0.40F, NOON);
            var player = player(h, 64);
            warm(h, player);
            met(h, player, TRUSTED);
            h.startSequence()
                    .thenExecuteAfter(A_LOOK, () -> {
                        h.assertTrue(PlayerTickHandler.resolvedTemperature(player) > ShepherdPolicy.COLD_LINE_C, "Warm: " + PlayerTickHandler.resolvedTemperature(player));
                        assertNoPrompt(h, "No stars, so nothing to ask");
                        skyAt(h, STARS, NOON);
                    })
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_SKY))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t3_blunt", timeoutTicks = 300)
    public static void shepherdBluntT3AsksAgainInThreeDays(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            skyAt(h, STARS, NOON);
            var player = player(h, 64);
            warm(h, player);
            var she = met(h, player, TRUSTED);
            var server = h.getLevel().getServer();
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_SKY))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        say(player, BLUNT, CONTINUE);
                        h.assertTrue(data(h).affinity() == TRUSTED - 15, "Scored: " + data(h).affinity());
                        h.assertFalse(data(h).flag("t3_going"), "Not going");
                        data(h).affinity(TRUSTED);
                        ShepherdReactions.skipDay(server);
                        ShepherdReactions.skipDay(server);
                    })
                    .thenExecuteAfter(A_LOOK, () -> {
                        assertNoPrompt(h, "Two days is not three");
                        ShepherdReactions.skipDay(server);
                    })
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_SKY))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_top_night", timeoutTicks = 300, skyAccess = true)
    public static void shepherdTheTopWaitsForAClearNight(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            skyAt(h, STARS, NOON);
            var player = player(h, TOP_Y);
            upThere(h, player);
            var roof = player.blockPosition().above(3);
            h.getLevel().setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
            h.startSequence()
                    .thenExecuteAfter(A_LOOK, () -> assertNoPrompt(h, "Not by day"))
                    // Open sky is read from the light, and a new roof's shadow takes its time.
                    .thenWaitUntil(() -> h.assertFalse(h.getLevel().canSeeSky(player.blockPosition()), "Waiting for the shadow"))
                    .thenExecute(() -> skyAt(h, STARS, MIDNIGHT))
                    .thenExecuteAfter(A_LOOK, () -> {
                        assertNoPrompt(h, "Not under a roof");
                        h.getLevel().setBlockAndUpdate(roof, Blocks.AIR.defaultBlockState());
                    })
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.AT_THE_TOP))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_top_height", timeoutTicks = 300, skyAccess = true)
    public static void shepherdTheTopIsHigh(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            skyAt(h, STARS, MIDNIGHT);
            var player = player(h, BELOW_THE_TOP_Y);
            upThere(h, player);
            h.startSequence()
                    .thenExecuteAfter(A_LOOK, () -> {
                        assertNoPrompt(h, "Not high enough");
                        player.setPos(player.getX(), ShepherdPolicy.SKY_HIGH_Y, player.getZ());
                    })
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.AT_THE_TOP))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_top_cold", timeoutTicks = 300, skyAccess = true)
    public static void shepherdTheTopIgnoresTheCold(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            skyAt(h, STARS, MIDNIGHT);
            var player = player(h, TOP_Y);
            upThere(h, player);
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.AT_THE_TOP))
                    .thenExecute(() -> h.assertTrue(PlayerTickHandler.resolvedTemperature(player) <= ShepherdPolicy.COLD_LINE_C, "Bitter: " + PlayerTickHandler.resolvedTemperature(player)))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_top_cyclone", timeoutTicks = 300, skyAccess = true)
    public static void shepherdTheTopWaitsOutACyclone(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            skyAt(h, STARS, MIDNIGHT);
            var player = player(h, TOP_Y);
            upThere(h, player);
            var cyclones = CycloneSavedData.get(h.getLevel().getServer());
            long clock = h.getLevel().getGameTime();
            cyclones.startForced(CycloneStorm.roll(42, CycloneSeverity.CATASTROPHIC, player.getBlockX(), player.getBlockZ() + 2000, clock, UUID.randomUUID(), 0)
                    .atStage(CycloneStage.PEAK, clock));
            h.startSequence()
                    .thenExecuteAfter(A_LOOK, () -> {
                        assertNoPrompt(h, "Not with a storm out");
                        cyclones.end();
                    })
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.AT_THE_TOP))
                    .thenSucceed();
        });
    }

    /** Left unanswered it is a thought about tonight's sky. It must not play in a basement next week. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_top_lapsed", timeoutTicks = 300, skyAccess = true)
    public static void shepherdTheTopGoesWithTheNight(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            skyAt(h, STARS, MIDNIGHT);
            var player = player(h, TOP_Y);
            upThere(h, player);
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.AT_THE_TOP))
                    .thenExecute(() -> skyAt(h, STARS, NOON))
                    .thenWaitUntil(() -> assertNoPrompt(h, "The night is over"))
                    .thenExecute(() -> h.assertFalse(data(h).flag("t3_top"), "And the top is still to come"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_top_once", timeoutTicks = 300, skyAccess = true)
    public static void shepherdTheTopPlaysOnce(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            skyAt(h, STARS, MIDNIGHT);
            var player = player(h, TOP_Y);
            var she = upThere(h, player);
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.AT_THE_TOP))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        h.assertTrue("t3.top".equals(ShepherdTalk.liveNode()), "On: " + ShepherdTalk.liveNode());
                        say(player, KIND);
                        h.assertTrue(data(h).affinity() == TRUSTED + 30, "Scored: " + data(h).affinity());
                        h.assertTrue(MillerPattern.remembers(h.getLevel().getServer(), player.getUUID(), "t3_stars"), "She keeps it");
                        say(player, CONTINUE);
                    })
                    .thenExecuteAfter(A_LOOK, () -> assertNoPrompt(h, "Once"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_top_real", timeoutTicks = 300, skyAccess = true)
    public static void shepherdARealAuroraChangesTheTop(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            skyAt(h, STARS, MIDNIGHT);
            var player = player(h, TOP_Y);
            var she = upThere(h, player);
            data(h).flag("saw_aurora", true);
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.AT_THE_TOP))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        h.assertTrue(ShepherdTalk.liveLines().equals(List.of("shepherd.t3.top.gone", "shepherd.t3.top.saw", "shepherd.t3.top.mine", "shepherd.t3.top.stars", "shepherd.t3.top.enough")),
                                "Her lines: " + ShepherdTalk.liveLines());
                    })
                    .thenSucceed();
        });
    }

    /** A clear early night with a display on, found from the world's own seed. */
    private static long auroraTime(GameTestHelper h) {
        var level = h.getLevel();
        var state = ApocalypseState.get(level.getServer());
        long seed = AuroraPolicy.mix(level.getSeed() ^ 0x46444155524f5241L);
        for (int day = 1; PhaseManager.getPhase(day, state.getTotalDays()) <= 1; day++) {
            long midnight = (long) day * ShepherdPolicy.DAY_TICKS + 16000;
            var display = AuroraPolicy.nightly(seed, midnight, PhaseManager.getPhase(day, state.getTotalDays()), PhaseManager.getProgress(day, state.getTotalDays()));
            if (display.durationTicks() > 0) return display.startTick() + display.durationTicks() / 2;
        }
        throw new IllegalStateException("No aurora before phase 2 on this seed");
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_aurora_seen", timeoutTicks = 500, skyAccess = true)
    public static void shepherdSeesARealAuroraUnderOpenSky(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var level = h.getLevel();
            level.setWeatherParameters(24000, 0, false, false);
            var player = player(h, TOP_Y);
            var she = met(h, player, TRUSTED);
            var over = she.blockPosition().above(6);
            // Wide, because she drifts: a test that had her walk out from under one block would be testing her walk.
            var roof = BlockPos.betweenClosedStream(over.offset(-8, 0, -8), over.offset(8, 0, 8)).map(BlockPos::immutable).toList();
            roof.forEach(at -> level.setBlockAndUpdate(at, Blocks.STONE.defaultBlockState()));
            h.startSequence()
                    // Open sky is read from the light, and a new roof's shadow takes its time.
                    .thenWaitUntil(() -> h.assertFalse(level.canSeeSky(she.blockPosition()), "Waiting for the shadow"))
                    .thenExecute(() -> ApocalypseState.get(level.getServer()).setApocalypseTicks(auroraTime(h), level.getServer()))
                    .thenExecuteAfter(ShepherdReactions.POLL_TICKS + 10, () -> {
                        h.assertFalse(data(h).flag("saw_aurora"), "Nothing through a roof");
                        roof.forEach(at -> level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState()));
                    })
                    .thenWaitUntil(() -> h.assertTrue(data(h).flag("saw_aurora"), "She saw it"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_aurora_pool", timeoutTicks = 400, skyAccess = true)
    public static void shepherdNoImaginedAuroraAfterARealOne(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            skyAt(h, 0.40F, MIDNIGHT);
            var player = ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
            met(h, player, 0);
            MillerPattern.want(h.getLevel().getServer(), Drive.CURIOSITY, 1);
            data(h).flag("saw_aurora", true);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(ShepherdReactions.said() != null, "Something is said"))
                    .thenExecute(() -> h.assertTrue(!ShepherdReactions.said().contains("imagined_aurora"), "She has no need to: " + ShepherdReactions.said()))
                    .thenSucceed();
        });
    }

    /** A clear night she kept from before the storms: that is a sky she has seen. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_stars_kept", timeoutTicks = 300, skyAccess = true)
    public static void shepherdStarsLineReadsTheKeepsake(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            skyAt(h, STARS, MIDNIGHT);
            var player = player(h, TOP_Y);
            var she = upThere(h, player);
            var server = h.getLevel().getServer();
            MillerPattern.heard(server, player.getUUID(), new Heard.Reply(null, List.of(), "clear_night"));
            for (int day = 0; day < 80; day++) MillerPattern.skipDay(server);
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.AT_THE_TOP))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        h.assertTrue(ShepherdTalk.liveLines().contains("shepherd.t3.top.stars.again"), "Her lines: " + ShepherdTalk.liveLines());
                        h.assertFalse(ShepherdTalk.liveLines().contains("shepherd.t3.top.stars"), "In its place");
                    })
                    .thenSucceed();
        });
    }

    /** The night they climbed for is a clear night too, and she keeps those: it cannot be what makes the stars old news. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_stars_tonight", timeoutTicks = 300, skyAccess = true)
    public static void shepherdTonightIsNotTheNightSheRemembers(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            skyAt(h, STARS, MIDNIGHT);
            var player = player(h, TOP_Y);
            var she = upThere(h, player);
            var server = h.getLevel().getServer();
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.AT_THE_TOP))
                    .thenWaitUntil(() -> h.assertTrue(MillerPattern.remembers(server, player.getUUID(), "clear_night"), "She keeps tonight"))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        h.assertTrue(ShepherdTalk.liveLines().contains("shepherd.t3.top.stars"), "Her lines: " + ShepherdTalk.liveLines());
                    })
                    .thenSucceed();
        });
    }

    /** Nothing in it needs the sky, so a Close world with no stars yet does not wait for them. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t4_first", timeoutTicks = 300)
    public static void shepherdT4CanComeBeforeT3(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            skyAt(h, 0.02F, NOON);
            var player = ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
            met(h, player, CLOSE);
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_SWITCH))
                    .thenExecute(() -> h.assertFalse(data(h).flag("t3_asked"), "The sky is passed over, not spent"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t4_days", timeoutTicks = 300)
    public static void shepherdT4SaysHowLongSheWasOut(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            skyAt(h, 0.02F, NOON);
            var player = ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
            var she = met(h, player, CLOSE);
            data(h).wokeAt(40L * ShepherdPolicy.DAY_TICKS);
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_SWITCH))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        var clock = ShepherdTalk.liveSent().lines().get(2);
                        h.assertTrue(clock.args().equals(List.of("27")), "The days: " + clock.args());
                        say(player, KIND);
                        h.assertTrue(data(h).affinity() == CLOSE + 30, "Scored: " + data(h).affinity());
                        h.assertTrue(MillerPattern.remembers(h.getLevel().getServer(), player.getUUID(), "t4_off"), "She keeps it");
                    })
                    .thenSucceed();
        });
    }
}
