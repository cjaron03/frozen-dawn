package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.event.PlayerTickHandler;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.miller.ImpressionStore.Theory;
import com.frozendawn.miller.LogEntry;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.miller.PatternSavedData;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.shepherd.ShepherdTalk.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Her private log, reached the way the game reaches it. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdLogGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);
    private static final int A_POLL = ShepherdReactions.POLL_TICKS + 10, TOP_Y = 112, MIDNIGHT = 18000;
    private static final float STARS = 0.8F;
    private static final String NOTE = "shepherd.log.note.", READ = "hub.log.read";
    private static final int SCORE = 100, COST = 5;

    private static long apocalypseTicks;

    @BeforeBatch(batch = "shepherd_log_day") public static void dayBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_log_day") public static void dayAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_log_cold") public static void coldBefore(ServerLevel l) {
        ShepherdTestWorld.fresh(l);
        apocalypseTicks = ApocalypseState.get(l.getServer()).getApocalypseTicks();
    }
    @AfterBatch(batch = "shepherd_log_cold") public static void coldAfter(ServerLevel l) {
        ApocalypseState.get(l.getServer()).setApocalypseTicks(apocalypseTicks, l.getServer());
        l.setWeatherParameters(0, 0, false, false);
        ShepherdTestWorld.clear(l);
    }
    @BeforeBatch(batch = "shepherd_log_death") public static void deathBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_log_death") public static void deathAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_log_unseen") public static void unseenBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_log_unseen") public static void unseenAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_log_elsewhere") public static void elsewhereBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_log_elsewhere") public static void elsewhereAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_log_aloud") public static void aloudBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_log_aloud") public static void aloudAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_log_close_call") public static void closeCallBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_log_close_call") public static void closeCallAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_log_fight") public static void fightBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_log_fight") public static void fightAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_read_once") public static void readOnceBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_read_once") public static void readOnceAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_read_second") public static void readSecondBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_read_second") public static void readSecondAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_read_empty") public static void readEmptyBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_read_empty") public static void readEmptyAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_read_leaving") public static void readLeavingBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_read_leaving") public static void readLeavingAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_read_closed") public static void readClosedBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_read_closed") public static void readClosedAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_read_wary") public static void readWaryBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_read_wary") public static void readWaryAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_read_blind") public static void readBlindBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_read_blind") public static void readBlindAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_read_reload") public static void readReloadBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_read_reload") public static void readReloadAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_screen_log") public static void screenLogBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_screen_log") public static void screenLogAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_screen_language") public static void screenLanguageBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_screen_language") public static void screenLanguageAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_screen_evidence") public static void screenEvidenceBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_screen_evidence") public static void screenEvidenceAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_screen_talk") public static void screenTalkBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_screen_talk") public static void screenTalkAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    private static ServerPlayer player(GameTestHelper h) {
        var player = ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static ShepherdEntity awake(GameTestHelper h, ServerPlayer player) {
        data(h).stage(Stage.AWAKE);
        return ShepherdEntity.spawnFrom(h.getLevel(), h.absolutePos(SPOT), player.getUUID());
    }

    private static void kill(GameTestHelper h, ServerPlayer player) {
        player.hurt(h.getLevel().damageSources().genericKill(), 1000);
        h.assertTrue(player.isDeadOrDying(), "They stopped");
    }

    /** On their feet again, as respawning leaves them to anyone watching. */
    private static void revive(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
    }

    /** The day ends. Her clock moves and the next real poll settles it. */
    private static void skip(GameTestHelper h) {
        ShepherdReactions.skipDay(h.getLevel().getServer());
    }

    private static LogEntry last(GameTestHelper h) {
        var log = MillerPattern.log(h.getLevel().getServer());
        h.assertTrue(!log.isEmpty(), "She wrote the day down");
        return log.getFirst();
    }

    private static void assertNote(GameTestHelper h, String note) {
        String written = last(h).note();
        h.assertTrue(note == null ? written == null : (NOTE + note).equals(written), "Expected " + note + ", wrote " + written);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_day", timeoutTicks = 600)
    public static void shepherdADayLeavesAnEntry(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> {
                        h.assertTrue(MillerPattern.log(h.getLevel().getServer()).isEmpty(), "Nothing has settled yet");
                        skip(h);
                    })
                    .thenExecuteAfter(A_POLL, () -> {
                        h.assertTrue(MillerPattern.log(h.getLevel().getServer()).size() == 1, "One entry");
                        assertNote(h, null);
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_cold", timeoutTicks = 800, skyAccess = true)
    public static void shepherdAColdDayIsLogged(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var state = ApocalypseState.get(h.getLevel().getServer());
            state.setApocalypseTicks((long) StrictMath.ceil(STARS * state.getTotalDays()) * ShepherdPolicy.DAY_TICKS + MIDNIGHT, h.getLevel().getServer());
            var at = h.absoluteVec(Vec3.atCenterOf(SPOT));
            var player = ShepherdTestWorld.onlinePlayer(h, new Vec3(at.x + 1, TOP_Y, at.z));
            var she = awake(h, player);
            she.teleportTo(player.getX() + 1, player.getY() + 1, player.getZ());
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> h.assertTrue(PlayerTickHandler.resolvedTemperature(player) <= ShepherdPolicy.COLD_LINE_C, "Cold: " + PlayerTickHandler.resolvedTemperature(player)))
                    .thenExecuteAfter(A_POLL, () -> skip(h))
                    .thenExecuteAfter(A_POLL, () -> {
                        assertNote(h, "cold.product");
                        h.assertTrue(ShepherdReactions.said() == null, "No line played: she has not met them, and it was logged anyway");
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_death", timeoutTicks = 800)
    public static void shepherdADeathSheSawIsLogged(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> kill(h, player))
                    .thenExecuteAfter(A_POLL, () -> skip(h))
                    .thenExecuteAfter(A_POLL, () -> {
                        assertNote(h, null);
                        revive(player);
                        skip(h);
                    })
                    .thenExecuteAfter(2 * A_POLL, () -> assertNote(h, "back.product"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_unseen", timeoutTicks = 800)
    public static void shepherdADeathSheDidNotSeeLeavesNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            data(h).waiting(true);
            player.setPos(player.position().add(ShepherdPolicy.HEARD_BLOCKS + 8, 0, 0));
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> {
                        kill(h, player);
                        revive(player);
                        player.setPos(h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
                    })
                    .thenExecuteAfter(A_POLL, () -> skip(h))
                    .thenExecuteAfter(A_POLL, () -> assertNote(h, null))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_elsewhere", timeoutTicks = 800)
    public static void shepherdAReturnSheNeverSawLeavesNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var other = player(h);
            awake(h, player);
            data(h).waiting(true);
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> {
                        kill(h, player);
                        player.setPos(player.position().add(ShepherdPolicy.HEARD_BLOCKS + 8, 0, 0));
                        revive(player);
                    })
                    .thenExecuteAfter(A_POLL, () -> skip(h))
                    .thenExecuteAfter(A_POLL, () -> {
                        h.assertTrue(other.distanceTo(player) > ShepherdPolicy.HEARD_BLOCKS, "They came back somewhere else");
                        assertNote(h, null);
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_aloud", timeoutTicks = 800)
    public static void shepherdADeathSheSawChangesNothingAloud(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var server = h.getLevel().getServer();
            var player = player(h);
            awake(h, player);
            data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
            data(h).affinity(100);
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> kill(h, player))
                    .thenExecuteAfter(A_POLL, () -> revive(player))
                    .thenExecuteAfter(A_POLL, () -> {
                        var mind = MillerPattern.snapshot(server);
                        h.assertTrue(mind.strongest() == null, "No feeling: " + mind.strongest());
                        h.assertTrue(mind.history().isEmpty(), "No reason kept");
                        h.assertTrue(mind.keepsakes().getOrDefault(player.getUUID(), 0) == 0, "No keepsake");
                        h.assertTrue(data(h).affinity() == 100, "No score");
                        h.assertTrue(ShepherdReactions.said() == null, "Nothing said: " + ShepherdReactions.said());
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_close_call", timeoutTicks = 600)
    public static void shepherdACloseCallIsLogged(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            player.setHealth(6);
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> {
                        player.hurt(h.getLevel().damageSources().generic(), 2);
                        h.assertTrue(player.isAlive() && player.getHealth() <= ShepherdPolicy.CLOSE_CALL_HEALTH, "Left on two hearts: " + player.getHealth());
                        skip(h);
                    })
                    .thenExecuteAfter(A_POLL, () -> assertNote(h, "close_call.1"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_fight", timeoutTicks = 600)
    public static void shepherdAFightIsLogged(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            var zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(16.5, 4, 10.5));
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> {
                        zombie.hurt(h.getLevel().damageSources().playerAttack(player), 1000);
                        skip(h);
                    })
                    .thenExecuteAfter(A_POLL, () -> assertNote(h, "fight.product"))
                    .thenSucceed();
        });
    }

    /** Beside her, with a day written and something to lose. */
    private static ShepherdEntity withADay(GameTestHelper h, ServerPlayer player) {
        var she = awake(h, player);
        data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
        data(h).affinity(SCORE);
        ShepherdReactions.writeDay(player);
        return she;
    }

    private static void act(ServerPlayer player, Action action) {
        ShepherdTalk.action(player, ShepherdTalk.liveId(), action);
    }

    private static void assertScore(GameTestHelper h, int score) {
        h.assertTrue(data(h).affinity() == score, "Expected " + score + ", is " + data(h).affinity());
    }

    private static void assertNode(GameTestHelper h, String node) {
        h.assertTrue(java.util.Objects.equals(node, ShepherdTalk.liveNode()), "Expected " + node + ", on " + ShepherdTalk.liveNode());
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_read_once", timeoutTicks = 200)
    public static void shepherdTheFirstReadCostsOnce(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = withADay(h, player);
            ShepherdTalk.openHub(player, she);
            act(player, Action.LOG);
            assertScore(h, SCORE - COST);
            act(player, Action.LOG);
            assertScore(h, SCORE - COST);
            act(player, Action.LOG_DONE);
            assertNode(h, READ);
            ShepherdTalk.reply(player, ShepherdTalk.liveId(), ShepherdConversation.CONTINUE);
            assertNode(h, null);
            h.assertTrue(ShepherdTalk.live(), "Back on the hub");
            act(player, Action.LOG);
            act(player, Action.LOG_DONE);
            assertNode(h, null);
            assertScore(h, SCORE - COST);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_read_second", timeoutTicks = 200)
    public static void shepherdASecondPlayerPaysToo(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var other = player(h);
            var she = withADay(h, player);
            ShepherdTalk.openHub(player, she);
            act(player, Action.LOG);
            ShepherdTalk.close(player);
            ShepherdTalk.openHub(other, she);
            assertNode(h, null);
            act(other, Action.LOG);
            assertScore(h, SCORE - 2 * COST);
            act(other, Action.LOG_DONE);
            assertNode(h, READ);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_read_empty", timeoutTicks = 200)
    public static void shepherdAnEmptyLogCostsNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
            data(h).affinity(SCORE);
            ShepherdTalk.openHub(player, she);
            act(player, Action.LOG);
            act(player, Action.LOG_DONE);
            assertScore(h, SCORE);
            assertNode(h, null);
            h.assertTrue(!MillerPattern.hasRead(h.getLevel().getServer(), player.getUUID()), "There was nothing to read");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_read_leaving", timeoutTicks = 200)
    public static void shepherdLeavingTheLogPlaysHerLines(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = withADay(h, player);
            ShepherdTalk.openHub(player, she);
            act(player, Action.LOG);
            assertNode(h, null);
            act(player, Action.LOG_DONE);
            h.assertTrue(ShepherdTalk.liveLines().size() == 2, "Both lines: " + ShepherdTalk.liveLines());
            h.assertTrue(ShepherdTalk.liveSent().lines().getFirst().mood() == ShepherdFace.Mood.UNIMPRESSED, "She knows");
            h.assertTrue(ShepherdTalk.liveSent().lines().getLast().mood() == ShepherdFace.Mood.THINKING, "She has decided");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_read_closed", timeoutTicks = 200)
    public static void shepherdAClosedReadPlaysAtTheNextTalk(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = withADay(h, player);
            ShepherdTalk.openHub(player, she);
            act(player, Action.LOG);
            ShepherdTalk.close(player);
            ShepherdTalk.openHub(player, she);
            assertNode(h, READ);
            ShepherdTalk.reply(player, ShepherdTalk.liveId(), ShepherdConversation.CONTINUE);
            h.assertTrue(ShepherdTalk.live(), "Then the hub, as the mockup draws it");
            ShepherdTalk.close(player);
            ShepherdTalk.openHub(player, she);
            assertNode(h, null);
            assertScore(h, SCORE - COST);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_read_wary", timeoutTicks = 200)
    public static void shepherdAtWaryTheReadCostsAndSheSaysNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = withADay(h, player);
            data(h).affinity(-300);
            ShepherdTalk.openHub(player, she);
            act(player, Action.LOG);
            assertScore(h, -300 - COST);
            act(player, Action.LOG_DONE);
            assertNode(h, null);
            data(h).affinity(SCORE);
            ShepherdTalk.close(player);
            ShepherdTalk.openHub(player, she);
            assertNode(h, null);
            h.assertTrue(MillerPattern.toldOff(h.getLevel().getServer(), player.getUUID()), "Dropped, and marked as said");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_read_blind", timeoutTicks = 200)
    public static void shepherdTheFirstReadCostsWhateverSheCanSee(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = withADay(h, player);
            ShepherdTalk.openHub(player, she);
            she.goOffline();
            act(player, Action.LOG);
            assertScore(h, SCORE);
            h.assertTrue(!MillerPattern.hasRead(h.getLevel().getServer(), player.getUUID()), "A read she could not charge is not marked paid");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_read_reload", timeoutTicks = 200)
    public static void shepherdTheReadSurvivesAReload(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = withADay(h, player);
            ShepherdTalk.openHub(player, she);
            act(player, Action.LOG);
            ShepherdTalk.close(player);
            var registries = h.getLevel().registryAccess();
            var saved = PatternSavedData.get(h.getLevel().getServer()).save(new CompoundTag(), registries);
            var again = PatternSavedData.load(saved, registries).save(new CompoundTag(), registries);
            h.assertTrue(again.equals(saved), "A reload changes nothing");
            h.assertTrue(again.getCompound("impressions").getList("read", Tag.TAG_INT_ARRAY).size() == 1, "They have read it");
            h.assertTrue(again.getCompound("impressions").getList("told", Tag.TAG_INT_ARRAY).isEmpty(), "And are still owed her lines");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_screen_log", timeoutTicks = 200)
    public static void shepherdTheLogReachesTheScreen(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var server = h.getLevel().getServer();
            var player = player(h);
            var she = withADay(h, player);
            MillerPattern.feel(server, com.frozendawn.miller.Feelings.Feeling.GUILT);
            MillerPattern.lean(server, player.getUUID());
            ShepherdReactions.writeDay(player);
            var cracked = ShepherdReactions.writeDay(player);
            ShepherdTalk.openHub(player, she);
            act(player, Action.LOG);
            var sent = ShepherdTalk.liveLog();
            h.assertTrue(sent != null && sent.conversation() == ShepherdTalk.liveId(), "It was sent");
            var pages = sent.pages();
            h.assertTrue(pages.size() == 3, "Every entry at once: " + pages.size());
            h.assertTrue(pages.getFirst().day() == cracked.day() && pages.getFirst().note().equals(cracked.note()), "Newest first");
            h.assertTrue(pages.getFirst().signed() && pages.getFirst().cracked(), "Signed and cracked");
            h.assertTrue(pages.get(1).signed() && pages.get(1).note() == null, "The first signed entry's Notes stay blank");
            h.assertTrue(!pages.getLast().signed() && !pages.getLast().cracked(), "An earlier day stays as it was");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_screen_language", timeoutTicks = 200)
    public static void shepherdLanguageStillSwaps(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = withADay(h, player);
            boolean heavy = data(h).heavy();
            ShepherdTalk.openHub(player, she);
            act(player, Action.LANGUAGE);
            h.assertTrue(data(h).heavy() != heavy, "It changed");
            act(player, Action.LANGUAGE);
            h.assertTrue(data(h).heavy() == heavy, "And back");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_screen_evidence", timeoutTicks = 200)
    public static void shepherdSettingsAreNotEvidence(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var server = h.getLevel().getServer();
            var player = player(h);
            var she = withADay(h, player);
            ShepherdTalk.openHub(player, she);
            act(player, Action.LANGUAGE);
            act(player, Action.LOG);
            act(player, Action.LOG_DONE);
            ShepherdTalk.close(player);
            MillerPattern.skipDay(server);
            MillerPattern.poll(server, 1, player.getUUID(), data(h).lastTalk(), data(h).tier());
            for (Theory theory : Theory.values()) h.assertTrue(MillerPattern.confidence(server, player.getUUID(), theory) == 0, "No theory moved: " + theory);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_screen_talk", timeoutTicks = 200)
    public static void shepherdNoLogDuringATalk(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = withADay(h, player);
            ShepherdTalk.openHub(player, she);
            act(player, Action.TALK);
            h.assertTrue(ShepherdTalk.liveNode() != null, "She is talking");
            act(player, Action.LOG);
            h.assertTrue(ShepherdTalk.liveLog() == null, "Nothing was sent");
            assertScore(h, SCORE);
            h.succeed();
        });
    }
}
