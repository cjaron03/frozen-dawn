package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.miller.PatternLaws;
import com.frozendawn.miller.SelfModel;
import com.frozendawn.shepherd.ShepherdDialogue.Line;
import com.frozendawn.shepherd.ShepherdDialogue.Node;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.shepherd.ShepherdTalk.Action;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdTalkGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);
    private static final int ASK = 0, KIND = 1, BLUNT = 2, SARCASTIC = 3, CONTINUE = -1;

    @BeforeBatch(batch = "shepherd_talk_wake") public static void wakeBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_wake") public static void wakeAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_stale") public static void staleBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_stale") public static void staleAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_slot") public static void slotBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_slot") public static void slotAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_resume") public static void resumeBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_resume") public static void resumeAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_damage") public static void damageBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_damage") public static void damageAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_walk") public static void walkBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_walk") public static void walkAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_reload") public static void reloadBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_reload") public static void reloadAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_logout") public static void logoutBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_logout") public static void logoutAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_second") public static void secondBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_second") public static void secondAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_saves") public static void savesBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_saves") public static void savesAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_settings") public static void settingsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_settings") public static void settingsAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_actions") public static void actionsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_actions") public static void actionsAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_hub") public static void hubBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_hub") public static void hubAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_offline") public static void offlineBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_offline") public static void offlineAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    @BeforeBatch(batch = "shepherd_talk_mouth") public static void mouthBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_mouth") public static void mouthAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    @BeforeBatch(batch = "shepherd_talk_logo") public static void logoBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_logo") public static void logoAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_nologo") public static void noLogoBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_nologo") public static void noLogoAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_lines") public static void linesBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_lines") public static void linesAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    @BeforeBatch(batch = "shepherd_talk_replay") public static void replayBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_replay") public static void replayAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    @BeforeBatch(batch = "shepherd_talk_badwhen") public static void badWhenBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); shipped = ShepherdDialogues.nodes(); }
    @AfterBatch(batch = "shepherd_talk_badwhen") public static void badWhenAfter(ServerLevel l) { ShepherdDialogues.load(shipped); ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_nolines") public static void noLinesBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); shipped = ShepherdDialogues.nodes(); }
    @AfterBatch(batch = "shepherd_talk_nolines") public static void noLinesAfter(ServerLevel l) { ShepherdDialogues.load(shipped); ShepherdTestWorld.clear(l); }

    @BeforeBatch(batch = "shepherd_talk_arg") public static void argBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); shipped = ShepherdDialogues.nodes(); ShepherdTestWorld.keepThePit(l); }
    @AfterBatch(batch = "shepherd_talk_arg") public static void argAfter(ServerLevel l) { ShepherdTestWorld.restoreThePit(l); ShepherdDialogues.load(shipped); ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_stage") public static void stageBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_stage") public static void stageAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_found") public static void foundBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_found") public static void foundAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_talk_woke") public static void wokeBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_talk_woke") public static void wokeAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    /** The real tree, while a test stands an edited datapack's node beside it. */
    private static Map<String, Node> shipped;

    private static void alsoLoad(Node node) {
        var nodes = new HashMap<>(shipped);
        nodes.put(node.id(), node);
        ShepherdDialogues.load(nodes);
    }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    private static ServerPlayer player(GameTestHelper h) {
        return ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
    }

    /** She is awake, past her first meeting unless a test says otherwise, and follows {@code player}. */
    private static ShepherdEntity awake(GameTestHelper h, ServerPlayer player) {
        data(h).stage(Stage.AWAKE);
        return ShepherdEntity.spawnFrom(h.getLevel(), h.absolutePos(SPOT), player.getUUID());
    }

    private static void rightClick(ShepherdEntity she, ServerPlayer player) {
        she.interact(player, InteractionHand.MAIN_HAND);
    }

    private static void say(ServerPlayer player, int... slots) {
        for (int slot : slots) ShepherdTalk.reply(player, ShepherdTalk.liveId(), slot);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_wake", timeoutTicks = 400)
    public static void shepherdWakingOpensTheFirstMeeting(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            h.setBlock(SPOT, ModBlocks.CS1_DOCK.get());
            var dock = h.absolutePos(SPOT);
            var player = player(h);
            player.setItemInHand(InteractionHand.MAIN_HAND, PowerCellItem.charged(1000));
            ShepherdTestWorld.boot(h, dock, player);
            h.succeedWhen(() -> h.assertTrue("fm.boot".equals(ShepherdTalk.liveNode()), "On fm.boot: " + ShepherdTalk.liveNode()));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_stale", timeoutTicks = 400)
    public static void shepherdAReplyForAnotherConversationIsIgnored(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            ShepherdTalk.start(player, "fm.boot");
            ShepherdTalk.reply(player, ShepherdTalk.liveId() + 1, KIND);
            h.assertTrue("fm.boot".equals(ShepherdTalk.liveNode()), "A stale id moves nothing");
            h.assertTrue(data(h).affinity() == 0, "And scores nothing");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_slot", timeoutTicks = 400)
    public static void shepherdASlotNotOfferedIsIgnored(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var other = player(h);
            awake(h, player);
            ShepherdTalk.start(player, "fm.filter");
            say(player, BLUNT);
            say(other, KIND);
            h.assertTrue("fm.filter".equals(ShepherdTalk.liveNode()), "Neither a missing slot nor another player moves it");
            h.assertTrue(!data(h).flag("profanity_mild"), "No flag was set");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_resume", timeoutTicks = 400)
    public static void shepherdEscResumesTheFirstMeeting(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            ShepherdTalk.start(player, "fm.boot");
            say(player, ASK, CONTINUE, KIND, CONTINUE);
            h.assertTrue("fm.name".equals(ShepherdTalk.liveNode()), "Reached fm.name: " + ShepherdTalk.liveNode());
            ShepherdTalk.close(player);
            h.assertTrue(ShepherdTalk.liveNode() == null, "Esc closes it");
            rightClick(she, player);
            h.assertTrue("fm.name".equals(ShepherdTalk.liveNode()), "She picks up where she stopped: " + ShepherdTalk.liveNode());
            say(player, KIND, KIND);
            h.assertTrue("fm.end".equals(ShepherdTalk.liveNode()), "On to fm.end: " + ShepherdTalk.liveNode());
            say(player, KIND, CONTINUE);
            h.assertTrue(!ShepherdTalk.live(), "The first meeting is over");
            rightClick(she, player);
            h.assertTrue(ShepherdTalk.live() && ShepherdTalk.liveNode() == null, "From now on a right-click is the hub");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_damage", timeoutTicks = 400)
    public static void shepherdDamageClosesAConversation(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            player.setGameMode(GameType.SURVIVAL);
            var she = awake(h, player);
            rightClick(she, player);
            h.assertTrue(ShepherdTalk.live(), "Talking");
            player.hurt(h.getLevel().damageSources().genericKill(), 1);
            h.succeedWhen(() -> h.assertTrue(!ShepherdTalk.live(), "Damage closes the screen"));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_walk", timeoutTicks = 400)
    public static void shepherdWalkingAwayClosesIt(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            data(h).waiting(true);
            rightClick(she, player);
            player.setPos(player.position().add(ShepherdPolicy.TALK_RANGE + 3, 0, 0));
            h.succeedWhen(() -> h.assertTrue(!ShepherdTalk.live(), "Out of range closes the screen"));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_reload", timeoutTicks = 400)
    public static void shepherdReloadEndsItCleanly(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            ShepherdTalk.start(player, "fm.boot");
            int id = ShepherdTalk.liveId();
            ShepherdDialogues.load(ShepherdDialogues.nodes());
            h.assertTrue(!ShepherdTalk.live(), "A reload ends the conversation");
            ShepherdTalk.reply(player, id, KIND);
            h.assertTrue(!ShepherdTalk.live() && data(h).affinity() == 0, "And a late reply lands nowhere");
            h.assertTrue("fm.boot".equals(data(h).resumeNode()), "The first meeting can still be resumed");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_badwhen", timeoutTicks = 400)
    public static void shepherdABadWhenFallsBackToTheHub(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            alsoLoad(new Node("test.bad", List.of(), List.of(), null, List.of(), "never", null));
            data(h).promptNode("test.bad");
            rightClick(she, player);
            h.assertTrue(ShepherdTalk.live() && ShepherdTalk.liveNode() == null, "The hub instead: " + ShepherdTalk.liveNode());
            h.assertTrue(data(h).promptNode() == null, "It is dropped: " + data(h).promptNode());
            ShepherdTalk.close(player);
            rightClick(she, player);
            h.assertTrue(ShepherdTalk.live(), "She can still be talked to");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_nolines", timeoutTicks = 400)
    public static void shepherdANodeWithNoLinesToSayDoesNotThrow(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            var said = new Line("x", null, Mood.SAD, "never");
            alsoLoad(new Node("test.silent", List.of(said), List.of(), null, List.of(), null, null));
            ShepherdTalk.start(player, "test.silent");
            h.assertTrue("test.silent".equals(ShepherdTalk.liveNode()), "It opens: " + ShepherdTalk.liveNode());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_logout", timeoutTicks = 400)
    public static void shepherdLogoutEndsIt(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            ShepherdTalk.start(player, "fm.boot");
            h.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            h.succeedWhen(() -> h.assertTrue(!ShepherdTalk.live(), "A speaker who has left ends the talk"));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_second", timeoutTicks = 200)
    public static void shepherdASecondPlayerGetsUnimpressed(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var first = player(h);
            var second = player(h);
            var she = awake(h, first);
            rightClick(she, first);
            int id = ShepherdTalk.liveId();
            rightClick(she, second);
            h.assertTrue(ShepherdTalk.liveId() == id, "Still the first conversation");
            h.assertTrue(she.mood() == Mood.UNIMPRESSED, "She gives the second player the face");
            h.runAfterDelay(30, () -> h.assertTrue(she.mood() == Mood.UNIMPRESSED, "It holds"));
            h.runAfterDelay(50, () -> {
                h.assertTrue(she.mood() != Mood.UNIMPRESSED, "And then it passes: " + she.mood());
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_saves", timeoutTicks = 400)
    public static void shepherdAffinityAndFlagsSave(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            ShepherdTalk.start(player, "fm.boot");
            say(player, KIND, CONTINUE, SARCASTIC, CONTINUE, KIND, KIND, BLUNT, CONTINUE);
            h.assertTrue(!ShepherdTalk.live(), "The path ended");
            h.assertTrue(data(h).affinity() == 20, "15 and 10, then -5: " + data(h).affinity());
            h.assertTrue(data(h).flag("profanity_heavy") && data(h).heavy(), "The filter came off");
            var talk = ShepherdDialogues.nodes().get("hub.talk.plain");
            h.assertTrue(ShepherdTalk.payload(1, talk, talk.lines(), true, 0, 0, arg -> arg).lines().getFirst().key().equals(talk.lines().getFirst().heavyKey()), "Heavy swaps the key");
            h.assertTrue(ShepherdTalk.payload(1, talk, talk.lines(), false, 0, 0, arg -> arg).lines().getFirst().key().equals(talk.lines().getFirst().key()), "Mild does not");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_settings", timeoutTicks = 400)
    public static void shepherdSettingsTogglesSwearing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            rightClick(she, player);
            ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.SETTINGS);
            h.assertTrue(data(h).heavy() && data(h).flag("profanity_heavy"), "Heavy on");
            ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.SETTINGS);
            h.assertTrue(!data(h).heavy() && data(h).flag("profanity_mild"), "And off again");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_actions", timeoutTicks = 400)
    public static void shepherdFollowAndLampActionsSave(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var other = player(h);
            var she = awake(h, player);
            rightClick(she, player);
            ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.FOLLOW);
            h.assertTrue(data(h).waiting(), "Her follower tells her to wait");
            ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.LAMP);
            h.assertTrue(!data(h).lamp(), "Lamp off");
            ShepherdTalk.close(player);
            rightClick(she, other);
            ShepherdTalk.action(other, ShepherdTalk.liveId(), Action.FOLLOW);
            h.assertTrue(!data(h).waiting() && other.getUUID().equals(data(h).follower()), "Anyone may tell her to follow");
            ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.LAMP);
            h.assertTrue(!data(h).lamp(), "Only the speaker's actions count");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_hub", timeoutTicks = 400)
    public static void shepherdTalkFromTheHubComesBackToIt(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            rightClick(she, player);
            ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.TALK);
            h.assertTrue("hub.talk.plain".equals(ShepherdTalk.liveNode()), "Talk speaks: " + ShepherdTalk.liveNode());
            say(player, CONTINUE);
            h.assertTrue(ShepherdTalk.live() && ShepherdTalk.liveNode() == null, "And returns to the hub");
            h.assertTrue(data(h).resumeNode() == null, "A hub talk leaves nothing to resume");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_offline", timeoutTicks = 400)
    public static void shepherdOfflineEndsATalkAndRefusesTheNext(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            rightClick(she, player);
            she.goOffline();
            h.runAfterDelay(2, () -> {
                h.assertTrue(!ShepherdTalk.live(), "She cannot talk while she is down");
                rightClick(she, player);
                h.assertTrue(!ShepherdTalk.live(), "Or be woken by a right-click");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_replay", timeoutTicks = 400)
    public static void shepherdReplayRestartsTheFirstMeetingFromNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            ShepherdTalk.start(player, "fm.boot");
            say(player, KIND, CONTINUE, SARCASTIC, CONTINUE, SARCASTIC, CONTINUE, KIND, KIND, CONTINUE);
            h.assertTrue(data(h).heavy() && data(h).flag("used_flashlight") && data(h).affinity() != 0, "A finished first meeting left its marks");
            rightClick(she, player);
            var server = h.getLevel().getServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "fd shepherd replay");
            h.assertTrue(!ShepherdTalk.live(), "The open hub closes");
            h.assertTrue(data(h).affinity() == 0 && !data(h).heavy(), "Score and swearing are back to the start");
            h.assertTrue(!data(h).flag("used_flashlight") && !data(h).flag("profanity_heavy"), "Her flags are cleared");
            rightClick(she, player);
            ShepherdBoot.answer(player, true);
            h.succeedWhen(() -> h.assertTrue("fm.boot".equals(ShepherdTalk.liveNode()), "The next right-click is the first meeting: " + ShepherdTalk.liveNode()));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_mouth", timeoutTicks = 400)
    public static void shepherdMouthStopsWhenTheLineOrTheTalkEnds(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            she.talkFor(2);
            h.assertTrue(she.talking(), "Her mouth moves for the line");
            h.runAfterDelay(5, () -> {
                h.assertTrue(!she.talking(), "Her mouth stops when the line runs out");
                rightClick(she, player);
                ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.TALK);
                h.assertTrue(she.talking(), "A line in a talk moves her mouth");
                say(player, CONTINUE);
                h.assertTrue(!she.talking(), "Back at the hub she has stopped");
                ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.TALK);
                player.setPos(player.position().add(ShepherdPolicy.TALK_RANGE + 3, 0, 0));
                h.succeedWhen(() -> h.assertTrue(!ShepherdTalk.live() && !she.talking(), "Ending the talk stops her mouth at once"));
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_logo", timeoutTicks = 400)
    public static void shepherdFirstMeetingOpensWithTheLogo(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            ShepherdTalk.start(player, "fm.boot");
            h.assertTrue(she.logo(), "Her display shows the logo");
            h.runAfterDelay(ShepherdFace.LOGO_TICKS + 2, () -> {
                h.assertTrue(!she.logo(), "Then her face comes up");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_nologo", timeoutTicks = 400)
    public static void shepherdOrdinaryTalkHasNoLogo(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            rightClick(she, player);
            ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.TALK);
            h.assertTrue(!she.logo(), "The logo is for her first boot");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_lines", timeoutTicks = 800)
    public static void shepherdBodyFollowsHerLinesFromRecordingToHerself(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            ShepherdTalk.start(player, "fm.boot");
            h.assertTrue(she.mood() == Mood.RECORDING, "The recording plays first: " + she.mood());
            h.succeedWhen(() -> h.assertTrue(she.mood() == Mood.UNIMPRESSED, "Then she speaks for herself: " + she.mood()));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_arg", timeoutTicks = 400)
    public static void shepherdAnArgumentReachesTheScreen(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            ShepherdTestWorld.pitAt(h.getLevel(), player.blockPosition().offset(300, 0, -400));
            alsoLoad(new Node("test.arg", List.of(new Line("shepherd.hub.talk", null, Mood.NEUTRAL, null, List.of("pit_x", "pit_z", "pit_blocks", "pit_bearing"))),
                    List.of(), null, List.of(), null, null));
            ShepherdTalk.start(player, "test.arg");
            var pit = ShepherdTestWorld.pit(h.getLevel());
            var args = ShepherdTalk.liveSent().lines().getFirst().args();
            h.assertTrue(args.equals(List.of(String.valueOf(pit.getX()), String.valueOf(pit.getZ()), "500", "north-east")), "Args: " + args);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_stage", timeoutTicks = 400)
    public static void shepherdStageConditionsReadThePattern(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            h.assertTrue(MillerPattern.stage(h.getLevel().getServer()) == SelfModel.Stage.PRODUCT, "She starts a product");
            h.assertFalse(ShepherdTalk.holds(player, "stage_noticing"), "Not noticing yet");
            h.assertFalse(ShepherdTalk.holds(player, "stage_walls"), "No walls yet");
            MillerPattern.witness(h.getLevel().getServer(), PatternLaws.LAGGED, 0);
            h.assertTrue(ShepherdTalk.holds(player, "stage_noticing"), "That stage or later");
            h.assertTrue(ShepherdTalk.holds(player, "stage_walls"), "At the walls");
            h.assertTrue(ShepherdTalk.holds(player, "tier_above_wary"), "Neutral is above Wary");
            data(h).affinity(-300);
            h.assertFalse(ShepherdTalk.holds(player, "tier_above_wary"), "Wary is not");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_found", timeoutTicks = 400)
    public static void shepherdFoundReadsTheSpeaker(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var reader = player(h);
            var other = player(h);
            awake(h, reader);
            var tag = new CompoundTag();
            tag.putString("doc_type", "terminus_log_last");
            var document = new ItemStack(ModItems.ORSA_DOCUMENT.get());
            document.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            reader.setItemInHand(InteractionHand.MAIN_HAND, document);
            h.assertFalse(ShepherdTalk.holds(reader, "found:terminus_log_last"), "Not read yet");
            document.use(h.getLevel(), reader, InteractionHand.MAIN_HAND);
            h.assertTrue(ShepherdTalk.holds(reader, "found:terminus_log_last"), "The reader holds it");
            h.assertFalse(ShepherdTalk.holds(other, "found:terminus_log_last"), "Nobody else does");
            h.assertFalse(ShepherdTalk.holds(reader, "found:no_such_document"), "A document nobody filed");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_talk_woke", timeoutTicks = 400)
    public static void shepherdWakingRecordsWhen(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            h.assertTrue(data(h).wokeAt() == ShepherdSavedData.NOT_SET, "Not set before she wakes");
            h.setBlock(SPOT, ModBlocks.CS1_DOCK.get());
            var player = player(h);
            player.setItemInHand(InteractionHand.MAIN_HAND, PowerCellItem.charged(1000));
            long now = h.getLevel().getServer().overworld().getGameTime();
            ShepherdTestWorld.boot(h, h.absolutePos(SPOT), player);
            h.assertTrue(data(h).wokeAt() == now, "Woke at " + data(h).wokeAt());
            var saved = data(h).save(new CompoundTag(), h.getLevel().registryAccess());
            h.assertTrue(ShepherdSavedData.load(saved, h.getLevel().registryAccess()).wokeAt() == now, "It reloads");
            saved.remove("wokeAt");
            h.assertTrue(ShepherdSavedData.load(saved, h.getLevel().registryAccess()).wokeAt() == ShepherdSavedData.NOT_SET, "A former save has none");
            h.succeed();
        });
    }
}
