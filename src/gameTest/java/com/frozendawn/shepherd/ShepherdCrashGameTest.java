package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModEntities;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.shepherd.ShepherdTalk.Action;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The word, the stop and the way back, reached the way the game reaches them. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdCrashGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);
    private static final int ASK = 0, KIND = 1, CONTINUE = -1;
    private static final int WARY = -300, NEUTRAL = 0, FRIENDLY = 300;
    private static final String WORD = "continuity", SCENE = "t2.others", FIRST = "shepherd.crash.first", AGAIN = "shepherd.crash.again";
    private static final int SOON = 3;

    @BeforeBatch(batch = "shepherd_crash_down") public static void downBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_down") public static void downAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_think") public static void thinkBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_think") public static void thinkAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_survives") public static void survivesBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_survives") public static void survivesAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_cut") public static void cutBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_cut") public static void cutAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_returns") public static void returnsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_returns") public static void returnsAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_height") public static void heightBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_height") public static void heightAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_first") public static void firstBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_first") public static void firstAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_later") public static void laterBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_later") public static void laterAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_t4") public static void t4Before(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_t4") public static void t4After(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_wary") public static void waryBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_wary") public static void waryAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_walk") public static void walkBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_walk") public static void walkAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_reload") public static void reloadBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_reload") public static void reloadAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_scene") public static void sceneBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_scene") public static void sceneAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_nobody") public static void nobodyBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_nobody") public static void nobodyAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_costs") public static void costsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_costs") public static void costsAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_buttons") public static void buttonsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_buttons") public static void buttonsAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    private static ServerPlayer player(GameTestHelper h) {
        return ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
    }

    private static ShepherdEntity awake(GameTestHelper h, ServerPlayer player, int score) {
        data(h).stage(Stage.AWAKE);
        data(h).affinity(score);
        MillerPattern.setCoherence(h.getLevel().getServer(), 1);
        return ShepherdEntity.spawnFrom(h.getLevel(), h.absolutePos(SPOT), player.getUUID());
    }

    private static void open(ShepherdEntity she, ServerPlayer player) {
        she.interact(player, InteractionHand.MAIN_HAND);
    }

    private static void type(ShepherdEntity she, ServerPlayer player, String text) {
        she.thinkFor(0);
        ShepherdTalk.typed(player, ShepherdTalk.liveId(), text);
    }

    private static void say(ServerPlayer player, int... slots) {
        for (int slot : slots) ShepherdTalk.reply(player, ShepherdTalk.liveId(), slot);
    }

    /** Down, and past the stop in a few ticks: eight seconds is too long to wait on a gate. */
    private static void crashAndWait(GameTestHelper h, ShepherdEntity she, ServerPlayer player, Runnable then) {
        type(she, player, WORD);
        she.rebootIn(1);
        h.runAfterDelay(SOON, then);
    }

    private static void assertHubSays(GameTestHelper h, String... keys) {
        h.assertTrue(ShepherdTalk.live() && ShepherdTalk.liveNode() == null, "Over the hub: " + ShepherdTalk.liveNode());
        var said = ShepherdTalk.liveHub().lines().stream().map(line -> line.key()).toList();
        h.assertTrue(said.equals(List.of(keys)), "Says " + List.of(keys) + ": " + said);
    }

    @BeforeBatch(batch = "shepherd_crash_verdict") public static void verdictBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_verdict") public static void verdictAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_crash_verdict_open") public static void verdictOpenBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_crash_verdict_open") public static void verdictOpenAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static final int STILL_STARTS = ShepherdPolicy.CRASH_TICKS + ShepherdPolicy.CRASH_STILL_TICKS + 1;

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_verdict", timeoutTicks = 400)
    public static void shepherdTheStripGetsTheLineWithNobodyWatching(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            open(she, player);
            type(she, player, WORD);
            ShepherdTalk.close(player);
            ShepherdReactions.clearForTests();
            she.rebootIn(STILL_STARTS);
            h.runAfterDelay(SOON, () -> {
                h.assertTrue(ShepherdGlitch.VERDICT.equals(ShepherdReactions.said()), "On the strip: " + ShepherdReactions.said());
                ShepherdReactions.clearForTests();
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_verdict_open", timeoutTicks = 400)
    public static void shepherdTheScreenKeepsTheLineToItself(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            open(she, player);
            type(she, player, WORD);
            ShepherdReactions.clearForTests();
            she.rebootIn(STILL_STARTS);
            h.runAfterDelay(SOON, () -> {
                h.assertTrue(ShepherdReactions.said() == null, "Not on the strip too: " + ShepherdReactions.said());
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_down", timeoutTicks = 400)
    public static void shepherdTheWordTakesHerDown(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            open(she, player);
            type(she, player, "tell me about C-O-N-T-I-N-U-I-T-Y");
            h.assertTrue(she.crashed() && she.offline(), "Down");
            h.assertTrue(she.rebootTicks() == ShepherdPolicy.CRASH_DOWN_TICKS, "The noise, the stillness and eight seconds: " + she.rebootTicks());
            h.assertFalse(she.logo(), "No logo yet");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_think", timeoutTicks = 400)
    public static void shepherdTheWordSkipsTheThink(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, ShepherdAffinity.SILENT_BELOW - 1);
            open(she, player);
            type(she, player, WORD);
            h.assertTrue(she.crashed() && !she.thinking(), "She does not hear it, even where she will not talk");
            she.rebootIn(1);
            h.runAfterDelay(SOON, () -> {
                h.assertFalse(she.offline(), "Back");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_survives", timeoutTicks = 400)
    public static void shepherdTheTalkSurvivesTheCrash(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            open(she, player);
            int id = ShepherdTalk.liveId();
            type(she, player, WORD);
            h.runAfterDelay(20, () -> {
                h.assertTrue(ShepherdTalk.live() && ShepherdTalk.liveId() == id, "The screen stays");
                ShepherdTalk.action(player, id, Action.LAMP);
                ShepherdTalk.typed(player, id, "what is orsa");
                h.assertTrue(ShepherdTalk.liveNode() == null, "And nothing reaches her: " + ShepherdTalk.liveNode());
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_cut", timeoutTicks = 400)
    public static void shepherdLampAndHeatCutOut(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            data(h).lamp(true);
            var lit = new HashMap<BlockPos, Integer>();
            ShepherdLamp.collect(h.getLevel(), lit);
            h.assertFalse(lit.isEmpty(), "Lit");
            h.assertTrue(ShepherdWarmth.of(player) > 0, "Warm");
            open(she, player);
            type(she, player, WORD);
            lit.clear();
            ShepherdLamp.collect(h.getLevel(), lit);
            h.assertTrue(lit.isEmpty(), "The lamp is out");
            h.assertTrue(ShepherdWarmth.of(player) == 0, "And the heater");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_returns", timeoutTicks = 400)
    public static void shepherdLampAndHeatReturnAtTheLogo(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            data(h).lamp(true);
            open(she, player);
            crashAndWait(h, she, player, () -> {
                h.assertTrue(she.logo() && she.crashed(), "The logo plays");
                var lit = new HashMap<BlockPos, Integer>();
                ShepherdLamp.collect(h.getLevel(), lit);
                h.assertFalse(lit.isEmpty(), "Lamp back");
                h.assertTrue(ShepherdWarmth.of(player) > 0, "Heat back");
                h.assertTrue(ShepherdTalk.liveHub().logoTicks() > 0, "Her line waits for it");
                h.runAfterDelay(ShepherdFace.LOGO_TICKS + 2, () -> {
                    h.assertFalse(she.logo() || she.crashed(), "Over");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_height", timeoutTicks = 400)
    public static void shepherdTheCrashKeepsHerHealthAndHeight(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            she.setHealth(20);
            open(she, player);
            type(she, player, WORD);
            double y = she.getY();
            h.runAfterDelay(20, () -> {
                h.assertTrue(she.getY() == y && she.isNoGravity(), "The drop is drawn, not moved: " + (she.getY() - y));
                h.assertTrue(she.getHealth() == 20, "Her health is hers: " + she.getHealth());
                she.rebootIn(1);
                h.runAfterDelay(SOON, () -> {
                    h.assertTrue(she.getHealth() == 20 && she.isNoGravity(), "She comes back as she was");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_first", timeoutTicks = 400)
    public static void shepherdTheFirstCrashLine(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            open(she, player);
            crashAndWait(h, she, player, () -> {
                assertHubSays(h, FIRST);
                h.assertTrue(data(h).flag("crash_seen"), "She only knows they cut out");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_later", timeoutTicks = 400)
    public static void shepherdLaterCrashesAreAgain(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            data(h).flag("crash_seen", true);
            open(she, player);
            crashAndWait(h, she, player, () -> {
                assertHubSays(h, AGAIN);
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_t4", timeoutTicks = 400)
    public static void shepherdAfterT4HerClockJumped(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            data(h).flag("crash_seen", true);
            MillerPattern.heard(h.getLevel().getServer(), player.getUUID(), new com.frozendawn.miller.Heard.Reply(null, List.of(), "t4_off"));
            open(she, player);
            crashAndWait(h, she, player, () -> {
                assertHubSays(h, "shepherd.crash.after.1", "shepherd.crash.after.2");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_wary", timeoutTicks = 400)
    public static void shepherdAtWaryTheCrashEndsOnARecording(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, WARY);
            open(she, player);
            crashAndWait(h, she, player, () -> {
                assertHubSays(h, "shepherd.crash.wary");
                h.assertFalse(data(h).flag("crash_seen"), "Nobody in a recording to have noticed");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_walk", timeoutTicks = 400)
    public static void shepherdWalkingOffStillGetsHerLine(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            open(she, player);
            type(she, player, WORD);
            ShepherdTalk.close(player);
            h.assertTrue(she.crashed(), "The crash carries on with nobody watching");
            she.rebootIn(1);
            h.runAfterDelay(SOON, () -> {
                h.assertTrue(FIRST.equals(ShepherdReactions.said()), "On the strip: " + ShepherdReactions.said());
                h.assertFalse(ShepherdTalk.live(), "The talk does not reopen");
                h.assertTrue(data(h).flag("crash_seen"), "She said it");
                h.assertFalse(she.offline(), "Whole");
                ShepherdReactions.clearForTests();
                h.runAfterDelay(ShepherdFace.LOGO_TICKS + 2, () -> {
                    h.assertTrue(ShepherdReactions.said() == null, "Once: " + ShepherdReactions.said());
                    h.assertFalse(she.crashed(), "And done");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_reload", timeoutTicks = 400)
    public static void shepherdTheCrashSurvivesAReload(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            open(she, player);
            type(she, player, WORD);
            she.rebootIn(40);
            var tag = she.saveWithoutId(new CompoundTag());
            h.assertTrue(tag.getBoolean("Crash"), "Saved");
            ShepherdTalk.clearForTests();
            she.discard();
            var plain = tag.copy();
            plain.remove("Crash");
            var older = ModEntities.CS1_UNIT.get().create(h.getLevel());
            older.load(plain);
            h.assertFalse(older.crashed(), "A save with no tag reads false");
            var again = ModEntities.CS1_UNIT.get().create(h.getLevel());
            again.load(tag);
            h.assertTrue(again.crashed() && again.offline() && again.rebootTicks() == 40, "She stays down for what is left: " + again.rebootTicks());
            again.setPos(she.position());
            h.getLevel().addFreshEntity(again);
            again.rebootIn(1);
            h.runAfterDelay(SOON, () -> {
                h.assertTrue(again.logo() && !again.offline(), "Up with the logo");
                h.assertTrue(FIRST.equals(ShepherdReactions.said()), "And the strip: " + ShepherdReactions.said());
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_scene", timeoutTicks = 400)
    public static void shepherdACrashInASceneGoesBack(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            data(h).promptNode(SCENE);
            open(she, player);
            type(she, player, "what is orsa");
            crashAndWait(h, she, player, () -> {
                h.assertTrue("crash.first".equals(ShepherdTalk.liveNode()), "Her line as a node: " + ShepherdTalk.liveNode());
                h.assertTrue(ShepherdTalk.liveSent().logoTicks() > 0, "After the logo");
                h.assertTrue(SCENE.equals(data(h).promptNode()), "The prompt is unspent");
                say(player, CONTINUE);
                h.assertTrue(SCENE.equals(ShepherdTalk.liveNode()), "Clicking on goes back: " + ShepherdTalk.liveNode());
                say(player, KIND);
                h.assertTrue(data(h).promptNode() == null, "An answer spends it");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_nobody", timeoutTicks = 400)
    public static void shepherdNobodyElseCanOpenHerDuringIt(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var other = player(h);
            var she = awake(h, player, NEUTRAL);
            open(she, player);
            type(she, player, WORD);
            open(she, other);
            h.assertTrue(ShepherdTalk.speakingTo(player), "Still theirs");
            ShepherdTalk.close(player);
            open(she, other);
            h.assertFalse(ShepherdTalk.live(), "And nobody opens her while she is down");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_costs", timeoutTicks = 400)
    public static void shepherdTheCrashCostsNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            var server = h.getLevel().getServer();
            open(she, player);
            var before = MillerPattern.snapshot(server);
            int score = data(h).affinity();
            crashAndWait(h, she, player, () -> {
                var after = MillerPattern.snapshot(server);
                h.assertTrue(data(h).affinity() == score, "No score: " + data(h).affinity());
                h.assertTrue(after.history().equals(before.history()) && after.impressions().equals(before.impressions()) && after.keepsakes().equals(before.keepsakes()), "No appraisal, theory or keepsake");
                h.assertTrue(after.stage() == before.stage() && after.walls().equals(before.walls()) && java.util.Objects.equals(after.lastNote(), before.lastNote()), "No stage, wall or note");
                h.assertTrue(Math.abs(after.coherence() - before.coherence()) < 0.001, "No coherence: " + before.coherence() + " to " + after.coherence());
                h.assertTrue(MillerPattern.log(server).isEmpty(), "Nothing in her log");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_crash_buttons", timeoutTicks = 400)
    public static void shepherdButtonsNeverCrashHer(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            open(she, player);
            ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.TALK);
            say(player, ASK, ASK, CONTINUE, ASK, CONTINUE);
            h.assertFalse(she.crashed(), "Only typed text can");
            h.succeed();
        });
    }
}
