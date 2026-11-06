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

/** Talk's menu, reached the way the game reaches it. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdTopicsGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);
    private static final int ASK = 0, KIND = 1, SARCASTIC = 3, CONTINUE = -1;
    private static final int WARY = -300, NEUTRAL = 0, FRIENDLY = 300, TRUSTED = 600;
    private static final String ASK_ORSA = "shepherd.talk.ask_orsa", NEVER_MIND = "shepherd.talk.never_mind", SOMETHING_ELSE = "shepherd.talk.something_else";
    private static final String ORSA = "shepherd.orsa.what_is_orsa.label", HAPPENED = "shepherd.orsa.what_happened.label", EXODUS = "shepherd.orsa.exodus.label",
            OUT_THERE = "shepherd.orsa.anyone_out_there.label", KEVIN = "shepherd.orsa.who_is_kevin.label";

    @BeforeBatch(batch = "shepherd_topics_offer") public static void offerBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_topics_offer") public static void offerAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_topics_unheard") public static void unheardBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_topics_unheard") public static void unheardAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_topics_pages") public static void pagesBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_topics_pages") public static void pagesAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_topics_kevin") public static void kevinBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_topics_kevin") public static void kevinAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_topics_candor") public static void candorBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_topics_candor") public static void candorAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_topics_back") public static void backBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_topics_back") public static void backAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_topics_nevermind") public static void nevermindBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_topics_nevermind") public static void nevermindAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_topics_silent") public static void silentBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_topics_silent") public static void silentAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_topics_notone") public static void notoneBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_topics_notone") public static void notoneAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    private static ServerPlayer player(GameTestHelper h) {
        return ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
    }

    private static ShepherdEntity awake(GameTestHelper h, ServerPlayer player, int score) {
        data(h).stage(Stage.AWAKE);
        data(h).affinity(score);
        return ShepherdEntity.spawnFrom(h.getLevel(), h.absolutePos(SPOT), player.getUUID());
    }

    private static void talk(ShepherdEntity she, ServerPlayer player) {
        she.interact(player, InteractionHand.MAIN_HAND);
        ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.TALK);
    }

    private static void say(ServerPlayer player, int... slots) {
        for (int slot : slots) ShepherdTalk.reply(player, ShepherdTalk.liveId(), slot);
    }

    private static List<String> labels() {
        return ShepherdTalk.liveSent().replies().stream().map(ShepherdNodePayload.Reply::label).toList();
    }

    private static int slotOf(GameTestHelper h, String label) {
        for (var reply : ShepherdTalk.liveSent().replies()) if (reply.label().equals(label)) return reply.slot();
        throw new AssertionError("Not on offer: " + label + " in " + labels());
    }

    /** Nobody on hand to settle on, so only the day's evidence moves. */
    private static void aDayPasses(GameTestHelper h) {
        var server = h.getLevel().getServer();
        MillerPattern.skipDay(server);
        MillerPattern.poll(server, 1, new java.util.UUID(0, 1), data(h).lastTalk(), ShepherdAffinity.Tier.NEUTRAL);
    }

    private static void assertTalkHasTheMenu(GameTestHelper h, ShepherdEntity she, ServerPlayer player, int score) {
        data(h).affinity(score);
        talk(she, player);
        var sent = ShepherdTalk.liveSent();
        h.assertTrue(labels().equals(List.of(ASK_ORSA, NEVER_MIND)) && sent.replies().get(0).slot() == ASK && sent.replies().get(1).slot() == SARCASTIC, "At " + score + ": " + labels());
        h.assertFalse(sent.canContinue(), "Its line no longer clicks through");
        h.assertTrue(sent.plain(), "No tone on these slots");
        ShepherdTalk.close(player);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_offer", timeoutTicks = 400)
    public static void shepherdTalkOffersTheMenu(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            for (int score : List.of(WARY, NEUTRAL, FRIENDLY, TRUSTED)) assertTalkHasTheMenu(h, she, player, score);
            data(h).affinity(NEUTRAL);
            talk(she, player);
            say(player, ASK);
            h.assertTrue("orsa.menu".equals(ShepherdTalk.liveNode()), "The menu: " + ShepherdTalk.liveNode());
            h.assertTrue(ShepherdTalk.liveLines().equals(List.of("shepherd.orsa.menu.first")), "The way in: " + ShepherdTalk.liveLines());
            h.assertTrue(ShepherdTalk.liveSent().plain(), "No tone on a menu");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_unheard", timeoutTicks = 400)
    public static void shepherdTheMenuIsUnheardFirst(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            talk(she, player);
            say(player, ASK);
            h.assertTrue(labels().equals(List.of(ORSA, HAPPENED, EXODUS, SOMETHING_ELSE)), "In their order: " + labels());
            ShepherdTalk.close(player);
            data(h).flag("orsa_heard_what_is_orsa", true);
            talk(she, player);
            say(player, ASK);
            h.assertTrue(labels().equals(List.of(HAPPENED, EXODUS, OUT_THERE, SOMETHING_ELSE)), "Unheard first: " + labels());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_pages", timeoutTicks = 400)
    public static void shepherdTheMenuPages(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            talk(she, player);
            say(player, ASK);
            h.assertTrue(labels().size() == 4 && labels().get(3).equals(SOMETHING_ELSE), "More to come: " + labels());
            say(player, SARCASTIC);
            h.assertTrue(labels().equals(List.of(OUT_THERE, KEVIN, NEVER_MIND)), "The rest: " + labels());
            h.assertTrue(ShepherdTalk.liveSent().replies().get(2).slot() == SARCASTIC, "Always the fourth slot");
            h.assertTrue(ShepherdTalk.liveSent().plain(), "Later pages have no tone either");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_kevin", timeoutTicks = 400)
    public static void shepherdKevinWaitsForFriendly(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            talk(she, player);
            say(player, ASK, SARCASTIC);
            h.assertTrue(labels().equals(List.of(OUT_THERE, NEVER_MIND)), "Not yet: " + labels());
            ShepherdTalk.close(player);
            data(h).affinity(FRIENDLY);
            talk(she, player);
            say(player, ASK, SARCASTIC);
            say(player, slotOf(h, KEVIN));
            h.assertTrue("orsa.who_is_kevin".equals(ShepherdTalk.liveNode()), "From Friendly: " + ShepherdTalk.liveNode());
            h.assertTrue(ShepherdTalk.liveLines().equals(List.of("shepherd.orsa.who_is_kevin.1", "shepherd.orsa.who_is_kevin.2")), "No T2, no channel: " + ShepherdTalk.liveLines());
            ShepherdTalk.close(player);
            data(h).flag("t2_heard", true);
            talk(she, player);
            say(player, ASK, SARCASTIC);
            say(player, slotOf(h, KEVIN));
            h.assertTrue(ShepherdTalk.liveLines().size() == 4, "T2 opens the rest: " + ShepherdTalk.liveLines());
            h.succeed();
        });
    }

    private static List<String> orsaAt(GameTestHelper h, ShepherdEntity she, ServerPlayer player, int score) {
        data(h).affinity(score);
        talk(she, player);
        say(player, ASK);
        say(player, slotOf(h, ORSA));
        var lines = new ArrayList<>(ShepherdTalk.liveLines());
        ShepherdTalk.close(player);
        data(h).flag("orsa_heard_what_is_orsa", false);
        return lines;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_candor", timeoutTicks = 400)
    public static void shepherdCandorFollowsTheTier(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            String key = "shepherd.orsa.what_is_orsa.";
            h.assertTrue(orsaAt(h, she, player, WARY).equals(List.of(key + 1)), "Wary, the recording alone");
            h.assertTrue(orsaAt(h, she, player, NEUTRAL).equals(List.of(key + 1, key + 2)), "Neutral, and the aside");
            h.assertTrue(orsaAt(h, she, player, FRIENDLY).equals(List.of(key + 1, key + 2, key + 3)), "Friendly, her opinion");
            h.assertTrue(orsaAt(h, she, player, TRUSTED).equals(List.of(key + 1, key + 2, key + 3, key + 4)), "Trusted, the rest");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_back", timeoutTicks = 400)
    public static void shepherdATopicComesBackToTheMenu(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            talk(she, player);
            say(player, ASK);
            say(player, slotOf(h, ORSA));
            h.assertTrue(data(h).flag("orsa_heard_what_is_orsa"), "Heard");
            say(player, CONTINUE);
            h.assertTrue("orsa.menu".equals(ShepherdTalk.liveNode()), "Back to the menu: " + ShepherdTalk.liveNode());
            h.assertTrue(ShepherdTalk.liveLines().equals(List.of("shepherd.orsa.menu.again")), "What else: " + ShepherdTalk.liveLines());
            h.assertTrue(labels().get(0).equals(HAPPENED), "And what she just said has moved back: " + labels());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_nevermind", timeoutTicks = 400)
    public static void shepherdNeverMindGoesToTheHub(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            talk(she, player);
            say(player, SARCASTIC);
            h.assertTrue(ShepherdTalk.live() && ShepherdTalk.liveNode() == null, "From Talk: " + ShepherdTalk.liveNode());
            ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.TALK);
            say(player, ASK, SARCASTIC);
            say(player, SARCASTIC);
            h.assertTrue(ShepherdTalk.live() && ShepherdTalk.liveNode() == null, "From the last page: " + ShepherdTalk.liveNode());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_silent", timeoutTicks = 400)
    public static void shepherdSilentHasNoMenu(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, ShepherdAffinity.SILENT_BELOW - 1);
            talk(she, player);
            h.assertTrue(ShepherdTalk.liveLines().equals(List.of("shepherd.hub.talk.silent")), "A recording: " + ShepherdTalk.liveLines());
            h.assertTrue(ShepherdTalk.liveSent().replies().isEmpty() && ShepherdTalk.liveSent().canContinue(), "And nothing to pick");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_notone", timeoutTicks = 400)
    public static void shepherdAMenuPickIsNoTone(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            var server = h.getLevel().getServer();
            talk(she, player);
            say(player, ASK);
            h.assertTrue(ShepherdTalk.liveSent().replies().get(KIND).slot() == KIND, "A topic in the Kind slot");
            say(player, KIND, CONTINUE, KIND, CONTINUE, KIND, CONTINUE);
            aDayPasses(h);
            h.assertTrue(MillerPattern.confidence(server, player.getUUID(), Theory.KIND) == 0, "Not a kind reply: " + MillerPattern.confidence(server, player.getUUID(), Theory.KIND));
            h.assertTrue(MillerPattern.confidence(server, player.getUUID(), Theory.TALKS_TO_ME) >= 0, "Nor anything said against them");
            h.succeed();
        });
    }
}
