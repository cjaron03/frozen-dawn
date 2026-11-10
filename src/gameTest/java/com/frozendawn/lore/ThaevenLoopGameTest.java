package com.frozendawn.lore;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.data.ThaevenLoreSavedData;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * CH-37's loop on a live server. Each test is one synchronous tick that fires the payoff through the qualifying
 * death, as PayoffGameTest does, and the batch puts the payoff, the erased flag and the world semantic back.
 */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThaevenLoopGameTest {
    private static boolean firedBefore, turnedBefore, eligibleBefore, erasedBefore;
    private static int sourceBefore, loopShownBefore;
    private static java.util.UUID brokenBefore, returningBefore;

    @BeforeBatch(batch = "thaevenloop") public static void record(ServerLevel level) {
        GameTestReporting.installReporter(level);
        MinecraftServer server = level.getServer();
        firedBefore = PayoffState.fired(server);
        turnedBefore = PayoffState.terminusTurned(server);
        eligibleBefore = PayoffState.eligible(server);
        erasedBefore = ReturnedHearthSavedData.get(server).maeveErased();
        ThaevenLoreSavedData lore = ThaevenLoreSavedData.get(server);
        sourceBefore = lore.semanticRevision(ThaevenSemanticKey.PAYOFF_SOURCE);
        loopShownBefore = lore.loopShown();
        brokenBefore = lore.loopBrokenOwed().orElse(null);
        returningBefore = lore.loopReturningOwed().orElse(null);
    }

    @AfterBatch(batch = "thaevenloop") public static void restore(ServerLevel level) {
        MinecraftServer server = level.getServer();
        PayoffState.setForTest(server, firedBefore);
        PayoffState.setTerminusTurnedForTest(server, turnedBefore);
        PayoffState.setEligibleForTest(server, eligibleBefore);
        PayoffGameTest.erased(server, erasedBefore);
        ThaevenLoreSavedData lore = ThaevenLoreSavedData.get(server);
        lore.setSemanticForTest(ThaevenSemanticKey.PAYOFF_SOURCE, sourceBefore);
        lore.setLoopShownForTest(loopShownBefore);
        lore.setLoopBrokenOwed(brokenBefore);
        lore.setLoopReturningOwed(returningBefore);
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "thaevenloop")
    public static void firingThePayoffReInksRecordOne(GameTestHelper h) {
        MinecraftServer server = fresh(h);
        h.assertTrue(source(server) == 0, "Record I reads unresolved before the payoff");
        killErased(h, server, "loop_reink");
        h.assertTrue(PayoffState.fired(server), "The death fires the payoff");
        h.assertTrue(source(server) == 1, "And re-inks Record I's source");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "thaevenloop")
    public static void anOlderWorldNeverReInks(GameTestHelper h) {
        MinecraftServer server = fresh(h);
        PayoffState.setEligibleForTest(server, false);
        try {
            killErased(h, server, "loop_old");
        } finally {
            PayoffState.setEligibleForTest(server, true);
        }
        h.assertFalse(PayoffState.fired(server), "An older world never fires the payoff");
        h.assertTrue(source(server) == 0, "So Record I stays unresolved");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "thaevenloop")
    public static void takeoverBeatsShowOncePerWorld(GameTestHelper h) {
        MinecraftServer server = freshLoop(h);
        var a = PayoffGameTest.player(h, "loop_takeover_a");
        var b = PayoffGameTest.player(h, "loop_takeover_b");
        ThaevenLoopManager.onTakeoverStart(a);
        h.assertTrue(shown(server) == ThaevenLoopBeat.CROSSED.bit(), "The first takeover shows the crossing");
        ThaevenLoopManager.onTakeoverStart(b);
        int both = ThaevenLoopBeat.CROSSED.bit() | ThaevenLoopBeat.CROSSED_AGAIN.bit();
        h.assertTrue(shown(server) == both, "The second, by anyone, shows it again");
        ThaevenLoopManager.onTakeoverStart(a);
        h.assertTrue(shown(server) == both, "A third shows nothing");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "thaevenloop")
    public static void anArenaDeathOwesTheBrokenLineOnRespawn(GameTestHelper h) {
        MinecraftServer server = freshLoop(h);
        var a = PayoffGameTest.player(h, "loop_broken_a");
        var b = PayoffGameTest.player(h, "loop_broken_b");
        ThaevenLoopManager.died(b, false);
        h.assertTrue(lore(server).loopBrokenOwed().isEmpty(), "A death outside the arena owes nothing");
        ThaevenLoopManager.died(a, true);
        h.assertTrue(a.getUUID().equals(lore(server).loopBrokenOwed().orElse(null)), "A is owed the broken lines");
        ThaevenLoopManager.respawned(b, true);
        h.assertTrue(shown(server) == 0, "Another player's respawn shows nothing");
        ThaevenLoopManager.respawned(a, true);
        h.assertTrue(shown(server) == ThaevenLoopBeat.BODY_BROKE.bit(), "A's respawn shows them");
        h.assertTrue(lore(server).loopBrokenOwed().isEmpty(), "And clears the debt");
        h.assertTrue(a.getUUID().equals(lore(server).loopReturningOwed().orElse(null)), "A is owed the return");
        ThaevenLoopManager.died(b, true);
        h.assertTrue(lore(server).loopBrokenOwed().isEmpty(), "A later arena death owes nothing");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "thaevenloop")
    public static void onlyTheDeadPlayerIsOwedTheReturn(GameTestHelper h) {
        MinecraftServer server = freshLoop(h);
        var a = PayoffGameTest.player(h, "loop_return_a");
        var b = PayoffGameTest.player(h, "loop_return_b");
        ThaevenLoopManager.died(a, true);
        ThaevenLoopManager.respawned(a, true);
        ThaevenLoopManager.tick(b, true);
        h.assertTrue(a.getUUID().equals(lore(server).loopReturningOwed().orElse(null)), "B entering clears nothing");
        ThaevenLoopManager.tick(a, false);
        h.assertTrue(lore(server).loopReturningOwed().isPresent(), "Nor does A outside the arena");
        ThaevenLoopManager.tick(a, true);
        int all = ThaevenLoopBeat.BODY_BROKE.bit() | ThaevenLoopBeat.RETURNING.bit();
        h.assertTrue(shown(server) == all, "A entering shows the return");
        h.assertTrue(lore(server).loopReturningOwed().isEmpty(), "Once");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "thaevenloop")
    public static void aRespawnInsideTheArenaIsNotAReturn(GameTestHelper h) {
        MinecraftServer server = freshLoop(h);
        var a = PayoffGameTest.player(h, "loop_inside_a");
        ThaevenLoopManager.died(a, true);
        ThaevenLoopManager.respawned(a, true);
        ThaevenLoopManager.tick(a, true);
        h.assertTrue((shown(server) & ThaevenLoopBeat.RETURNING.bit()) == 0, "Waking inside the arena is no return");
        ThaevenLoopManager.tick(a, false);
        ThaevenLoopManager.tick(a, true);
        h.assertTrue((shown(server) & ThaevenLoopBeat.RETURNING.bit()) != 0, "Leaving and coming back is");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "thaevenloop")
    public static void onlyAPlayingReturnCounts(GameTestHelper h) {
        MinecraftServer server = freshLoop(h);
        var a = PayoffGameTest.player(h, "loop_mode_a");
        ThaevenLoopManager.died(a, true);
        ThaevenLoopManager.respawned(a, true);
        ThaevenLoopManager.tick(a, false);
        try {
            a.setGameMode(GameType.SPECTATOR);
            ThaevenLoopManager.tick(a, true);
            a.setGameMode(GameType.CREATIVE);
            ThaevenLoopManager.tick(a, true);
        } finally {
            a.setGameMode(GameType.SURVIVAL);
        }
        h.assertTrue((shown(server) & ThaevenLoopBeat.RETURNING.bit()) == 0, "A spectator or creative entry shows nothing");
        ThaevenLoopManager.tick(a, true);
        h.assertTrue((shown(server) & ThaevenLoopBeat.RETURNING.bit()) != 0, "A survival entry does");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "thaevenloop")
    public static void theHeartFallingDropsTheReturn(GameTestHelper h) {
        MinecraftServer server = freshLoop(h);
        var a = PayoffGameTest.player(h, "loop_fallen_a");
        ThaevenLoopManager.died(a, true);
        ThaevenLoopManager.respawned(a, true);
        ThaevenLoopManager.heartDown(server);
        h.assertTrue(lore(server).loopReturningOwed().isEmpty(), "A fallen Heart owes no return");
        ThaevenLoopManager.tick(a, true);
        h.assertTrue((shown(server) & ThaevenLoopBeat.RETURNING.bit()) == 0, "So it never shows");

        lore(server).resetLoop();
        ThaevenLoopManager.died(a, true);
        ThaevenLoopManager.respawned(a, false);
        h.assertTrue(shown(server) == ThaevenLoopBeat.BODY_BROKE.bit(), "A respawn after the fall still breaks");
        h.assertTrue(lore(server).loopReturningOwed().isEmpty(), "But owes no return");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "thaevenloop")
    public static void noBeatInAnErasedWorld(GameTestHelper h) {
        MinecraftServer server = freshLoop(h);
        var a = PayoffGameTest.player(h, "loop_erased_a");
        PayoffGameTest.erased(server, true);
        try {
            ThaevenLoopManager.onTakeoverStart(a);
            ThaevenLoopManager.died(a, true);
            h.assertTrue(lore(server).loopBrokenOwed().isEmpty(), "No debt in an erased world");
            lore(server).setLoopBrokenOwed(a.getUUID());
            lore(server).setLoopReturningOwed(a.getUUID());
            ThaevenLoopManager.respawned(a, true);
            ThaevenLoopManager.tick(a, true);
        } finally {
            PayoffGameTest.erased(server, false);
        }
        h.assertTrue(shown(server) == 0, "Takeover, death and entry show nothing");
        h.succeed();
    }

    static MinecraftServer freshLoop(GameTestHelper h) {
        MinecraftServer server = fresh(h);
        lore(server).resetLoop();
        return server;
    }

    private static ThaevenLoreSavedData lore(MinecraftServer server) {
        return ThaevenLoreSavedData.get(server);
    }

    private static int shown(MinecraftServer server) {
        return lore(server).loopShown();
    }

    static MinecraftServer fresh(GameTestHelper h) {
        MinecraftServer server = PayoffGameTest.fresh(h);
        ThaevenLoreSavedData.get(server).setSemanticForTest(ThaevenSemanticKey.PAYOFF_SOURCE, 0);
        return server;
    }

    private static void killErased(GameTestHelper h, MinecraftServer server, String name) {
        var player = PayoffGameTest.player(h, name);
        PayoffGameTest.erased(server, true);
        try {
            PayoffGameTest.kill(player);
        } finally {
            PayoffGameTest.erased(server, false);
        }
    }

    private static int source(MinecraftServer server) {
        return ThaevenLoreSavedData.get(server).semanticRevision(ThaevenSemanticKey.PAYOFF_SOURCE);
    }
}
