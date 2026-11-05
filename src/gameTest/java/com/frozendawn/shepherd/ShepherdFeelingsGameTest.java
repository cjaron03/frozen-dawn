package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.miller.Drives.Drive;
import com.frozendawn.miller.Feelings.Feeling;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.miller.PatternSavedData;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What reaches her inner state, and what must not. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdFeelingsGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);

    @BeforeBatch(batch = "shepherd_feel_saw") public static void sawBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_saw") public static void sawAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_far") public static void farBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_far") public static void farAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_offline") public static void offlineBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_offline") public static void offlineAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_cooldown") public static void cooldownBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_cooldown") public static void cooldownAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_talk") public static void talkBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_talk") public static void talkAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_death") public static void deathBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_death") public static void deathAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_poll") public static void pollBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_poll") public static void pollAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_reload") public static void reloadBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_reload") public static void reloadAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_replay") public static void replayBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_replay") public static void replayAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_skip") public static void skipBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_skip") public static void skipAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_rests") public static void restsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_rests") public static void restsAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_fades") public static void fadesBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_fades") public static void fadesAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_wary") public static void waryBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_wary") public static void waryAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_asking") public static void askingBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_asking") public static void askingAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_hub") public static void hubBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_hub") public static void hubAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_resting") public static void restingBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_resting") public static void restingAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_feel_reboot") public static void rebootBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_feel_reboot") public static void rebootAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    private static MillerPattern.Snapshot inner(GameTestHelper h) {
        return MillerPattern.snapshot(h.getLevel().getServer());
    }

    private static ServerPlayer player(GameTestHelper h) {
        return ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
    }

    private static ShepherdEntity awake(GameTestHelper h, ServerPlayer player) {
        data(h).stage(Stage.AWAKE);
        return ShepherdEntity.spawnFrom(h.getLevel(), h.absolutePos(SPOT), player.getUUID());
    }

    private static void assertFeelsNothing(GameTestHelper h) {
        h.assertTrue(inner(h).history().isEmpty(), "Reasons: " + inner(h).history());
        inner(h).feelings().forEach((feeling, value) -> h.assertTrue(value == 0, feeling + "=" + value));
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_saw", timeoutTicks = 400)
    public static void shepherdADeedSheSawMovesHerFeelings(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            ShepherdReactions.saw(player, Deed.WARM_SHELTER);
            h.assertTrue(inner(h).feelings().get(Feeling.JOY) > 0, "She is glad");
            h.assertTrue(inner(h).history().getFirst().who().equals(player.getUUID()), "And knows who did it");
            h.assertTrue(data(h).affinity() == Deed.WARM_SHELTER.score(), "The score moves as it did: " + data(h).affinity());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_far", timeoutTicks = 400)
    public static void shepherdADeedOutOfRangeFeelsNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var near = player(h);
            awake(h, near);
            var far = ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(ShepherdPolicy.HEARD_BLOCKS + 1, 0, 0));
            ShepherdReactions.saw(far, Deed.ARCHIVIST_KILLED);
            assertFeelsNothing(h);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_offline", timeoutTicks = 400)
    public static void shepherdAnOfflineUnitFeelsNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            she.die(h.getLevel().damageSources().generic());
            ShepherdReactions.saw(player, Deed.ARCHIVIST_KILLED);
            assertFeelsNothing(h);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_cooldown", timeoutTicks = 400)
    public static void shepherdADeedOnCooldownFeelsNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            ShepherdReactions.saw(player, Deed.READ_LOG);
            ShepherdReactions.saw(player, Deed.READ_LOG);
            h.assertTrue(inner(h).history().size() == 1, "Reasons: " + inner(h).history());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_talk", timeoutTicks = 400)
    public static void shepherdADeedDuringATalkStillFeels(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            she.interact(player, InteractionHand.MAIN_HAND);
            h.assertTrue(ShepherdTalk.live(), "They are talking");
            ShepherdReactions.saw(player, Deed.CALLED_SHEPHERD);
            h.assertTrue(inner(h).history().size() == 1, "She heard it");
            ShepherdTalk.close(player);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_death", timeoutTicks = 400)
    public static void shepherdAPlayersDeathIsNotAnEvent(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            player.setGameMode(GameType.SURVIVAL);
            awake(h, player);
            int[] ticks = {0};
            h.startSequence()
                    .thenExecuteAfter(2, () -> {
                        player.hurt(h.getLevel().damageSources().genericKill(), 1);
                        NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, h.getLevel().damageSources().generic()));
                    })
                    .thenWaitUntil(() -> h.assertTrue(++ticks[0] > ShepherdReactions.POLL_TICKS + 10, "A poll has run"))
                    .thenExecute(() -> assertFeelsNothing(h))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_poll", timeoutTicks = 400)
    public static void shepherdThePollFeedsCompany(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            awake(h, player(h));
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(inner(h).drives().get(Drive.COMPANY) < 0.5, "Company: " + inner(h).drives().get(Drive.COMPANY)))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_reload", timeoutTicks = 400)
    public static void shepherdFeelingsSurviveAReload(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            ShepherdReactions.saw(player, Deed.ARCHIVIST_KILLED);
            var registries = h.getLevel().registryAccess();
            var saved = PatternSavedData.get(h.getLevel().getServer()).save(new CompoundTag(), registries);
            h.assertTrue(saved.getCompound("feelings").contains("REPROACH"), "It is in the save");
            h.assertTrue(PatternSavedData.load(saved, registries).save(new CompoundTag(), registries).equals(saved), "And comes back the same");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_replay", timeoutTicks = 400)
    public static void shepherdReplayClearsHerFeelings(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            ShepherdReactions.saw(player, Deed.ARCHIVIST_KILLED);
            h.assertTrue(ShepherdTalk.replay(h.getLevel().getServer()), "She is awake to replay");
            assertFeelsNothing(h);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_skip", timeoutTicks = 400)
    public static void shepherdASkippedDayAgesHerFeelings(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            ShepherdReactions.saw(player, Deed.ARCHIVIST_KILLED);
            double before = inner(h).feelings().get(Feeling.REPROACH);
            ShepherdReactions.skipDay(h.getLevel().getServer());
            double after = inner(h).feelings().get(Feeling.REPROACH);
            h.assertTrue(Math.abs(after - before / 2) < 0.01, before + " to " + after);
            h.succeed();
        });
    }

    private static void assertFace(GameTestHelper h, ShepherdEntity she, Mood mood) {
        h.assertTrue(she.mood() == mood, "Her face: " + she.mood());
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_rests", timeoutTicks = 400)
    public static void shepherdRestsOnWhatSheFeels(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            ShepherdReactions.saw(player, Deed.READ_LOG);
            assertFace(h, she, Mood.NEUTRAL);
            h.startSequence()
                    .thenExecuteAfter(ShepherdPolicy.REACT_TICKS + 5, () -> assertFace(h, she, Mood.THINKING))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_fades", timeoutTicks = 400)
    public static void shepherdTheFeelingFadesToNeutral(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            ShepherdReactions.saw(player, Deed.ARCHIVIST_KILLED);
            h.startSequence()
                    .thenExecuteAfter(ShepherdPolicy.REACT_TICKS + 5, () -> {
                        assertFace(h, she, Mood.SAD);
                        ShepherdReactions.skipDay(h.getLevel().getServer());
                        ShepherdReactions.skipDay(h.getLevel().getServer());
                    })
                    .thenExecuteAfter(3, () -> assertFace(h, she, Mood.NEUTRAL))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_wary", timeoutTicks = 400)
    public static void shepherdAtWaryShePlaysNoFeeling(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            data(h).affinity(-300);
            ShepherdReactions.saw(player, Deed.ARCHIVIST_KILLED);
            h.startSequence()
                    .thenExecuteAfter(ShepherdPolicy.REACT_TICKS + 5, () -> {
                        assertFace(h, she, Mood.RECORDING);
                        h.assertTrue(inner(h).feelings().get(Feeling.DISTRESS) > 0, "She still feels it");
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_asking", timeoutTicks = 400)
    public static void shepherdAskingStaysThinking(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            ShepherdReactions.saw(player, Deed.CLEAR_NIGHT);
            she.askFor(ShepherdPolicy.REACT_TICKS + 40);
            h.startSequence()
                    .thenExecuteAfter(ShepherdPolicy.REACT_TICKS + 5, () -> assertFace(h, she, Mood.THINKING))
                    .thenSucceed();
        });
    }

    /** The talk screen's hub is always Neutral, so her body stays with it. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_hub", timeoutTicks = 400)
    public static void shepherdATalkKeepsHerFace(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            ShepherdReactions.saw(player, Deed.ARCHIVIST_KILLED);
            she.interact(player, InteractionHand.MAIN_HAND);
            h.startSequence()
                    .thenExecuteAfter(5, () -> {
                        assertFace(h, she, Mood.NEUTRAL);
                        h.assertTrue(!she.resting(), "A conversation has no resting face");
                        ShepherdTalk.close(player);
                    })
                    .thenExecuteAfter(3, () -> assertFace(h, she, Mood.SAD))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_resting", timeoutTicks = 400)
    public static void shepherdIsRestingOnlyWhenSheRests(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            h.startSequence()
                    .thenExecuteAfter(3, () -> {
                        h.assertTrue(she.resting(), "She has settled");
                        ShepherdReactions.saw(player, Deed.CLEAR_NIGHT);
                        she.react(Mood.HAPPY);
                        h.assertTrue(!she.resting(), "A reaction is not a rest");
                    })
                    .thenExecuteAfter(ShepherdPolicy.REACT_TICKS + 5, () -> h.assertTrue(she.resting(), "She has settled again"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_feel_reboot", timeoutTicks = 400)
    public static void shepherdRebootsOntoHerFeeling(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            ShepherdReactions.saw(player, Deed.ARCHIVIST_KILLED);
            she.die(h.getLevel().damageSources().generic());
            she.rebootIn(5);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(!she.offline(), "Waiting for the reboot"))
                    .thenExecute(() -> assertFace(h, she, Mood.SAD))
                    .thenSucceed();
        });
    }
}
