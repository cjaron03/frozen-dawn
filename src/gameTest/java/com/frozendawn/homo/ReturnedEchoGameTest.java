package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Payoff branch 4 on a live server: the return notice is owed only for a witnessed death, at most twice per world,
 * and never after the Heart falls. Each test is synchronous; the batch resets the notice and puts the erased flag back.
 */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReturnedEchoGameTest {
    private static boolean erasedBefore;

    @BeforeBatch(batch = "returnedecho") public static void record(ServerLevel level) {
        GameTestReporting.installReporter(level);
        MinecraftServer server = level.getServer();
        erasedBefore = ReturnedHearthSavedData.get(server).maeveErased();
        ReturnNoticeState.resetForDebug(server);
    }

    @AfterBatch(batch = "returnedecho") public static void restore(ServerLevel level) {
        MinecraftServer server = level.getServer();
        erased(server, erasedBefore);
        ReturnNoticeState.resetForDebug(server);
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "returnedecho")
    public static void aKilledReturnedRingsThenRests(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        fresh(h);
        BlockPos at = h.absolutePos(BlockPos.ZERO);
        HearthPopulationManager.clearRingRest();
        long rings = HearthPopulationManager.replacementRings();
        h.assertTrue(HearthPopulationManager.ringForDeath(level, HearthPopulationRole.RETURNED, at),
                "A killed Returned rings at once");
        h.assertFalse(HearthPopulationManager.ringForDeath(level, HearthPopulationRole.HUNTER, at),
                "A second kill inside ten minutes is silent");
        HearthPopulationManager.clearRingRest();
        h.assertFalse(HearthPopulationManager.ringForDeath(level, HearthPopulationRole.MIMIC, at),
                "A killed Mimic is silent");
        h.assertTrue(HearthPopulationManager.replacementRings() == rings + 1, "One ring counted");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "returnedecho")
    public static void noRingInAnErasedWorld(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = fresh(h);
        HearthPopulationManager.clearRingRest();
        erased(server, true);
        try {
            h.assertFalse(HearthPopulationManager.ringForDeath(level, HearthPopulationRole.RETURNED,
                    h.absolutePos(BlockPos.ZERO)), "Nothing rings once the Heart has fallen");
        } finally {
            erased(server, false);
        }
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "returnedecho")
    public static void aWitnessedDeathOwesTheNotice(GameTestHelper h) {
        MinecraftServer server = fresh(h);
        ServerPlayer a = heard(h, "echo_a");
        ServerPlayer b = heard(h, "echo_b");
        ReturnNoticeState.onDeath(a, true);
        ReturnNoticeState.onDeath(b, false);
        h.assertTrue(ReturnNoticeState.isOwed(server, a.getUUID()), "A's witnessed death owes A the notice");
        h.assertFalse(ReturnNoticeState.isOwed(server, b.getUUID()), "B's unwitnessed death owes nothing");
        h.assertTrue(ReturnNoticeState.shown(server) == 0, "Owing shows nothing yet");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "returnedecho")
    public static void twoNoticesPerWorld(GameTestHelper h) {
        MinecraftServer server = fresh(h);
        ServerPlayer a = heard(h, "echo_a");
        ServerPlayer b = heard(h, "echo_b");
        ServerPlayer c = heard(h, "echo_c");
        ReturnNoticeState.onDeath(a, true);
        ReturnNoticeState.spend(server, a.getUUID());
        h.assertFalse(ReturnNoticeState.isOwed(server, a.getUUID()), "Spending clears A's debt");
        ReturnNoticeState.onDeath(b, true);
        ReturnNoticeState.onDeath(c, true);
        ReturnNoticeState.spend(server, b.getUUID());
        h.assertTrue(ReturnNoticeState.shown(server) == 2, "Two notices shown");
        h.assertFalse(ReturnNoticeState.isOwed(server, c.getUUID()), "The second notice clears every open debt");
        h.assertTrue(ReturnNoticeState.owed(server).isEmpty(), "Nothing is left owed");
        ReturnNoticeState.onDeath(a, true);
        h.assertFalse(ReturnNoticeState.isOwed(server, a.getUUID()), "A third witnessed death owes nothing");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "returnedecho")
    public static void noNoticeAfterTheHeart(GameTestHelper h) {
        MinecraftServer server = fresh(h);
        ServerPlayer a = heard(h, "echo_a");
        ServerPlayer b = heard(h, "echo_b");
        ReturnNoticeState.onDeath(a, true);
        h.assertTrue(ReturnNoticeState.isOwed(server, a.getUUID()), "A is owed before the Heart falls");
        erased(server, true);
        try {
            UUID hearthId = major(h).id();
            h.assertFalse(HearthTransmissionManager.tryStart(h.getLevel(), null, a, hearthId),
                    "No transmission, and so no notice, starts once the Heart has fallen");
            h.assertFalse(HearthTransmissionManager.isActive(a.getUUID()), "No session opened");
            ReturnNoticeState.onDeath(b, true);
            h.assertFalse(ReturnNoticeState.isOwed(server, b.getUUID()), "A witnessed death after the Heart owes nothing");
        } finally {
            erased(server, false);
        }
        h.succeed();
    }

    /** A world before the Heart falls, with no notice shown or owed. */
    private static MinecraftServer fresh(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        erased(server, false);
        ReturnNoticeState.resetForDebug(server);
        return server;
    }

    private static void erased(MinecraftServer server, boolean erased) {
        ReturnedHearthSavedData.get(server).setMaeveErasedForDebug(erased, server.overworld().getGameTime());
    }

    /** A player who has been assessed and heard the first transmission at the Major Hearth. */
    private static ServerPlayer heard(GameTestHelper h, String name) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        UUID hearthId = major(h).id();
        data.recordArchitectAssessment(player.getUUID(), hearthId, level.getGameTime(), false);
        h.assertTrue(data.completeFirstTransmission(player.getUUID(), hearthId, level.getGameTime()),
                name + " heard the first transmission");
        return player;
    }

    /** The Major Hearth, planned here only if no earlier batch made one; its surface is left unresolved. */
    private static ReturnedHearthSavedData.HearthRecord major(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        if (data.hearth(HearthSelectionPolicy.HearthType.MAJOR).isEmpty()) {
            data.applySelectionPlan(HearthSelectionPolicy.createPlan(
                    level.getSeed(), h.absolutePos(BlockPos.ZERO)), level.getGameTime());
        }
        ReturnedHearthSavedData.HearthRecord hearth =
                data.hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        h.assertTrue(hearth != null, "No Major Hearth record to hold the contact");
        return hearth;
    }
}
