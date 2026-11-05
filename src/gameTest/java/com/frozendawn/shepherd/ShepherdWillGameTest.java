package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.miller.Drives.Drive;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.miller.PatternLaws;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdAmbient.Trigger;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
}
