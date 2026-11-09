package com.frozendawn.lore;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.defense.TerminusLedger;
import com.frozendawn.gametest.GameTestPlayers;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The payoff flag on a live server. Each test is one synchronous tick: it sets the saved erased flag, kills its
 * players and puts the flag back before any other system ticks on it.
 */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PayoffGameTest {
    private static boolean firedBefore, turnedBefore, eligibleBefore, erasedBefore, debugBefore;

    @BeforeBatch(batch = "payoff") public static void record(ServerLevel level) {
        GameTestReporting.installReporter(level);
        var server = level.getServer();
        firedBefore = PayoffState.fired(server);
        turnedBefore = PayoffState.terminusTurned(server);
        eligibleBefore = PayoffState.eligible(server);
        erasedBefore = ReturnedHearthSavedData.get(server).maeveErased();
        debugBefore = FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.get();
    }

    @AfterBatch(batch = "payoff") public static void restore(ServerLevel level) {
        var server = level.getServer();
        PayoffState.setForTest(server, firedBefore);
        PayoffState.setTerminusTurnedForTest(server, turnedBefore);
        PayoffState.setEligibleForTest(server, eligibleBefore);
        erased(server, erasedBefore);
        FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.set(debugBefore);
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "payoff")
    public static void aDeathBeforeTheHeartChangesNothing(GameTestHelper h) {
        var server = fresh(h);
        kill(player(h, "payoff_early"));
        h.assertFalse(PayoffState.fired(server), "A death while Maeve stands fires nothing");
        h.assertFalse(TerminusLedger.turned(server), "Nor turns the Terminus");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "payoff")
    public static void theFirstDeathAfterErasureFiresOnce(GameTestHelper h) {
        var server = fresh(h);
        var a = player(h, "payoff_a");
        var b = player(h, "payoff_b");
        erased(server, true);
        try {
            kill(a);
            h.assertTrue(PayoffState.fired(server), "The first death fires it");
            h.assertTrue(TerminusLedger.turned(server), "And turns the Terminus");
            h.assertTrue(holds(a), "The dying player gets the pages");
            kill(b);
            h.assertTrue(a.getUUID().equals(PayoffState.firedBy(server)), "A second death changes nothing");
        } finally {
            erased(server, false);
        }
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "payoff")
    public static void aPlayerWhoWasAwayStillGetsThePages(GameTestHelper h) {
        var server = fresh(h);
        erased(server, true);
        try {
            kill(player(h, "payoff_firing"));
        } finally {
            erased(server, false);
        }
        var late = player(h, "payoff_late");
        h.assertFalse(holds(late), "Not before logging in");
        PayoffState.login(late);
        h.assertTrue(holds(late), "A player who was away gets the pages on login");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "payoff")
    public static void theDebugConfigNeverFiresIt(GameTestHelper h) {
        var server = fresh(h);
        boolean was = FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.get();
        FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.set(true);
        try {
            kill(player(h, "payoff_debug"));
        } finally {
            FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.set(was);
        }
        h.assertFalse(PayoffState.fired(server), "The debug config never fires the payoff");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "payoff")
    public static void anOldWorldNeverFires(GameTestHelper h) {
        var server = fresh(h);
        PayoffState.setEligibleForTest(server, false);
        var dying = player(h, "payoff_old");
        erased(server, true);
        try {
            kill(dying);
        } finally {
            erased(server, false);
            PayoffState.setEligibleForTest(server, true);
        }
        h.assertTrue(TerminusLedger.turned(server), "The Terminus still turns");
        h.assertFalse(PayoffState.fired(server), "The payoff does not fire");
        h.assertFalse(holds(dying), "Nobody gets the pages");
        h.assertTrue(PayoffState.cueFor(dying) == PayoffState.RespawnCue.BOOT, "And the respawn is a boot");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "payoff")
    public static void theLineComesBackAfterARelog(GameTestHelper h) {
        var server = fresh(h);
        var a = player(h, "payoff_relog_a");
        var b = player(h, "payoff_relog_b");
        erased(server, true);
        try {
            kill(a);
            kill(b);
        } finally {
            erased(server, false);
        }
        h.assertTrue(PayoffState.deathLineOwed(a), "Still on the death screen, the firing player is owed the line");
        h.assertFalse(PayoffState.deathLineOwed(b), "Another player never is");
        a.setHealth(a.getMaxHealth());
        h.assertFalse(PayoffState.deathLineOwed(a), "Once alive again, nothing is owed");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "payoff")
    public static void onlyTheFiringPlayerGetsTheReplay(GameTestHelper h) {
        var server = fresh(h);
        var a = player(h, "payoff_replay_a");
        var b = player(h, "payoff_replay_b");
        erased(server, true);
        try {
            kill(a);
            kill(b);
        } finally {
            erased(server, false);
        }
        h.assertTrue(PayoffState.cueFor(a) == PayoffState.RespawnCue.REPLAY, "The firing player's next respawn replays");
        h.assertTrue(PayoffState.cueFor(b) == PayoffState.RespawnCue.BOOT, "Anyone else boots");
        h.assertTrue(PayoffState.cueOnRespawn(a, false).orElse(null) == PayoffState.RespawnCue.REPLAY, "The respawn sends the replay");
        h.assertTrue(PayoffState.cueFor(a) == PayoffState.RespawnCue.BOOT, "Once: every later respawn boots");
        h.assertTrue(PayoffState.cueOnRespawn(b, false).orElse(null) == PayoffState.RespawnCue.BOOT, "Still a boot for anyone else");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "payoff")
    public static void leavingTheEndIsNotARespawn(GameTestHelper h) {
        var server = fresh(h);
        var a = player(h, "payoff_end");
        erased(server, true);
        try {
            kill(a);
        } finally {
            erased(server, false);
        }
        h.assertTrue(PayoffState.cueOnRespawn(a, true).isEmpty(), "Leaving the End sends no cue");
        h.assertTrue(PayoffState.cueFor(a) == PayoffState.RespawnCue.REPLAY, "And keeps the replay for the real respawn");
        h.succeed();
    }

    /** The replay holds its player still for all of it, so nothing may hurt them meanwhile (branch review). */
    @GameTest(template = GameTestTemplates.EMPTY, batch = "payoff", timeoutTicks = 520)
    public static void theReplayShieldsItsPlayerUntilItEnds(GameTestHelper h) {
        var server = fresh(h);
        erased(server, true);
        var a = player(h, "payoff_shielded");
        try {
            kill(a);
        } finally {
            erased(server, false);
        }
        // The respawned player is a new entity with the same profile.
        var reborn = player(h, a.getGameProfile());
        PayoffState.onRespawn(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(reborn, false));
        h.runAfterDelay(400, () -> {
            reborn.hurt(h.getLevel().damageSources().freeze(), 4.0F);
            h.assertTrue(reborn.getHealth() == reborn.getMaxHealth(), "Unhurt near the end of the replay");
        });
        h.runAfterDelay(460, () -> {
            reborn.hurt(h.getLevel().damageSources().freeze(), 4.0F);
            h.assertTrue(reborn.getHealth() < reborn.getMaxHealth(), "Hurt again once it ends");
            h.succeed();
        });
    }

    /** A new world before the Heart falls. */
    static MinecraftServer fresh(GameTestHelper h) {
        var server = h.getLevel().getServer();
        PayoffState.setForTest(server, false);
        erased(server, false);
        return server;
    }

    static void erased(MinecraftServer server, boolean erased) {
        ReturnedHearthSavedData.get(server).setMaeveErasedForDebug(erased, server.overworld().getGameTime());
    }

    static void kill(ServerPlayer player) {
        player.hurt(player.level().damageSources().genericKill(), Float.MAX_VALUE);
    }

    static boolean holds(ServerPlayer player) {
        var holder = player.getServer().getAdvancements().get(PayoffState.ADVANCEMENT);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** A real survival player without spawn protection: FakePlayer ignores advancements. */
    static ServerPlayer player(GameTestHelper h, String name) {
        return player(h, new GameProfile(UUID.randomUUID(), name));
    }

    static ServerPlayer player(GameTestHelper h, GameProfile profile) {
        var level = h.getLevel();
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        player.connection = new net.neoforged.neoforge.common.util.FakePlayer(level, profile).connection;
        player.setPos(h.absoluteVec(new Vec3(4.5, 2, 4.5)));
        player.setGameMode(GameType.SURVIVAL);
        GameTestPlayers.join(h, player);
        try {
            var field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Test players must lose spawn protection", e);
        }
        return player;
    }
}
