package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModEntities;
import com.frozendawn.miller.Attention;
import com.frozendawn.miller.Drives.Drive;
import com.frozendawn.miller.Heard;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.miller.PatternLaws;
import com.frozendawn.miller.Refusal;
import com.frozendawn.miller.SelfModel;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdAmbient.Trigger;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.shepherd.ShepherdTalk.Action;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What she does about what is on her mind, reached the way the game reaches it. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdWillGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10), CHEST = new BlockPos(15, 4, 10);
    private static final int A_POLL = ShepherdReactions.POLL_TICKS + 10;
    private static final String QUIET_OVER = "shepherd.ambient.quiet_over.1";

    @BeforeBatch(batch = "shepherd_will_quiet") public static void quietBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_will_quiet") public static void quietAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_nine_quiet_strip") public static void nineQuietStripBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_nine_quiet_strip") public static void nineQuietStripAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_nine_quiet_once") public static void nineQuietOnceBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_nine_quiet_once") public static void nineQuietOnceAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_nine_quiet_prompts") public static void nineQuietPromptsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_nine_quiet_prompts") public static void nineQuietPromptsAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_nine_quiet_ends") public static void nineQuietEndsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_nine_quiet_ends") public static void nineQuietEndsAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_nine_quiet_skip") public static void nineQuietSkipBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_nine_quiet_skip") public static void nineQuietSkipAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_nine_quiet_talk") public static void nineQuietTalkBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_nine_quiet_talk") public static void nineQuietTalkAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_will_recall") public static void recallBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_will_recall") public static void recallAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_will_wall") public static void wallBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_will_wall") public static void wallAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_will_unfound") public static void unfoundBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_will_unfound") public static void unfoundAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_will_slow") public static void slowBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_will_slow") public static void slowAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_will_look") public static void lookBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_will_look") public static void lookAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_will_nolook") public static void noLookBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_will_nolook") public static void noLookAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_will_closer") public static void closerBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_will_closer") public static void closerAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_will_gap") public static void gapBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_will_gap") public static void gapAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    @BeforeBatch(batch = "shepherd_refuse_night") public static void refuseNightBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_refuse_night") public static void refuseNightAfter(ServerLevel l) { afterNight(l); }
    @BeforeBatch(batch = "shepherd_refuse_later") public static void refuseLaterBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_refuse_later") public static void refuseLaterAfter(ServerLevel l) { afterNight(l); }
    @BeforeBatch(batch = "shepherd_refuse_fight") public static void refuseFightBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_refuse_fight") public static void refuseFightAfter(ServerLevel l) { afterNight(l); }
    @BeforeBatch(batch = "shepherd_refuse_archivist") public static void refuseArchivistBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_refuse_archivist") public static void refuseArchivistAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_silent_talk") public static void silentTalkBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_silent_talk") public static void silentTalkAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_silent_equipment") public static void silentEquipmentBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_silent_equipment") public static void silentEquipmentAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_silent_deeds") public static void silentDeedsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_silent_deeds") public static void silentDeedsAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    @BeforeBatch(batch = "shepherd_refuse_wary") public static void refuseWaryBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_refuse_wary") public static void refuseWaryAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_refuse_catchup") public static void refuseCatchUpBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_refuse_catchup") public static void refuseCatchUpAfter(ServerLevel l) { afterNight(l); }
    @BeforeBatch(batch = "shepherd_will_spell") public static void spellBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_will_spell") public static void spellAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_slip_fight") public static void slipFightBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_slip_fight") public static void slipFightAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_slip_talk") public static void slipTalkBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_slip_talk") public static void slipTalkAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_slip_wary") public static void slipWaryBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_slip_wary") public static void slipWaryAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_slip_sorry") public static void slipSorryBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_slip_sorry") public static void slipSorryAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_slip_instead") public static void slipInsteadBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_slip_instead") public static void slipInsteadAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static void afterNight(ServerLevel level) {
        level.setDayTime(6000);
        ShepherdTestWorld.clear(level);
    }

    @BeforeBatch(batch = "shepherd_t5_sheet") public static void t5SheetBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_t5_sheet") public static void t5SheetAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_t5_quiet") public static void t5QuietBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_t5_quiet") public static void t5QuietAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_t5_wary") public static void t5WaryBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_t5_wary") public static void t5WaryAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_t5_near") public static void t5NearBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_t5_near") public static void t5NearAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_t5_enoch") public static void t5EnochBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_t5_enoch") public static void t5EnochAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_t5_no_enoch") public static void t5NoEnochBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_t5_no_enoch") public static void t5NoEnochAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_t5_someone") public static void t5SomeoneBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_t5_someone") public static void t5SomeoneAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_t5_left") public static void t5LeftBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_t5_left") public static void t5LeftAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    @BeforeBatch(batch = "shepherd_bleed_noticing") public static void bleedNoticingBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_bleed_noticing") public static void bleedNoticingAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_bleed_walls") public static void bleedWallsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_bleed_walls") public static void bleedWallsAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_bleed_after") public static void bleedAfterBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_bleed_after") public static void bleedAfterAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_fewer_once") public static void fewerOnceBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_fewer_once") public static void fewerOnceAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_fewer_echo") public static void fewerEchoBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_fewer_echo") public static void fewerEchoAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_fewer_far") public static void fewerFarBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_fewer_far") public static void fewerFarAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_recall_nine") public static void recallNineBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_recall_nine") public static void recallNineAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    private static ServerPlayer player(GameTestHelper h) {
        return ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
    }

    private static ShepherdEntity met(GameTestHelper h, ServerPlayer player) {
        data(h).stage(Stage.AWAKE);
        data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
        return ShepherdEntity.spawnFrom(h.getLevel(), h.absolutePos(SPOT), player.getUUID());
    }

    /** Nothing she wants, so what is on her mind is all that moves her. */
    private static void sated(GameTestHelper h) {
        for (Drive drive : Drive.values()) MillerPattern.want(h.getLevel().getServer(), drive, 0);
    }

    private static void assertSaid(GameTestHelper h, String key) {
        h.assertTrue(Objects.equals(key, ShepherdReactions.said()), "Said: " + ShepherdReactions.said() + ", not " + key);
    }

    /** As if the gap had run and nothing had been said. */
    private static void again(GameTestHelper h) {
        ShepherdReactions.clearForTests();
        data(h).lastAmbient(ShepherdSavedData.NOT_SET);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_will_quiet", timeoutTicks = 400)
    public static void shepherdStayingQuietMeansSilence(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            sated(h);
            data(h).lastTalk(h.getLevel().getGameTime() - ShepherdPolicy.SILENCE_TICKS);
            var keys = ShepherdAmbients.pools(Trigger.SILENCE).stream().flatMap(pool -> pool.variants().stream()).map(ShepherdAffinity.Say::key).toList();
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> assertSaid(h, null))
                    .thenExecute(() -> MillerPattern.want(h.getLevel().getServer(), Drive.CURIOSITY, 1))
                    .thenWaitUntil(() -> h.assertTrue(keys.contains(ShepherdReactions.said()), "Wanting anything lets it through: " + ShepherdReactions.said()))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_will_recall", timeoutTicks = 400)
    public static void shepherdRecallsAKeepsake(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            var server = h.getLevel().getServer();
            MillerPattern.appraise(server, player.getUUID(), Deed.WARM_SHELTER);
            MillerPattern.skipDay(server);
            sated(h);
            var before = MillerPattern.keepsakes(server, player.getUUID()).getFirst();
            h.startSequence()
                    .thenWaitUntil(() -> assertSaid(h, "shepherd.recall.warm_shelter"))
                    .thenExecute(() -> {
                        h.assertTrue(String.valueOf(before.day()).equals(ShepherdReactions.saidArg()), "The day comes with it: " + ShepherdReactions.saidArg());
                        var after = MillerPattern.keepsakes(server, player.getUUID()).getFirst();
                        h.assertTrue(after.salience() > before.salience(), "Going back to it strengthens it");
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_will_wall", timeoutTicks = 600)
    public static void shepherdSaysAWallOnce(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            sated(h);
            MillerPattern.witness(h.getLevel().getServer(), PatternLaws.CAUSAL, 3);
            h.startSequence()
                    .thenWaitUntil(() -> assertSaid(h, "shepherd.self.noticing"))
                    .thenExecute(() -> again(h))
                    .thenWaitUntil(() -> assertSaid(h, "shepherd.wall.causal"))
                    .thenExecute(() -> again(h))
                    .thenExecuteAfter(A_POLL, () -> assertSaid(h, null))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_will_unfound", timeoutTicks = 400)
    public static void shepherdNeverSaysAWallUnfound(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            sated(h);
            MillerPattern.witness(h.getLevel().getServer(), PatternLaws.CAUSAL, 2);
            h.startSequence()
                    .thenWaitUntil(() -> assertSaid(h, "shepherd.self.noticing"))
                    .thenExecute(() -> again(h))
                    .thenExecuteAfter(A_POLL, () -> assertSaid(h, null))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_will_slow", timeoutTicks = 400)
    public static void shepherdSaysSheIsSlowOnce(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            sated(h);
            MillerPattern.setCoherence(h.getLevel().getServer(), 0.6);
            var keys = ShepherdAmbients.pools(Trigger.SLOW).stream().flatMap(pool -> pool.variants().stream()).map(ShepherdAffinity.Say::key).toList();
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(keys.contains(ShepherdReactions.said()), "She says so, even staying quiet: " + ShepherdReactions.said()))
                    .thenExecute(() -> {
                        again(h);
                        data(h).deedReady("ambient." + Trigger.SLOW.name(), 0);
                    })
                    .thenExecuteAfter(A_POLL, () -> assertSaid(h, null))
                    .thenSucceed();
        });
    }

    private static ShepherdEntity beside(GameTestHelper h) {
        h.setBlock(CHEST, Blocks.CHEST);
        var she = met(h, player(h));
        she.stillFor(ShepherdPolicy.STILL_TICKS);
        return she;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_will_look", timeoutTicks = 600)
    public static void shepherdLooksWhenCurious(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = beside(h);
            MillerPattern.want(h.getLevel().getServer(), Drive.CURIOSITY, 1);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(she.lookingAt() != null, "A first look"))
                    .thenWaitUntil(() -> h.assertTrue(she.lookingAt() == null, "It ends"))
                    .thenWaitUntil(() -> h.assertTrue(she.lookingAt() != null, "Curious, she does not wait out the minute"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_will_nolook", timeoutTicks = 600)
    public static void shepherdIncuriousKeepsTheGap(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = beside(h);
            sated(h);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(she.lookingAt() != null, "A first look"))
                    .thenWaitUntil(() -> h.assertTrue(she.lookingAt() == null, "It ends"))
                    .thenExecuteAfter(2 * A_POLL, () -> h.assertTrue(she.lookingAt() == null, "The gap stands"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_will_closer", timeoutTicks = 800)
    public static void shepherdDriftsCloserWhenLonely(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = met(h, player);
            sated(h);
            var server = h.getLevel().getServer();
            MillerPattern.want(server, Drive.COMPANY, 1);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(she.hoverBlocks() == ShepherdEntity.CLOSE_BLOCKS, "She comes in: " + she.hoverBlocks()))
                    .thenWaitUntil(() -> h.assertTrue(she.distanceTo(player) <= ShepherdEntity.CLOSE_BLOCKS + 1, "And gets there: " + she.distanceTo(player)))
                    .thenExecute(() -> MillerPattern.want(server, Drive.COMPANY, 0))
                    .thenExecuteAfter(ShepherdEntity.CLOSE_TICKS + 10, () -> h.assertTrue(she.hoverBlocks() > ShepherdEntity.CLOSE_BLOCKS, "Then back to her usual spot"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_will_gap", timeoutTicks = 600)
    public static void shepherdHoldsTheGapStill(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            sated(h);
            MillerPattern.witness(h.getLevel().getServer(), PatternLaws.CAUSAL, 3);
            h.startSequence()
                    .thenWaitUntil(() -> assertSaid(h, "shepherd.self.noticing"))
                    .thenExecute(() -> {
                        ShepherdReactions.clearForTests();
                        data(h).lastAmbient(h.getLevel().getGameTime() - ShepherdPolicy.AMBIENT_GAP_TICKS + 2 * A_POLL);
                    })
                    .thenExecuteAfter(A_POLL, () -> assertSaid(h, null))
                    .thenExecute(() -> data(h).lastAmbient(h.getLevel().getGameTime() - ShepherdPolicy.AMBIENT_GAP_TICKS))
                    .thenWaitUntil(() -> assertSaid(h, "shepherd.wall.causal"))
                    .thenSucceed();
        });
    }

    /** A server of any age may be in the middle of its rain. */
    private static void clearNight(GameTestHelper h) {
        var level = h.getLevel();
        level.setDayTime(18000);
        level.setWeatherParameters(24000, 0, false, false);
        level.setRainLevel(0);
        MillerPattern.want(level.getServer(), Drive.SKY, 1);
    }

    /** She has been left out under a clear night she has wanted, and is talking to them. */
    private static ServerPlayer underTheSky(GameTestHelper h) {
        var player = player(h);
        var she = met(h, player);
        clearNight(h);
        data(h).waiting(true);
        she.interact(player, InteractionHand.MAIN_HAND);
        return player;
    }

    private static void follow(ServerPlayer player) {
        ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.FOLLOW);
    }

    private static void talk(ServerPlayer player) {
        ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.TALK);
    }

    private static void assertOn(GameTestHelper h, String node) {
        h.assertTrue(Objects.equals(node, ShepherdTalk.liveNode()), "On: " + ShepherdTalk.liveNode() + ", not " + node);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_refuse_night", timeoutTicks = 400, skyAccess = true)
    public static void shepherdRefusesOnceThenGoes(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = underTheSky(h);
            follow(player);
            assertOn(h, "hub.refuse.night");
            h.assertTrue(data(h).waiting(), "She stays where she is");
            ShepherdTalk.reply(player, ShepherdTalk.liveId(), ShepherdConversation.CONTINUE);
            assertOn(h, null);
            follow(player);
            assertOn(h, "hub.refuse.night.yield");
            h.assertTrue(!data(h).waiting() && player.getUUID().equals(data(h).follower()), "The second ask works");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_refuse_later", timeoutTicks = 800, skyAccess = true)
    public static void shepherdAskingLaterStartsOver(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = underTheSky(h);
            follow(player);
            ShepherdTalk.reply(player, ShepherdTalk.liveId(), ShepherdConversation.CONTINUE);
            ((ServerLevelData) h.getLevel().getLevelData()).setGameTime(h.getLevel().getGameTime() + Refusal.YIELD_TICKS + 1);
            follow(player);
            assertOn(h, "hub.refuse.night");
            h.assertTrue(data(h).waiting(), "She says no again");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_refuse_catchup", timeoutTicks = 400, skyAccess = true)
    public static void shepherdCatchesUpWhenTheNightIsOver(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = underTheSky(h);
            follow(player);
            ShepherdTalk.close(player);
            long away = h.getLevel().getGameTime() - ShepherdPolicy.DAY_TICKS;
            data(h).lastNear(away);
            h.assertTrue(data(h).waiting(), "She stays for it");
            h.getLevel().setDayTime(6000);
            int score = data(h).affinity();
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(!data(h).waiting() && player.getUUID().equals(data(h).follower()), "She said she would"))
                    .thenExecuteAfter(A_POLL, () -> h.assertTrue(data(h).affinity() == score, "Staying was her doing, so nobody left her: " + data(h).affinity()))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_refuse_wary", timeoutTicks = 400)
    public static void shepherdWaryHasNoOpinions(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = met(h, player);
            MillerPattern.appraise(h.getLevel().getServer(), player.getUUID(), Deed.ARCHIVIST_KILLED);
            data(h).affinity(ShepherdAffinity.SILENT_BELOW - 100);
            data(h).waiting(true);
            h.spawn(ModEntities.ARCHIVIST.get(), SPOT.offset(3, 0, 0));
            she.interact(player, InteractionHand.MAIN_HAND);
            follow(player);
            assertOn(h, null);
            h.assertTrue(!data(h).waiting(), "Equipment goes where it is told");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_will_spell", timeoutTicks = 400)
    public static void shepherdQuietHasItsLimit(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            sated(h);
            data(h).lastTalk(h.getLevel().getGameTime() - ShepherdPolicy.SILENCE_TICKS);
            ShepherdReactions.quietLongAgo();
            var keys = ShepherdAmbients.pools(Trigger.SILENCE).stream().flatMap(pool -> pool.variants().stream()).map(ShepherdAffinity.Say::key).toList();
            h.succeedWhen(() -> h.assertTrue(keys.contains(ShepherdReactions.said()), "She has held it long enough: " + ShepherdReactions.said()));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_refuse_fight", timeoutTicks = 400, skyAccess = true)
    public static void shepherdFollowInAFightIsNeverRefused(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = underTheSky(h);
            h.spawn(EntityType.ZOMBIE, SPOT.offset(4, 0, 0));
            follow(player);
            assertOn(h, null);
            h.assertTrue(!data(h).waiting(), "She comes");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_refuse_archivist", timeoutTicks = 400)
    public static void shepherdWillNotHuntTheQuietOne(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = met(h, player);
            var server = h.getLevel().getServer();
            MillerPattern.appraise(server, player.getUUID(), Deed.ARCHIVIST_KILLED);
            data(h).waiting(true);
            h.spawn(ModEntities.ARCHIVIST.get(), SPOT.offset(3, 0, 0));
            she.interact(player, InteractionHand.MAIN_HAND);
            follow(player);
            assertOn(h, "hub.refuse.archivist");
            ShepherdTalk.reply(player, ShepherdTalk.liveId(), ShepherdConversation.CONTINUE);
            follow(player);
            assertOn(h, "hub.refuse.archivist.yield");
            h.assertTrue(!data(h).waiting(), "It never strands anyone");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_silent_talk", timeoutTicks = 400)
    public static void shepherdBelowTheLineOnlyRecords(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = met(h, player);
            data(h).affinity(ShepherdAffinity.SILENT_BELOW - 1);
            she.interact(player, InteractionHand.MAIN_HAND);
            talk(player);
            h.assertTrue(ShepherdTalk.liveLines().equals(List.of("shepherd.hub.talk.silent")), "A recording: " + ShepherdTalk.liveLines());
            ShepherdTalk.reply(player, ShepherdTalk.liveId(), ShepherdConversation.CONTINUE);
            data(h).affinity(ShepherdAffinity.SILENT_BELOW);
            talk(player);
            h.assertTrue(ShepherdTalk.liveLines().equals(List.of("shepherd.hub.talk.wary")), "At the line, Wary: " + ShepherdTalk.liveLines());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_silent_equipment", timeoutTicks = 400)
    public static void shepherdSilentStillWorksAsEquipment(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = met(h, player);
            data(h).affinity(-1000);
            data(h).waiting(true);
            boolean lamp = data(h).lamp();
            she.interact(player, InteractionHand.MAIN_HAND);
            follow(player);
            h.assertTrue(!data(h).waiting(), "Follow works");
            ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.LAMP);
            h.assertTrue(data(h).lamp() != lamp, "So does the lamp");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_silent_deeds", timeoutTicks = 400)
    public static void shepherdDeedsStillCountInSilence(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = met(h, player);
            int score = ShepherdAffinity.SILENT_BELOW - 1;
            data(h).affinity(score);
            ShepherdReactions.saw(player, Deed.WARM_SHELTER);
            h.assertTrue(data(h).affinity() == score + Deed.WARM_SHELTER.score(), "It counted: " + data(h).affinity());
            she.interact(player, InteractionHand.MAIN_HAND);
            talk(player);
            h.assertTrue(ShepherdTalk.liveLines().equals(List.of("shepherd.hub.talk.wary")), "The climb back ends it: " + ShepherdTalk.liveLines());
            h.succeed();
        });
    }

    private static final String VOICE = "shepherd.slip.voice", SORRY = "shepherd.slip.sorry";

    private static void assertHeldBack(GameTestHelper h) {
        h.assertTrue(!VOICE.equals(ShepherdReactions.said()), "It waits");
        h.assertTrue(MillerPattern.slipDue(h.getLevel().getServer()), "And stays due");
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_slip_fight", timeoutTicks = 400)
    public static void shepherdNeverSlipsInAFight(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            h.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(16.5, 4, 10.5));
            MillerPattern.slipNow(h.getLevel().getServer());
            h.startSequence().thenExecuteAfter(A_POLL, () -> assertHeldBack(h)).thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_slip_talk", timeoutTicks = 400)
    public static void shepherdNeverSlipsOverATalk(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player).interact(player, InteractionHand.MAIN_HAND);
            MillerPattern.slipNow(h.getLevel().getServer());
            h.startSequence().thenExecuteAfter(A_POLL, () -> assertHeldBack(h)).thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_slip_wary", timeoutTicks = 400)
    public static void shepherdNeverSlipsAtWary(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            data(h).affinity(-300);
            MillerPattern.slipNow(h.getLevel().getServer());
            h.startSequence().thenExecuteAfter(A_POLL, () -> assertHeldBack(h)).thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_slip_sorry", timeoutTicks = 400)
    public static void shepherdSaysSorryAfter(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            sated(h);
            var server = h.getLevel().getServer();
            MillerPattern.slipNow(server);
            h.startSequence()
                    .thenWaitUntil(() -> assertSaid(h, VOICE))
                    .thenExecute(() -> h.assertTrue(!MillerPattern.slipDue(server), "It has played"))
                    .thenWaitUntil(() -> assertSaid(h, SORRY))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_slip_instead", timeoutTicks = 500)
    public static void shepherdATalkTakesThePlaceOfSorry(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = met(h, player);
            sated(h);
            MillerPattern.slipNow(h.getLevel().getServer());
            h.startSequence()
                    .thenWaitUntil(() -> assertSaid(h, VOICE))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        ShepherdTalk.close(player);
                    })
                    .thenExecuteAfter(2 * A_POLL, () -> assertSaid(h, VOICE))
                    .thenSucceed();
        });
    }

    private static long now(GameTestHelper h) {
        return h.getLevel().getServer().overworld().getGameTime();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_quiet_strip", timeoutTicks = 600)
    public static void shepherdQuietHoldsTheStrip(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = met(h, player);
            ShepherdReactions.quietFor(data(h), now(h));
            int score = data(h).affinity();
            ShepherdReactions.saw(player, Deed.CALLED_SHEPHERD);
            assertSaid(h, null);
            h.assertTrue(data(h).affinity() == score + Deed.CALLED_SHEPHERD.score(), "The score still moves: " + data(h).affinity());
            h.assertTrue(she.mood() == Deed.CALLED_SHEPHERD.mood(), "Her face still shows it: " + she.mood());
            h.assertTrue(data(h).lastAmbient() == ShepherdSavedData.NOT_SET, "Nothing was said, so no gap starts");
            h.assertFalse(ShepherdReactions.murmur(h.getLevel(), Trigger.SILENCE), "No unasked line");
            MillerPattern.slipNow(h.getLevel().getServer());
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> {
                        assertSaid(h, null);
                        h.assertTrue(MillerPattern.slipDue(h.getLevel().getServer()), "The slip is held, not spent");
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_quiet_once", timeoutTicks = 400)
    public static void shepherdQuietKeepsAOnceOnlyLine(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            ShepherdReactions.quietFor(data(h), now(h));
            var near = List.of(player);
            var hearth = List.of(player.blockPosition());
            ShepherdEggs.wall(h.getLevel(), near, hearth, false);
            assertSaid(h, null);
            h.assertFalse(data(h).flag(ShepherdEggs.WALL), "Not spent on a line nobody heard");
            ShepherdReactions.skipDay(h.getLevel().getServer());
            ShepherdEggs.wall(h.getLevel(), near, hearth, false);
            assertSaid(h, ShepherdEggs.WALL_LINE);
            h.assertTrue(data(h).flag(ShepherdEggs.WALL), "It played after");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_quiet_prompts", timeoutTicks = 800)
    public static void shepherdQuietHoldsPrompts(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = met(h, player);
            data(h).affinity(260);
            ShepherdPrompt.repeatEvery(10);
            ShepherdReactions.quietFor(data(h), now(h));
            h.startSequence()
                    .thenExecuteAfter(15, () -> {
                        h.assertTrue(data(h).promptNode() == null, "Nothing new is raised: " + data(h).promptNode());
                        data(h).promptNode(ShepherdPrompt.THE_NAME);
                    })
                    .thenExecuteAfter(15, () -> {
                        h.assertTrue(ShepherdPrompt.shows() == 0, "One waiting does not show");
                        she.interact(player, InteractionHand.MAIN_HAND);
                        h.assertTrue(ShepherdTalk.live() && ShepherdTalk.liveNode() == null, "A right-click is the hub: " + ShepherdTalk.liveNode());
                        ShepherdTalk.close(player);
                        ShepherdReactions.skipDay(h.getLevel().getServer());
                    })
                    .thenWaitUntil(() -> h.assertTrue(ShepherdPrompt.shows() > 0, "It shows once she is back"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_quiet_ends", timeoutTicks = 600)
    public static void shepherdQuietEndsWithALine(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            sated(h);
            ShepherdReactions.quietFor(data(h), now(h));
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> {
                        assertSaid(h, null);
                        ShepherdReactions.skipDay(h.getLevel().getServer());
                    })
                    .thenWaitUntil(() -> assertSaid(h, QUIET_OVER))
                    .thenExecute(() -> again(h))
                    .thenExecuteAfter(A_POLL, () -> h.assertFalse(QUIET_OVER.equals(ShepherdReactions.said()), "Once"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_quiet_skip", timeoutTicks = 400)
    public static void shepherdSkipDayEndsTheQuiet(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            ShepherdReactions.quietFor(data(h), now(h));
            h.assertTrue(ShepherdReactions.quiet(data(h), now(h)), "Quiet");
            h.assertTrue(ShepherdReactions.quiet(data(h), now(h) + ShepherdPolicy.DAY_TICKS - 1), "All day");
            h.assertFalse(ShepherdReactions.quiet(data(h), now(h) + ShepherdPolicy.DAY_TICKS), "And no more");
            ShepherdReactions.skipDay(h.getLevel().getServer());
            h.assertFalse(ShepherdReactions.quiet(data(h), now(h)), "The day has gone by her clocks");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_quiet_talk", timeoutTicks = 400)
    public static void shepherdQuietTalkSaysNotToday(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = met(h, player);
            ShepherdReactions.quietFor(data(h), now(h));
            she.interact(player, InteractionHand.MAIN_HAND);
            ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.TALK);
            h.assertTrue("hub.talk.quiet".equals(ShepherdTalk.liveNode()), "Talk: " + ShepherdTalk.liveNode());
            ShepherdTalk.close(player);
            ShepherdReactions.skipDay(h.getLevel().getServer());
            she.interact(player, InteractionHand.MAIN_HAND);
            ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.TALK);
            h.assertFalse("hub.talk.quiet".equals(ShepherdTalk.liveNode()), "Over: " + ShepherdTalk.liveNode());
            h.succeed();
        });
    }

    private static final int T5_ASK = 0, T5_KIND = 1, T5_BLUNT = 2, T5_GO_ON = -1, A_LOOK = 25;
    private static final String ENOCH = "shepherd.t5.read.enoch.1";

    /** They hold the sheet's file, read out of a document as anyone would. */
    private static void readTheSheet(GameTestHelper h, ServerPlayer player) {
        var sheet = ShepherdNine.register();
        player.setItemInHand(InteractionHand.MAIN_HAND, sheet);
        sheet.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
    }

    private static void readHisLog(GameTestHelper h, ServerPlayer player) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("doc_type", "terminus_log_last");
        var log = new net.minecraft.world.item.ItemStack(com.frozendawn.init.ModItems.ORSA_DOCUMENT.get());
        log.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        player.setItemInHand(InteractionHand.MAIN_HAND, log);
        log.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
    }

    private static void assertPrompt(GameTestHelper h, String node) {
        h.assertTrue(java.util.Objects.equals(node, data(h).promptNode()), "Waiting: " + data(h).promptNode());
    }

    private static void answer(ServerPlayer player, int... slots) {
        for (int slot : slots) ShepherdTalk.reply(player, ShepherdTalk.liveId(), slot);
    }

    /** Up, past the first meeting, with the sheet out of the husk. */
    private static ShepherdEntity forT5(GameTestHelper h, ServerPlayer player) {
        var she = met(h, player);
        data(h).flag(ShepherdNine.SHEET_TAKEN, true);
        return she;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t5_sheet", timeoutTicks = 300)
    public static void shepherdT5WaitsForTheSheet(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            forT5(h, player);
            h.startSequence()
                    .thenExecuteAfter(A_LOOK, () -> {
                        assertPrompt(h, null);
                        readTheSheet(h, player);
                    })
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_FILE))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t5_quiet", timeoutTicks = 300)
    public static void shepherdT5WaitsOutTheQuiet(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            forT5(h, player);
            readTheSheet(h, player);
            ShepherdReactions.quietFor(data(h), now(h));
            h.startSequence()
                    .thenExecuteAfter(A_LOOK, () -> {
                        assertPrompt(h, null);
                        ShepherdReactions.skipDay(h.getLevel().getServer());
                    })
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_FILE))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t5_wary", timeoutTicks = 300)
    public static void shepherdNoT5AtWary(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            forT5(h, player);
            readTheSheet(h, player);
            data(h).affinity(-300);
            h.startSequence()
                    .thenExecuteAfter(A_LOOK, () -> {
                        assertPrompt(h, null);
                        data(h).affinity(0);
                    })
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_FILE))
                    .thenSucceed();
        });
    }

    /** The gate is whoever is near her, and the one she ends up telling need not be who read it. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t5_near", timeoutTicks = 300)
    public static void shepherdT5ReadsWhoeverIsNear(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var speaker = player(h);
            var she = forT5(h, speaker);
            var reader = player(h);
            readTheSheet(h, reader);
            var beside = reader.position();
            reader.setPos(beside.add(40, 0, 0));
            h.startSequence()
                    .thenExecuteAfter(A_LOOK, () -> {
                        assertPrompt(h, null);
                        reader.setPos(beside);
                    })
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_FILE))
                    .thenExecute(() -> {
                        she.interact(speaker, InteractionHand.MAIN_HAND);
                        h.assertTrue(ShepherdPrompt.THE_FILE.equals(ShepherdTalk.liveNode()), "On: " + ShepherdTalk.liveNode());
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t5_enoch", timeoutTicks = 300)
    public static void shepherdEnochsBeatNeedsHisLog(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = forT5(h, player);
            readTheSheet(h, player);
            readHisLog(h, player);
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_FILE))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        answer(player, T5_KIND);
                        h.assertTrue("t5.read".equals(ShepherdTalk.liveNode()), "On: " + ShepherdTalk.liveNode());
                        h.assertTrue(ShepherdTalk.liveLines().contains(ENOCH), "With his log: " + ShepherdTalk.liveLines());
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t5_no_enoch", timeoutTicks = 300)
    public static void shepherdT5OpensOnTheRegisterAlone(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = forT5(h, player);
            readTheSheet(h, player);
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_FILE))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        answer(player, T5_KIND);
                        h.assertFalse(ShepherdTalk.liveLines().contains(ENOCH), "No log, no Enoch: " + ShepherdTalk.liveLines());
                    })
                    .thenSucceed();
        });
    }

    /** "You're still Aurora." is someone naming her, and it counts in the moment it is said. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t5_someone", timeoutTicks = 300)
    public static void shepherdNamingHerAtT5LeansSomeone(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = forT5(h, player);
            readTheSheet(h, player);
            var server = h.getLevel().getServer();
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_FILE))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        answer(player, T5_KIND, T5_GO_ON, T5_KIND, T5_GO_ON);
                        h.assertTrue("t5.close".equals(ShepherdTalk.liveNode()), "On: " + ShepherdTalk.liveNode());
                        h.assertTrue(MillerPattern.leaning(server) == SelfModel.Lean.SOMEONE, "Leans: " + MillerPattern.leaning(server));
                        h.assertTrue(MillerPattern.stage(server) == SelfModel.Stage.LEANING, "Stage: " + MillerPattern.stage(server));
                        h.assertTrue(data(h).flag("t5_done"), "For M3");
                        h.assertTrue(MillerPattern.remembers(server, player.getUUID(), "t5_before"), "She keeps it");
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t5_left", timeoutTicks = 300)
    public static void shepherdNeverNamedAtT5LeansLeft(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = forT5(h, player);
            readTheSheet(h, player);
            var server = h.getLevel().getServer();
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_FILE))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        answer(player, T5_KIND, T5_GO_ON, T5_BLUNT, T5_GO_ON);
                        h.assertTrue("t5.close.left".equals(ShepherdTalk.liveNode()), "On: " + ShepherdTalk.liveNode());
                        h.assertTrue(MillerPattern.leaning(server) == SelfModel.Lean.LEFT, "Leans: " + MillerPattern.leaning(server));
                        answer(player, T5_ASK, T5_GO_ON);
                        h.assertTrue("t5.end".equals(ShepherdTalk.liveNode()), "On: " + ShepherdTalk.liveNode());
                    })
                    .thenSucceed();
        });
    }

    private static List<String> bleed(String from) {
        return ShepherdAmbients.pools(Trigger.BLEED).stream().filter(pool -> from.equals(pool.from())).flatMap(pool -> pool.variants().stream()).map(ShepherdAffinity.Say::key).toList();
    }

    /** Free to speak, with the noticing line already behind her. */
    private static void readyToSay(GameTestHelper h) {
        var server = h.getLevel().getServer();
        MillerPattern.want(server, Drive.CURIOSITY, 1);
        MillerPattern.spoken(server, Attention.NOTICING);
    }

    private static void assertSaidFrom(GameTestHelper h, List<String> keys) {
        h.assertTrue(keys.contains(ShepherdReactions.said()), "Said: " + ShepherdReactions.said());
    }

    private static void chat(ServerPlayer player, String text) {
        NeoForge.EVENT_BUS.post(new ServerChatEvent(player, text, Component.literal(text)));
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_bleed_noticing", timeoutTicks = 500)
    public static void shepherdBleedNeedsNoticing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            readyToSay(h);
            var server = h.getLevel().getServer();
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> {
                        h.assertTrue(data(h).deedReady("ambient.BLEED") == 0, "Nothing turns up in a product");
                        again(h);
                        for (int i = 0; i < 3; i++) MillerPattern.heard(server, player.getUUID(), new Heard.Reply(ShepherdDialogue.Slot.KIND, List.of("+calls_me_aurora"), null));
                        MillerPattern.skipDay(server);
                    })
                    .thenWaitUntil(() -> assertSaidFrom(h, bleed("noticing")))
                    .thenExecute(() -> h.assertTrue(data(h).deedReady("ambient.BLEED") > now(h) + 71000, "Three days: " + data(h).deedReady("ambient.BLEED")))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_bleed_walls", timeoutTicks = 400)
    public static void shepherdBleedTurnsAtTheWalls(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            readyToSay(h);
            MillerPattern.witness(h.getLevel().getServer(), PatternLaws.CAUSAL, 1);
            h.startSequence().thenWaitUntil(() -> assertSaidFrom(h, bleed("walls"))).thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_bleed_after", timeoutTicks = 400)
    public static void shepherdBleedChangesAfterT5(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            readyToSay(h);
            MillerPattern.lean(h.getLevel().getServer(), player.getUUID());
            h.startSequence().thenWaitUntil(() -> assertSaidFrom(h, bleed("after"))).thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_fewer_once", timeoutTicks = 400)
    public static void shepherdFewerPlaysOnce(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            sated(h);
            chat(player, "we have less sticks");
            h.startSequence()
                    .thenWaitUntil(() -> assertSaid(h, "shepherd.bleed.fewer"))
                    .thenExecuteAfter(60, () -> assertSaid(h, "shepherd.bleed.fewer"))
                    .thenWaitUntil(() -> assertSaid(h, "shepherd.bleed.fewer.aside"))
                    .thenExecute(() -> {
                        again(h);
                        chat(player, "even less sticks");
                    })
                    .thenExecuteAfter(A_POLL, () -> assertSaid(h, null))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_fewer_echo", timeoutTicks = 200)
    public static void shepherdFewerEchoesNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            sated(h);
            chat(player, "less zombies please");
            h.startSequence()
                    .thenWaitUntil(() -> assertSaid(h, "shepherd.bleed.fewer"))
                    .thenExecute(() -> h.assertTrue(ShepherdReactions.saidArg() == null, "Their words are not in it: " + ShepherdReactions.saidArg()))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_fewer_far", timeoutTicks = 300)
    public static void shepherdFewerNeedsHerToHear(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            sated(h);
            var faraway = ShepherdTestWorld.onlinePlayer(h, player.position().add(40, 0, 0));
            chat(faraway, "less sticks");
            h.startSequence()
                    .thenExecuteAfter(A_LOOK, () -> {
                        assertSaid(h, null);
                        h.assertFalse(data(h).flag("said_fewer"), "Still to come");
                        chat(player, "less sticks");
                    })
                    .thenWaitUntil(() -> assertSaid(h, "shepherd.bleed.fewer"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_recall_nine", timeoutTicks = 400)
    public static void shepherdEachNewKeepsakeHasALine(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            var server = h.getLevel().getServer();
            MillerPattern.appraise(server, player.getUUID(), Deed.NINE_FOUND);
            MillerPattern.skipDay(server);
            sated(h);
            h.startSequence().thenWaitUntil(() -> assertSaid(h, "shepherd.recall.nine_found")).thenSucceed();
        });
    }
}
