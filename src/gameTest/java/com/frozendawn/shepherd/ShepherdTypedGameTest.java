package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.miller.ImpressionStore.Theory;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.network.ShepherdNodePayload;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.shepherd.ShepherdTalk.Action;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/** A typed line, heard the way the server hears it. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdTypedGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);
    private static final int ASK = 0, KIND = 1, SARCASTIC = 3, CONTINUE = -1;
    private static final int WARY = -300, NEUTRAL = 0, FRIENDLY = 300;
    private static final String SCENE = "t2.others", GIBBERISH = "asdfghjkl", ORSA_LINE = "what is orsa", BOTH = "orsa and the evacuation";
    private static final String ORSA = "shepherd.orsa.what_is_orsa.label", HAPPENED = "shepherd.orsa.what_happened.label", EXODUS = "shepherd.orsa.exodus.label",
            OUT_THERE = "shepherd.orsa.anyone_out_there.label", KEVIN = "shepherd.orsa.who_is_kevin.label", NEITHER = "shepherd.typed.neither";
    private static final String MISS = "shepherd.typed.miss.";

    @BeforeBatch(batch = "shepherd_typed_hubline") public static void hublineBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_hubline") public static void hublineAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_strong") public static void strongBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_strong") public static void strongAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_close") public static void closeBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_close") public static void closeAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_neither") public static void neitherBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_neither") public static void neitherAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_none") public static void noneBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_none") public static void noneAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_locked") public static void lockedBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_locked") public static void lockedAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_guarded") public static void guardedBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_guarded") public static void guardedAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_always") public static void alwaysBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_always") public static void alwaysAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_recording") public static void recordingBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_recording") public static void recordingAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_wary") public static void waryBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_wary") public static void waryAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_silent") public static void silentBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_silent") public static void silentAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_quiet") public static void quietBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_quiet") public static void quietAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_once") public static void onceBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_once") public static void onceAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_back") public static void backBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_back") public static void backAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_keeps") public static void keepsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_keeps") public static void keepsAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_miss") public static void missBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_miss") public static void missAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_boot") public static void bootBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_boot") public static void bootAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_bad") public static void badBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_bad") public static void badAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_typed_log") public static void logBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_typed_log") public static void logAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

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

    /** Past her think, as a player is once the bar is live again. */
    private static void type(ShepherdEntity she, ServerPlayer player, String text) {
        she.thinkFor(0);
        ShepherdTalk.typed(player, ShepherdTalk.liveId(), text);
    }

    private static void say(ServerPlayer player, int... slots) {
        for (int slot : slots) ShepherdTalk.reply(player, ShepherdTalk.liveId(), slot);
    }

    private static List<String> labels() {
        return ShepherdTalk.liveSent().replies().stream().map(ShepherdNodePayload.Reply::label).toList();
    }

    private static void assertAt(GameTestHelper h, String node) {
        h.assertTrue(node.equals(ShepherdTalk.liveNode()), "At " + node + ": " + ShepherdTalk.liveNode());
    }

    private static void assertLines(GameTestHelper h, String... keys) {
        h.assertTrue(ShepherdTalk.liveLines().equals(List.of(keys)), "Says " + List.of(keys) + ": " + ShepherdTalk.liveLines());
    }

    private static void assertHub(GameTestHelper h) {
        h.assertTrue(ShepherdTalk.live() && ShepherdTalk.liveNode() == null, "On the hub: " + ShepherdTalk.liveNode());
    }

    private static void aDayPasses(GameTestHelper h) {
        var server = h.getLevel().getServer();
        MillerPattern.skipDay(server);
        MillerPattern.poll(server, 1, new java.util.UUID(0, 1), data(h).lastTalk(), ShepherdAffinity.Tier.NEUTRAL);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_strong", timeoutTicks = 400)
    public static void shepherdStrongOpensTheNode(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            open(she, player);
            type(she, player, "who is kevin");
            assertAt(h, "orsa.who_is_kevin");
            h.assertTrue(data(h).flag("orsa_heard_who_is_kevin"), "The same as the button");
            say(player, CONTINUE);
            assertHub(h);
            type(she, player, "hello");
            assertAt(h, "chat.hello");
            assertLines(h, "shepherd.chat.hello.warm");
            say(player, CONTINUE);
            assertHub(h);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_close", timeoutTicks = 400)
    public static void shepherdCloseOffersTwoGuesses(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            open(she, player);
            type(she, player, BOTH);
            assertAt(h, "typed.close");
            assertLines(h, "shepherd.orsa.exodus.guess");
            h.assertTrue(labels().equals(List.of(EXODUS, ORSA, NEITHER)), "Two guesses and a way out: " + labels());
            var sent = ShepherdTalk.liveSent();
            h.assertTrue(sent.replies().get(1).slot() == KIND && sent.replies().get(2).slot() == SARCASTIC && sent.plain(), "In their slots, with no tone");
            say(player, ASK);
            assertAt(h, "orsa.exodus");
            say(player, CONTINUE);
            assertHub(h);
            type(she, player, "wat hapened to kevin");
            h.assertTrue(labels().equals(List.of(KEVIN, NEITHER)), "One guess: " + labels());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_neither", timeoutTicks = 400)
    public static void shepherdNeitherIsAMiss(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            open(she, player);
            type(she, player, BOTH);
            say(player, SARCASTIC);
            assertAt(h, "typed.none");
            assertLines(h, MISS + 1);
            h.assertTrue(labels().equals(List.of(ORSA, HAPPENED, EXODUS, OUT_THERE)), "What is on her mind: " + labels());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_none", timeoutTicks = 400)
    public static void shepherdNoneShowsTopics(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            data(h).flag("orsa_heard_what_is_orsa", true);
            open(she, player);
            type(she, player, GIBBERISH);
            assertAt(h, "typed.none");
            h.assertTrue(labels().equals(List.of(HAPPENED, EXODUS, OUT_THERE, ORSA)), "Four, unheard first: " + labels());
            h.assertTrue(ShepherdTalk.liveSent().plain(), "No tone on them");
            say(player, ASK);
            assertAt(h, "orsa.what_happened");
            say(player, CONTINUE);
            assertHub(h);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_locked", timeoutTicks = 400)
    public static void shepherdALockedTopicIsAnOrdinaryMiss(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            open(she, player);
            type(she, player, "who is kevin");
            assertAt(h, "typed.none");
            assertLines(h, MISS + 1);
            var replies = labels();
            int wait = ShepherdTalk.liveThinkTicks();
            type(she, player, GIBBERISH);
            assertLines(h, MISS + 2);
            h.assertTrue(labels().equals(replies) && ShepherdTalk.liveThinkTicks() == wait, "The same slots and the same wait: " + labels());
            type(she, player, "did kevin really volunteer");
            assertLines(h, MISS + 3);
            h.assertTrue(labels().equals(replies) && ShepherdTalk.liveThinkTicks() == wait, "An intent with no node is no different");
            type(she, player, GIBBERISH);
            assertLines(h, MISS + 4);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_guarded", timeoutTicks = 400)
    public static void shepherdGuardedWordsMiss(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            open(she, player);
            for (String line : List.of("what is orsa game", "who is kevin game", "who is sophie", "what is tithonus", "tell me about orsa and anderson-kind")) {
                type(she, player, line);
                assertAt(h, "typed.none");
            }
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_always", timeoutTicks = 400)
    public static void shepherdTypingAlwaysThinks(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            open(she, player);
            type(she, player, "hello");
            h.assertTrue(ShepherdTalk.liveThinkTicks() == 28 && she.thinking(), "Even at full coherence: " + ShepherdTalk.liveThinkTicks());
            type(she, player, GIBBERISH);
            h.assertTrue(ShepherdTalk.liveThinkTicks() == 28, "A miss too: " + ShepherdTalk.liveThinkTicks());
            MillerPattern.setCoherence(h.getLevel().getServer(), 0.4);
            type(she, player, GIBBERISH);
            h.assertTrue(ShepherdTalk.liveThinkTicks() == 44, "Longer when she is worn: " + ShepherdTalk.liveThinkTicks());
            MillerPattern.setCoherence(h.getLevel().getServer(), 1);
            say(player, ASK);
            h.assertTrue(ShepherdTalk.liveThinkTicks() == 0, "A button is as it was: " + ShepherdTalk.liveThinkTicks());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_recording", timeoutTicks = 400)
    public static void shepherdARecordingTopicStillThinksWhenTyped(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            open(she, player);
            type(she, player, ORSA_LINE);
            assertAt(h, "orsa.what_is_orsa");
            h.assertTrue(ShepherdTalk.liveThinkTicks() == 28, "It opens on a recording, and she still worked it out: " + ShepherdTalk.liveThinkTicks());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_wary", timeoutTicks = 400)
    public static void shepherdAtWaryTypingIsARecording(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, WARY);
            open(she, player);
            type(she, player, ORSA_LINE);
            assertAt(h, "orsa.what_is_orsa");
            assertLines(h, "shepherd.orsa.what_is_orsa.1");
            h.assertTrue(ShepherdTalk.liveThinkTicks() == 0, "A recording does not think");
            for (String line : List.of(GIBBERISH, BOTH, "hello", "who is kevin")) {
                type(she, player, line);
                assertAt(h, "hub.talk");
                assertLines(h, "shepherd.hub.talk.wary");
                h.assertTrue(ShepherdTalk.liveThinkTicks() == 0, "No Orbit on " + line);
            }
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_silent", timeoutTicks = 400)
    public static void shepherdSilentTypingIsARecording(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, ShepherdAffinity.SILENT_BELOW - 1);
            open(she, player);
            for (String line : List.of(ORSA_LINE, GIBBERISH, "who is sophie")) {
                type(she, player, line);
                assertAt(h, "hub.talk.silent");
                h.assertTrue(ShepherdTalk.liveThinkTicks() == 0, "No Orbit");
            }
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_quiet", timeoutTicks = 400)
    public static void shepherdQuietTypingIsNotToday(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            ShepherdReactions.quietFor(data(h), h.getLevel().getServer().overworld().getGameTime());
            open(she, player);
            type(she, player, ORSA_LINE);
            assertAt(h, "hub.talk.quiet");
            say(player, CONTINUE);
            assertHub(h);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_once", timeoutTicks = 400)
    public static void shepherdTypingCountsOnce(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var typist = player(h);
            var presser = player(h);
            var both = player(h);
            var she = awake(h, typist, NEUTRAL);
            open(she, typist);
            for (int i = 0; i < 3; i++) type(she, typist, GIBBERISH);
            ShepherdTalk.close(typist);
            open(she, presser);
            ShepherdTalk.action(presser, ShepherdTalk.liveId(), Action.TALK);
            ShepherdTalk.close(presser);
            open(she, both);
            type(she, both, GIBBERISH);
            say(both, ASK);
            ShepherdTalk.close(both);
            open(she, both);
            ShepherdTalk.close(both);
            aDayPasses(h);
            var server = h.getLevel().getServer();
            double typed = MillerPattern.confidence(server, typist.getUUID(), Theory.USES_ME), pressed = MillerPattern.confidence(server, presser.getUUID(), Theory.USES_ME);
            h.assertTrue(pressed < 0, "A press of Talk tells her something: " + pressed);
            h.assertTrue(typed == pressed, "Three lines count as one press: " + typed + " against " + pressed);
            for (Theory theory : Theory.values()) {
                if (theory != Theory.USES_ME) h.assertTrue(MillerPattern.confidence(server, typist.getUUID(), theory) == 0, "The words count for nothing: " + theory);
            }
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_back", timeoutTicks = 400)
    public static void shepherdADetourReturnsToTheScene(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            ShepherdTalk.start(player, SCENE);
            var replies = labels();
            var lines = new ArrayList<>(ShepherdTalk.liveLines());
            type(she, player, ORSA_LINE);
            assertAt(h, "orsa.what_is_orsa");
            h.assertTrue(SCENE.equals(data(h).resumeNode()), "The scene waits: " + data(h).resumeNode());
            say(player, CONTINUE);
            assertAt(h, SCENE);
            h.assertTrue(labels().equals(replies) && ShepherdTalk.liveLines().equals(lines), "She says its line again");
            ShepherdTalk.close(player);
            data(h).resumeNode(null);
            open(she, player);
            ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.TALK);
            say(player, ASK);
            type(she, player, ORSA_LINE);
            assertAt(h, "orsa.what_is_orsa");
            say(player, CONTINUE);
            assertAt(h, "orsa.menu");
            assertLines(h, "shepherd.orsa.menu.again");
            say(player, ASK);
            type(she, player, ORSA_LINE);
            say(player, CONTINUE);
            assertHub(h);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_miss", timeoutTicks = 400)
    public static void shepherdAMissKeepsTheSceneReplies(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            ShepherdTalk.start(player, SCENE);
            var replies = labels();
            type(she, player, GIBBERISH);
            assertLines(h, MISS + 1);
            h.assertTrue(labels().equals(replies), "The scene is what is on her mind: " + labels());
            h.assertFalse(ShepherdTalk.liveSent().plain(), "They are the scene's own, tones and all");
            type(she, player, BOTH);
            say(player, SARCASTIC);
            assertLines(h, MISS + 2);
            h.assertTrue(labels().equals(replies), "Neither keeps them too: " + labels());
            say(player, KIND);
            var next = ShepherdDialogues.nodes().get(SCENE).responses().get(KIND).next();
            assertAt(h, next);
            h.assertTrue(next.equals(data(h).resumeNode()), "The scene is walking again: " + data(h).resumeNode());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_keeps", timeoutTicks = 400)
    public static void shepherdADetourKeepsTheScene(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            data(h).promptNode(SCENE);
            open(she, player);
            assertAt(h, SCENE);
            type(she, player, ORSA_LINE);
            ShepherdTalk.close(player);
            h.assertTrue(SCENE.equals(data(h).promptNode()) && SCENE.equals(data(h).resumeNode()), "Walking off leaves it waiting");
            open(she, player);
            assertAt(h, SCENE);
            type(she, player, ORSA_LINE);
            type(she, player, "what happened");
            assertAt(h, "orsa.what_happened");
            say(player, CONTINUE);
            assertAt(h, SCENE);
            h.assertTrue(SCENE.equals(data(h).promptNode()), "Typing twice spends nothing");
            type(she, player, ORSA_LINE);
            say(player, CONTINUE);
            say(player, KIND);
            h.assertTrue(data(h).promptNode() == null, "An answer does");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_boot", timeoutTicks = 400)
    public static void shepherdADetourFromTheFirstMeetingHasNoLogo(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            ShepherdTalk.start(player, "fm.boot");
            assertAt(h, "fm.boot");
            h.assertTrue(she.logoTicks() > 0, "The meeting opens on it");
            h.runAfterDelay(ShepherdFace.LOGO_TICKS + 2, () -> {
                type(she, player, "hi");
                assertAt(h, "chat.hello");
                say(player, CONTINUE);
                assertAt(h, "fm.boot");
                h.assertTrue(she.logoTicks() == 0 && ShepherdTalk.liveSent().logoTicks() == 0, "She only came back to the point");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_bad", timeoutTicks = 400)
    public static void shepherdBadTypingDoesNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var other = player(h);
            var she = awake(h, player, FRIENDLY);
            ShepherdTalk.typed(player, 1, ORSA_LINE);
            h.assertFalse(ShepherdTalk.live(), "Nobody is talking");
            open(she, player);
            int id = ShepherdTalk.liveId();
            ShepherdTalk.typed(player, id + 1, ORSA_LINE);
            ShepherdTalk.typed(other, id, ORSA_LINE);
            ShepherdTalk.typed(player, id, "   ");
            ShepherdTalk.typed(player, id, "");
            assertHub(h);
            ShepherdTalk.typed(player, id, ORSA_LINE);
            assertAt(h, "orsa.what_is_orsa");
            ShepherdTalk.typed(player, id, "what happened");
            assertAt(h, "orsa.what_is_orsa");
            type(she, player, "a".repeat(32000) + ORSA_LINE);
            assertAt(h, "typed.none");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_hubline", timeoutTicks = 400)
    public static void shepherdTheHubCanCarryALine(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            open(she, player);
            h.assertTrue(ShepherdTalk.liveHub().lines().isEmpty() && ShepherdTalk.liveHub().logoTicks() == 0, "An ordinary hub carries nothing");
            she.showLogo();
            ShepherdTalk.sayOnTheHub("hub.talk.plain");
            assertHub(h);
            var hub = ShepherdTalk.liveHub();
            h.assertTrue(hub.lines().size() == 1 && hub.lines().get(0).key().equals("shepherd.hub.talk"), "Her line over the buttons: " + hub.lines());
            h.assertTrue(hub.logoTicks() == ShepherdFace.LOGO_TICKS, "After the logo: " + hub.logoTicks());
            ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.LAMP);
            h.assertTrue(ShepherdTalk.liveHub().lines().isEmpty(), "It is said once");
            h.succeed();
        });
    }

    private static final class Listening extends AbstractAppender {
        final List<String> heard = new ArrayList<>();

        Listening() {
            super("shepherd_typed", null, null, false, Property.EMPTY_ARRAY);
            start();
        }

        @Override public void append(LogEvent event) {
            heard.add(event.getMessage().getFormattedMessage());
        }
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_typed_log", timeoutTicks = 400)
    public static void shepherdTypedTextIsNotLogged(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            var root = (Logger) LogManager.getRootLogger();
            var listening = new Listening();
            root.addAppender(listening);
            try {
                h.assertTrue(ShepherdTalk.typedListener == null, "Nothing listens in a normal build");
                open(she, player);
                for (String line : List.of("zqxj marker one", "who is kevin zqxj", "who is sophie zqxj")) type(she, player, line);
                LogManager.getLogger("shepherd_typed_test").info("zqxj control");
                h.assertTrue(listening.heard.stream().filter(line -> line.contains("zqxj")).toList().equals(List.of("zqxj control")), "Only the control: " + listening.heard);
            } finally {
                root.removeAppender(listening);
            }
            h.succeed();
        });
    }
}
