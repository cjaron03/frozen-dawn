package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.shepherd.ShepherdTalk.Action;
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

/** What her score does to her. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdAffinityGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);
    private static final int CONTINUE = -1;

    @BeforeBatch(batch = "shepherd_tier_wary") public static void waryBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_tier_wary") public static void waryAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_tier_leaving") public static void leavingBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_tier_leaving") public static void leavingAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_tier_hub") public static void hubBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_tier_hub") public static void hubAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_tier_replay") public static void replayBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_tier_replay") public static void replayAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_tier_command") public static void commandBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_tier_command") public static void commandAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    private static ServerPlayer player(GameTestHelper h) {
        return ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
    }

    private static ShepherdEntity awake(GameTestHelper h, ServerPlayer player) {
        data(h).stage(Stage.AWAKE);
        return ShepherdEntity.spawnFrom(h.getLevel(), h.absolutePos(SPOT), player.getUUID());
    }

    private static void command(GameTestHelper h, String command) {
        var server = h.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static void assertFace(GameTestHelper h, ShepherdEntity she, Mood mood) {
        h.assertTrue(she.mood() == mood, "Her face: " + she.mood());
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_tier_wary", timeoutTicks = 400)
    public static void shepherdAtWarySheRestsOnTheRecordingFace(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            data(h).affinity(-300);
            h.startSequence()
                    .thenWaitUntil(() -> assertFace(h, she, Mood.RECORDING))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        assertFace(h, she, Mood.RECORDING);
                        ShepherdTalk.close(player);
                        assertFace(h, she, Mood.RECORDING);
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_tier_leaving", timeoutTicks = 400)
    public static void shepherdLeavingWaryBringsHerFaceBack(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = awake(h, player(h));
            data(h).affinity(-300);
            h.startSequence()
                    .thenWaitUntil(() -> assertFace(h, she, Mood.RECORDING))
                    .thenExecute(() -> data(h).affinity(0))
                    .thenWaitUntil(() -> assertFace(h, she, Mood.NEUTRAL))
                    .thenSucceed();
        });
    }

    private static void assertTalkOpens(GameTestHelper h, ShepherdEntity she, ServerPlayer player, int score, String node) {
        data(h).affinity(score);
        she.interact(player, InteractionHand.MAIN_HAND);
        ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.TALK);
        h.assertTrue(node.equals(ShepherdTalk.liveNode()), "At " + score + ": " + ShepherdTalk.liveNode());
        ShepherdTalk.reply(player, ShepherdTalk.liveId(), CONTINUE);
        ShepherdTalk.close(player);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_tier_hub", timeoutTicks = 400)
    public static void shepherdHubTalkFollowsTheTier(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            assertTalkOpens(h, she, player, -300, "hub.talk");
            assertTalkOpens(h, she, player, 0, "hub.talk.plain");
            assertTalkOpens(h, she, player, 300, "hub.talk.warm");
            assertTalkOpens(h, she, player, 900, "hub.talk.warm");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_tier_replay", timeoutTicks = 400)
    public static void shepherdReplayClearsTheScore(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            awake(h, player(h));
            data(h).affinity(300);
            command(h, "fd shepherd replay");
            h.assertTrue(data(h).tier() == ShepherdAffinity.Tier.NEUTRAL, "Back to nothing: " + data(h).affinity());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_tier_command", timeoutTicks = 400)
    public static void shepherdAffinityCommandSetsTheScore(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            command(h, "fd shepherd affinity 260");
            h.assertTrue(data(h).affinity() == 260, "Set: " + data(h).affinity());
            command(h, "fd shepherd affinity -300");
            h.assertTrue(data(h).tier() == ShepherdAffinity.Tier.WARY, "Negative too: " + data(h).affinity());
            h.succeed();
        });
    }
}
