package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.WinConditionState;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModItems;
import com.frozendawn.miller.ImpressionStore.Theory;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.network.ShepherdNodePayload;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.shepherd.ShepherdTalk.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Talk's menu, reached the way the game reaches it. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdTopicsGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);
    private static final int ASK = 0, KIND = 1, SARCASTIC = 3, CONTINUE = -1;
    private static final int WARY = -300, NEUTRAL = 0, FRIENDLY = 300, TRUSTED = 600, CLOSE = 900;
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

    @BeforeBatch(batch = "shepherd_topics_locked") public static void lockedBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_topics_locked") public static void lockedAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_topics_gates") public static void gatesBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); WinConditionState.get(l.getServer()).setConspiracyDiscovered(false); }
    @AfterBatch(batch = "shepherd_topics_gates") public static void gatesAfter(ServerLevel l) { ShepherdTestWorld.clear(l); WinConditionState.get(l.getServer()).setConspiracyDiscovered(false); }
    @BeforeBatch(batch = "shepherd_topics_newcandor") public static void newCandorBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); WinConditionState.get(l.getServer()).setConspiracyDiscovered(false); }
    @AfterBatch(batch = "shepherd_topics_newcandor") public static void newCandorAfter(ServerLevel l) { ShepherdTestWorld.clear(l); WinConditionState.get(l.getServer()).setConspiracyDiscovered(false); }
    @BeforeBatch(batch = "shepherd_topics_wary") public static void waryBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); WinConditionState.get(l.getServer()).setConspiracyDiscovered(false); }
    @AfterBatch(batch = "shepherd_topics_wary") public static void waryAfter(ServerLevel l) { ShepherdTestWorld.clear(l); WinConditionState.get(l.getServer()).setConspiracyDiscovered(false); }
    @BeforeBatch(batch = "shepherd_topics_seventh") public static void seventhBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_topics_seventh") public static void seventhAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_topics_whoareyou") public static void whoAreYouBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_topics_whoareyou") public static void whoAreYouAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_topics_detour") public static void detourBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); WinConditionState.get(l.getServer()).setConspiracyDiscovered(false); }
    @AfterBatch(batch = "shepherd_topics_detour") public static void detourAfter(ServerLevel l) { ShepherdTestWorld.clear(l); WinConditionState.get(l.getServer()).setConspiracyDiscovered(false); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    private static ServerPlayer player(GameTestHelper h) {
        return ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
    }

    static ShepherdEntity awake(GameTestHelper h, ServerPlayer player, int score) {
        data(h).stage(Stage.AWAKE);
        data(h).affinity(score);
        MillerPattern.setCoherence(h.getLevel().getServer(), 1);
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
        throw new net.minecraft.gametest.framework.GameTestAssertException("Not on offer: " + label + " in " + labels());
    }

    /** Every topic on the menu as it stands, over all its pages, leaving the talk closed. */
    static List<String> menu(ShepherdEntity she, ServerPlayer player) {
        talk(she, player);
        say(player, ASK);
        var topics = new ArrayList<String>();
        while (true) {
            var page = labels();
            topics.addAll(page.subList(0, page.size() - 1));
            if (!page.getLast().equals(SOMETHING_ELSE)) break;
            say(player, SARCASTIC);
        }
        ShepherdTalk.close(player);
        return topics;
    }

    /** Talk, then the page that has it, then it. */
    static void pick(GameTestHelper h, ShepherdEntity she, ServerPlayer player, String label) {
        talk(she, player);
        say(player, ASK);
        while (!labels().contains(label) && labels().getLast().equals(SOMETHING_ELSE)) say(player, SARCASTIC);
        say(player, slotOf(h, label));
    }

    private static void find(GameTestHelper h, ServerPlayer player, String doc) {
        var filed = h.getLevel().getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "found_" + doc));
        player.getAdvancements().award(filed, "impossible");
    }

    private static void carryThePlush(ServerPlayer player) {
        player.getInventory().add(new ItemStack(ModItems.STUFFED_PENGUIN.get()));
    }

    private static void conspiracy(GameTestHelper h, boolean found) {
        WinConditionState.get(h.getLevel().getServer()).setConspiracyDiscovered(found);
    }

    private static void findEverything(GameTestHelper h, ServerPlayer player) {
        for (Gate gate : GATES.values()) if (gate.doc != null) find(h, player, gate.doc);
        carryThePlush(player);
        conspiracy(h, true);
    }

    private static String label(String topic) {
        return "shepherd.orsa." + topic + ".label";
    }

    private static void type(ShepherdEntity she, ServerPlayer player, String text) {
        she.thinkFor(0);
        ShepherdTalk.typed(player, ShepherdTalk.liveId(), text);
    }

    /** The plan's table, Ruling 5: the score below and at its tier, and what the speaker must have brought. */
    private record Gate(int below, int at, String doc) {
        Gate(int below, int at) {
            this(below, at, null);
        }
    }

    private static final Map<String, Gate> GATES = Map.ofEntries(Map.entry("how_you_know", new Gate(NEUTRAL, FRIENDLY)), Map.entry("gift_shop", new Gate(NEUTRAL, FRIENDLY)),
            Map.entry("seventh_floor", new Gate(NEUTRAL, FRIENDLY)), Map.entry("safety_bulletin", new Gate(NEUTRAL, FRIENDLY, "orsa_bulletin")),
            Map.entry("kevin_volunteer", new Gate(FRIENDLY, TRUSTED)), Map.entry("deng", new Gate(FRIENDLY, TRUSTED, "terminus_log_maintenance")),
            Map.entry("anand", new Gate(FRIENDLY, TRUSTED, "terminus_log_medical")), Map.entry("varga", new Gate(FRIENDLY, TRUSTED, "terminus_log_records")),
            Map.entry("hale", new Gate(FRIENDLY, TRUSTED, "terminus_log_security")), Map.entry("the_book", new Gate(FRIENDLY, TRUSTED, "terminus_name_review")),
            Map.entry("recall_date", new Gate(FRIENDLY, TRUSTED, "terminus_log_last")), Map.entry("enoch", new Gate(TRUSTED, CLOSE, "terminus_log_last")),
            Map.entry("cascade_report", new Gate(WARY, NEUTRAL, "incident_report")), Map.entry("directive_7c", new Gate(WARY, NEUTRAL, "board_packet")),
            Map.entry("who_ran_vault", new Gate(WARY, NEUTRAL, "terminus_manifest")));
    /** The two that are not a find. */
    private static final List<String> SPECIAL = List.of("penguin", "session_14b");

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
            var she = awake(h, player, NEUTRAL);
            talk(she, player);
            say(player, ASK);
            h.assertTrue(labels().size() == 4 && labels().get(3).equals(SOMETHING_ELSE), "More to come: " + labels());
            say(player, SARCASTIC);
            h.assertTrue(labels().equals(List.of(OUT_THERE, NEVER_MIND)), "The rest: " + labels());
            h.assertTrue(ShepherdTalk.liveSent().replies().get(1).slot() == SARCASTIC, "Always the fourth slot");
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
            pick(h, she, player, KEVIN);
            h.assertTrue("orsa.who_is_kevin".equals(ShepherdTalk.liveNode()), "From Friendly: " + ShepherdTalk.liveNode());
            h.assertTrue(ShepherdTalk.liveLines().equals(List.of("shepherd.orsa.who_is_kevin.1", "shepherd.orsa.who_is_kevin.2")), "No T2, no channel: " + ShepherdTalk.liveLines());
            ShepherdTalk.close(player);
            data(h).flag("t2_heard", true);
            pick(h, she, player, KEVIN);
            h.assertTrue(ShepherdTalk.liveLines().size() == 4, "T2 opens the rest: " + ShepherdTalk.liveLines());
            h.succeed();
        });
    }

    private static List<String> orsaAt(GameTestHelper h, ShepherdEntity she, ServerPlayer player, int score) {
        data(h).affinity(score);
        pick(h, she, player, ORSA);
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

    private static List<String> locked() {
        var topics = new ArrayList<>(GATES.keySet());
        topics.addAll(SPECIAL);
        return topics;
    }

    /** All seventeen, shut for this speaker: whatever they type shows nothing that could tell a topic from gibberish. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_locked", timeoutTicks = 400)
    public static void shepherdEveryLockedTopicIsAMiss(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            var labels = locked().stream().map(ShepherdTopicsGameTest::label).toList();
            var nodes = locked().stream().map(topic -> "orsa." + topic).toList();
            she.interact(player, InteractionHand.MAIN_HAND);
            type(she, player, "asdfghjkl");
            var slots = labels();
            int wait = ShepherdTalk.liveThinkTicks();
            h.assertTrue("typed.none".equals(ShepherdTalk.liveNode()), "Gibberish misses: " + ShepherdTalk.liveNode());
            for (var intent : ShepherdIntents.intents()) {
                if (!locked().contains(intent.id())) continue;
                for (String example : intent.examples()) {
                    type(she, player, example);
                    h.assertFalse(nodes.contains(ShepherdTalk.liveNode()), example + " opened " + ShepherdTalk.liveNode());
                    h.assertTrue(labels().stream().noneMatch(labels::contains), example + " offered " + labels());
                }
                String exact = intent.examples().getFirst();
                type(she, player, exact);
                h.assertTrue("typed.none".equals(ShepherdTalk.liveNode()), exact + " is a miss: " + ShepherdTalk.liveNode());
                h.assertTrue(ShepherdTalk.liveLines().getFirst().startsWith("shepherd.typed.miss."), exact + ": " + ShepherdTalk.liveLines());
                h.assertTrue(labels().equals(slots), exact + " has the same slots: " + labels());
                h.assertTrue(ShepherdTalk.liveThinkTicks() == wait, exact + " takes the same wait: " + ShepherdTalk.liveThinkTicks());
            }
            h.succeed();
        });
    }

    private static void assertOpensAt(GameTestHelper h, ShepherdEntity she, ServerPlayer player, String topic, int below, int at) {
        data(h).affinity(below);
        h.assertFalse(menu(she, player).contains(label(topic)), topic + " below its tier");
        data(h).affinity(at);
        h.assertTrue(menu(she, player).contains(label(topic)), topic + " at its gate");
        pick(h, she, player, label(topic));
        h.assertTrue(("orsa." + topic).equals(ShepherdTalk.liveNode()), topic + ": " + ShepherdTalk.liveNode());
        h.assertTrue(data(h).flag("orsa_heard_" + topic), topic + " is heard");
        ShepherdTalk.close(player);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_gates", timeoutTicks = 400)
    public static void shepherdEachTopicOpensOnItsGate(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var first = player(h);
            var she = awake(h, first, NEUTRAL);
            GATES.forEach((topic, gate) -> {
                var player = player(h);
                if (gate.doc != null) {
                    data(h).affinity(gate.at);
                    h.assertFalse(menu(she, player).contains(label(topic)), topic + " before the find");
                    find(h, player, gate.doc);
                }
                assertOpensAt(h, she, player, topic, gate.below, gate.at);
            });
            var carrier = player(h);
            data(h).affinity(NEUTRAL);
            h.assertFalse(menu(she, carrier).contains(label("penguin")), "No plush");
            carryThePlush(carrier);
            assertOpensAt(h, she, carrier, "penguin", WARY, NEUTRAL);
            var reader = player(h);
            data(h).affinity(NEUTRAL);
            h.assertFalse(menu(she, reader).contains(label("session_14b")), "No conspiracy");
            conspiracy(h, true);
            assertOpensAt(h, she, reader, "session_14b", WARY, NEUTRAL);
            h.succeed();
        });
    }

    private static List<String> linesOf(GameTestHelper h, ShepherdEntity she, ServerPlayer player, String topic) {
        pick(h, she, player, label(topic));
        var lines = new ArrayList<>(ShepherdTalk.liveLines());
        ShepherdTalk.close(player);
        return lines;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_newcandor", timeoutTicks = 400)
    public static void shepherdCandorFollowsTheTierOnNewTopics(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            String seventh = "shepherd.orsa.seventh_floor.", kevin = "shepherd.orsa.kevin_volunteer.", pens = "shepherd.orsa.directive_7c.";
            h.assertTrue(linesOf(h, she, player, "seventh_floor").equals(List.of(seventh + 1, seventh + 2, seventh + 3)), "Friendly, no registry story");
            data(h).affinity(TRUSTED);
            h.assertTrue(linesOf(h, she, player, "seventh_floor").equals(List.of(seventh + 1, seventh + 2, seventh + 3, seventh + 4)), "Trusted, the rest");
            h.assertTrue(linesOf(h, she, player, "kevin_volunteer").equals(List.of(kevin + 1, kevin + 2, kevin + 3, kevin + 4)), "No conspiracy, no memo");
            find(h, player, "board_packet");
            h.assertTrue(linesOf(h, she, player, "directive_7c").equals(List.of(pens + 1)), "Just the pens");
            conspiracy(h, true);
            h.assertTrue(linesOf(h, she, player, "kevin_volunteer").equals(List.of(kevin + 1, kevin + 2, kevin + 3, kevin + 4, kevin + 5)), "The memo");
            h.assertTrue(linesOf(h, she, player, "directive_7c").equals(List.of(pens + 1, pens + 2)), "And who broke it");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_wary", timeoutTicks = 400)
    public static void shepherdNothingButARecordingAtWary(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, WARY);
            findEverything(h, player);
            h.assertTrue(menu(she, player).equals(List.of(ORSA, HAPPENED, EXODUS, OUT_THERE)), "Only what has a recording: " + menu(she, player));
            she.interact(player, InteractionHand.MAIN_HAND);
            for (var intent : ShepherdIntents.intents()) {
                if (!locked().contains(intent.id()) && !"who_are_you".equals(intent.id())) continue;
                type(she, player, intent.examples().getFirst());
                h.assertTrue("hub.talk".equals(ShepherdTalk.liveNode()), intent.id() + " is a recording: " + ShepherdTalk.liveNode());
            }
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_seventh", timeoutTicks = 400)
    public static void shepherdTheSeventhFloorChangesAfterT5(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            String key = "shepherd.orsa.seventh_floor.";
            h.assertTrue(linesOf(h, she, player, "seventh_floor").contains(key + 2), "I come from four");
            data(h).flag("t5_done", true);
            var after = linesOf(h, she, player, "seventh_floor");
            h.assertTrue(after.contains(key + "2t") && !after.contains(key + 2), "I thought I came from four: " + after);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_whoareyou", timeoutTicks = 400)
    public static void shepherdWhoAreYouIsSmallTalk(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            String key = "shepherd.chat.who_are_you";
            h.assertFalse(menu(she, player).stream().anyMatch(topic -> topic.contains("who_are_you")), "Off the menu");
            she.interact(player, InteractionHand.MAIN_HAND);
            for (String line : List.of("Who are you?", "what are you")) {
                type(she, player, line);
                h.assertTrue(ShepherdTalk.liveLines().equals(List.of(key)), line + ": " + ShepherdTalk.liveLines());
            }
            data(h).affinity(FRIENDLY);
            type(she, player, "who are you");
            h.assertTrue(ShepherdTalk.liveLines().equals(List.of(key + ".warm")), "Friendly: " + ShepherdTalk.liveLines());
            data(h).flag("t5_done", true);
            type(she, player, "who are you");
            h.assertTrue(ShepherdTalk.liveLines().equals(List.of(key + ".after")), "After T5: " + ShepherdTalk.liveLines());
            say(player, CONTINUE);
            h.assertTrue(ShepherdTalk.live() && ShepherdTalk.liveNode() == null, "Back to the hub: " + ShepherdTalk.liveNode());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_topics_detour", timeoutTicks = 400)
    public static void shepherdADetourKeepsTheSceneOnANewTopic(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, TRUSTED);
            String scene = "t2.others";
            conspiracy(h, true);
            ShepherdTalk.start(player, scene);
            h.assertTrue(scene.equals(ShepherdTalk.liveNode()), "The scene: " + ShepherdTalk.liveNode());
            type(she, player, "What was Session 14-B?");
            h.assertTrue("orsa.session_14b".equals(ShepherdTalk.liveNode()), "The long way: " + ShepherdTalk.liveNode());
            say(player, KIND);
            h.assertTrue("orsa.session_14b.r2".equals(ShepherdTalk.liveNode()), "A reply: " + ShepherdTalk.liveNode());
            say(player, CONTINUE);
            h.assertTrue("orsa.session_14b.end".equals(ShepherdTalk.liveNode()), "All four lead here: " + ShepherdTalk.liveNode());
            say(player, CONTINUE);
            h.assertTrue(scene.equals(ShepherdTalk.liveNode()), "And the scene again: " + ShepherdTalk.liveNode());
            h.succeed();
        });
    }
}
