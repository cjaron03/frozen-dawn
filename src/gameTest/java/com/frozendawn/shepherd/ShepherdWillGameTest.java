package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModEntities;
import com.frozendawn.miller.Drives.Drive;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.miller.PatternLaws;
import com.frozendawn.miller.Refusal;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What she does about what is on her mind, reached the way the game reaches it. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdWillGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10), CHEST = new BlockPos(15, 4, 10);
    private static final int A_POLL = ShepherdReactions.POLL_TICKS + 10;

    @BeforeBatch(batch = "shepherd_will_quiet") public static void quietBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_will_quiet") public static void quietAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
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

    /** She has been left out under a clear night she has wanted, and is talking to them. */
    private static ServerPlayer underTheSky(GameTestHelper h) {
        var player = player(h);
        var she = met(h, player);
        h.getLevel().setDayTime(18000);
        MillerPattern.want(h.getLevel().getServer(), Drive.SKY, 1);
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
            h.startSequence().thenExecuteAfter(Refusal.YIELD_TICKS + 1, () -> {
                h.getLevel().setDayTime(18000);
                MillerPattern.want(h.getLevel().getServer(), Drive.SKY, 1);
                follow(player);
                assertOn(h, "hub.refuse.night");
                h.assertTrue(data(h).waiting(), "She says no again");
            }).thenSucceed();
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
}
