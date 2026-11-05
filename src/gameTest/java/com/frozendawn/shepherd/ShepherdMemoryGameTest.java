package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.miller.ImpressionStore.Theory;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.shepherd.ShepherdDialogue.Slot;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What she remembers of people, reached the way the game reaches it. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdMemoryGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);

    @BeforeBatch(batch = "shepherd_memory_answer") public static void answerBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_memory_answer") public static void answerAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_memory_named") public static void namedBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_memory_named") public static void namedAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_memory_hub") public static void hubBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_memory_hub") public static void hubAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_memory_death") public static void deathBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_memory_death") public static void deathAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

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

    /** She has had a day to think about it. */
    private static void aDayPasses(GameTestHelper h) {
        var server = h.getLevel().getServer();
        MillerPattern.skipDay(server);
        MillerPattern.poll(server, 1, data(h).lastTalk());
    }

    private static double lean(GameTestHelper h, ServerPlayer player, Theory theory) {
        return MillerPattern.confidence(h.getLevel().getServer(), player.getUUID(), theory);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_memory_answer", timeoutTicks = 400)
    public static void shepherdAnAnswerIsHeard(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            ShepherdTalk.start(player, ShepherdTalk.FIRST_MEETING);
            var kind = ShepherdDialogues.nodes().get(ShepherdTalk.FIRST_MEETING).responses().stream().filter(r -> r.slot() == Slot.KIND).findFirst().orElseThrow();
            ShepherdTalk.reply(player, ShepherdTalk.liveId(), Slot.KIND.ordinal());
            h.assertTrue(lean(h, player, Theory.KIND) == 0, "She has not worked it out yet");
            aDayPasses(h);
            h.assertTrue(lean(h, player, Theory.KIND) > 0, "A day later she has");
            h.assertTrue(data(h).affinity() == kind.affinity(), "Only the node moved the score: " + data(h).affinity());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_memory_named", timeoutTicks = 400)
    public static void shepherdANamedPlayerIsNoted(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            ShepherdTalk.start(player, "fm.name");
            ShepherdTalk.reply(player, ShepherdTalk.liveId(), Slot.KIND.ordinal());
            h.assertTrue("fm.name.aurora".equals(ShepherdTalk.liveNode()), "On the name: " + ShepherdTalk.liveNode());
            aDayPasses(h);
            h.assertTrue(lean(h, player, Theory.CALLS_ME_AURORA) > 0, "They said her name");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_memory_hub", timeoutTicks = 400)
    public static void shepherdHubActionsAreNoted(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            she.interact(player, InteractionHand.MAIN_HAND);
            ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.LAMP);
            aDayPasses(h);
            h.assertTrue(lean(h, player, Theory.USES_ME) > 0, "A lamp is a use");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_memory_death", timeoutTicks = 400)
    public static void shepherdAPlayersDeathLeavesNoImpression(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            player.setGameMode(GameType.SURVIVAL);
            player.hurt(h.getLevel().damageSources().genericKill(), 1000);
            h.assertTrue(player.isDeadOrDying(), "They died beside her");
            aDayPasses(h);
            for (Theory theory : Theory.values()) h.assertTrue(lean(h, player, theory) == 0, "A death is never evidence: " + theory);
            h.succeed();
        });
    }
}
