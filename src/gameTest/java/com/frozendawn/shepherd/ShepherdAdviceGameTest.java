package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.network.ShepherdNodePayload;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.shepherd.ShepherdTalk.Action;
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

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdAdviceGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);
    private static final int ASK = 0, KIND = 1, BLUNT = 2, SARCASTIC = 3, CONTINUE = -1;
    private static final int WARY = -300, NEUTRAL = 0, FRIENDLY = 300;

    @BeforeBatch(batch = "shepherd_advice_opens") public static void opensBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_advice_opens") public static void opensAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_advice_returns") public static void returnsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_advice_returns") public static void returnsAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_advice_orsa") public static void orsaBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_advice_orsa") public static void orsaAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_advice_wary") public static void waryBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_advice_wary") public static void waryAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_advice_erased") public static void erasedBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.set(true); }
    @AfterBatch(batch = "shepherd_advice_erased") public static void erasedAfter(ServerLevel l) { FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.set(false); ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_advice_items") public static void itemsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.set(true); }
    @AfterBatch(batch = "shepherd_advice_items") public static void itemsAfter(ServerLevel l) { FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.set(false); ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_advice_again") public static void againBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_advice_again") public static void againAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_advice_help") public static void helpBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_advice_help") public static void helpAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_advice_three") public static void threeBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_advice_three") public static void threeAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    private static ServerPlayer player(GameTestHelper h) {
        return ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
    }

    private static ShepherdEntity awake(GameTestHelper h, ServerPlayer player, int affinity) {
        data(h).stage(Stage.AWAKE);
        data(h).affinity(affinity);
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

    private static void type(ShepherdEntity she, ServerPlayer player, String text) {
        she.thinkFor(0);
        ShepherdTalk.typed(player, ShepherdTalk.liveId(), text);
    }

    private static String line(String topic, String which) {
        return "shepherd.advice." + topic + "." + which;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_advice_opens", timeoutTicks = 400)
    public static void shepherdAdviceOpensFromTheHub(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            talk(she, player);
            h.assertTrue(labels().contains("shepherd.talk.ask_advice"), "On Talk: " + labels());
            say(player, KIND);
            h.assertTrue("advice.menu".equals(ShepherdTalk.liveNode()), "The menu: " + ShepherdTalk.liveNode());
            h.assertTrue(labels().getFirst().equals(line("frostbitten", "label")), "Mobs first: " + labels());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_advice_returns", timeoutTicks = 400)
    public static void shepherdAdviceReturnsToAdvice(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            talk(she, player);
            say(player, KIND, ASK);
            h.assertTrue("advice.frostbitten".equals(ShepherdTalk.liveNode()), "The topic: " + ShepherdTalk.liveNode());
            say(player, CONTINUE);
            h.assertTrue("advice.menu".equals(ShepherdTalk.liveNode()), "Back to Advice: " + ShepherdTalk.liveNode());
            h.assertTrue(ShepherdTalk.liveLines().contains("shepherd.advice.menu.again"), "Her second line: " + ShepherdTalk.liveLines());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_advice_orsa", timeoutTicks = 400)
    public static void shepherdOrsaStillReturnsToOrsa(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            talk(she, player);
            say(player, KIND, ASK, CONTINUE);
            ShepherdTalk.close(player);
            talk(she, player);
            say(player, ASK);
            h.assertTrue("orsa.menu".equals(ShepherdTalk.liveNode()), "ORSA's menu: " + ShepherdTalk.liveNode());
            h.assertTrue(labels().stream().noneMatch(label -> label.startsWith("shepherd.advice")), "Only ORSA's: " + labels());
            say(player, ASK, CONTINUE);
            h.assertTrue("orsa.menu".equals(ShepherdTalk.liveNode()), "Back to ORSA: " + ShepherdTalk.liveNode());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_advice_wary", timeoutTicks = 400)
    public static void shepherdWaryOnlyPlaysTheCounters(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, WARY);
            talk(she, player);
            say(player, KIND, ASK);
            h.assertTrue(List.of(line("frostbitten", "1")).equals(ShepherdTalk.liveLines()), "Only the recording: " + ShepherdTalk.liveLines());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_advice_erased", timeoutTicks = 400)
    public static void shepherdTheMobsLockOnceErased(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            talk(she, player);
            say(player, KIND);
            h.assertTrue(labels().stream().noneMatch(label -> List.of("frostbitten", "frostmite", "hollow", "returned").stream().anyMatch(mob -> label.equals(line(mob, "label")))), "No mobs: " + labels());
            for (String typed : List.of("frostbitten", "the returned", "hunter", "mimic", "architect")) {
                type(she, player, typed);
                h.assertTrue(ShepherdTalk.liveLines().stream().allMatch(key -> key.startsWith("shepherd.typed.miss")), typed + " reads as a miss: " + ShepherdTalk.liveLines());
            }
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_advice_items", timeoutTicks = 400)
    public static void shepherdItemsOutliveTheEnd(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            talk(she, player);
            say(player, KIND);
            h.assertTrue(labels().getFirst().equals(line("thermal_heater", "label")), "Items stay: " + labels());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_advice_again", timeoutTicks = 400)
    public static void shepherdAsksAgainDifferently(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            talk(she, player);
            say(player, KIND, ASK);
            h.assertTrue(ShepherdTalk.liveLines().getFirst().equals(line("frostbitten", "open")), "The first time: " + ShepherdTalk.liveLines());
            ShepherdTalk.close(player);
            talk(she, player);
            say(player, KIND);
            type(she, player, "frostbitten");
            h.assertTrue(ShepherdTalk.liveLines().getFirst().equals(line("frostbitten", "again")), "Asked again: " + ShepherdTalk.liveLines());
            h.assertTrue(ShepherdTalk.liveLines().contains(line("frostbitten", "1")), "The rest still plays");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_advice_help", timeoutTicks = 400)
    public static void shepherdHelpMePointsAtAdvice(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            talk(she, player);
            type(she, player, "help me");
            h.assertTrue(List.of("shepherd.chat.need_help.warm").equals(ShepherdTalk.liveLines()), "She points: " + ShepherdTalk.liveLines());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_advice_three", timeoutTicks = 400)
    public static void shepherdTheReturnedOffersItsThree(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, NEUTRAL);
            talk(she, player);
            say(player, KIND);
            h.assertTrue(labels().stream().noneMatch(label -> label.equals(line("hunter", "label"))), "The variants live on its own menu: " + labels());
            say(player, SARCASTIC);
            int returned = labels().indexOf(line("returned", "label"));
            h.assertTrue(returned >= 0, "The next page has it: " + labels());
            say(player, returned);
            h.assertTrue(List.of(line("hunter", "label"), line("mimic", "label"), line("architect", "label"), "shepherd.talk.never_mind").equals(labels()), "Three and out: " + labels());
            say(player, SARCASTIC);
            h.assertTrue("advice.menu".equals(ShepherdTalk.liveNode()), "Never mind goes back: " + ShepherdTalk.liveNode());
            h.succeed();
        });
    }
}
