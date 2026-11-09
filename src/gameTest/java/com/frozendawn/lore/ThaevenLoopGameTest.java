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
    private static int sourceBefore;

    @BeforeBatch(batch = "thaevenloop") public static void record(ServerLevel level) {
        GameTestReporting.installReporter(level);
        MinecraftServer server = level.getServer();
        firedBefore = PayoffState.fired(server);
        turnedBefore = PayoffState.terminusTurned(server);
        eligibleBefore = PayoffState.eligible(server);
        erasedBefore = ReturnedHearthSavedData.get(server).maeveErased();
        sourceBefore = ThaevenLoreSavedData.get(server).semanticRevision(ThaevenSemanticKey.PAYOFF_SOURCE);
    }

    @AfterBatch(batch = "thaevenloop") public static void restore(ServerLevel level) {
        MinecraftServer server = level.getServer();
        PayoffState.setForTest(server, firedBefore);
        PayoffState.setTerminusTurnedForTest(server, turnedBefore);
        PayoffState.setEligibleForTest(server, eligibleBefore);
        PayoffGameTest.erased(server, erasedBefore);
        ThaevenLoreSavedData.get(server).setSemanticForTest(ThaevenSemanticKey.PAYOFF_SOURCE, sourceBefore);
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
