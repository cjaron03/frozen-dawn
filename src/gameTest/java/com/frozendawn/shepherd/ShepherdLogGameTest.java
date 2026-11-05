package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.event.PlayerTickHandler;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.miller.LogEntry;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Her private log, reached the way the game reaches it. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdLogGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);
    private static final int A_POLL = ShepherdReactions.POLL_TICKS + 10, TOP_Y = 112, MIDNIGHT = 18000;
    private static final float STARS = 0.8F;
    private static final String NOTE = "shepherd.log.note.";

    private static long apocalypseTicks;

    @BeforeBatch(batch = "shepherd_log_day") public static void dayBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_log_day") public static void dayAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_log_cold") public static void coldBefore(ServerLevel l) {
        ShepherdTestWorld.fresh(l);
        apocalypseTicks = ApocalypseState.get(l.getServer()).getApocalypseTicks();
    }
    @AfterBatch(batch = "shepherd_log_cold") public static void coldAfter(ServerLevel l) {
        ApocalypseState.get(l.getServer()).setApocalypseTicks(apocalypseTicks, l.getServer());
        l.setWeatherParameters(0, 0, false, false);
        ShepherdTestWorld.clear(l);
    }
    @BeforeBatch(batch = "shepherd_log_death") public static void deathBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_log_death") public static void deathAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_log_unseen") public static void unseenBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_log_unseen") public static void unseenAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_log_elsewhere") public static void elsewhereBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_log_elsewhere") public static void elsewhereAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_log_aloud") public static void aloudBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_log_aloud") public static void aloudAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_log_close_call") public static void closeCallBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_log_close_call") public static void closeCallAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_log_fight") public static void fightBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_log_fight") public static void fightAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    private static ServerPlayer player(GameTestHelper h) {
        var player = ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static ShepherdEntity awake(GameTestHelper h, ServerPlayer player) {
        data(h).stage(Stage.AWAKE);
        return ShepherdEntity.spawnFrom(h.getLevel(), h.absolutePos(SPOT), player.getUUID());
    }

    private static void kill(GameTestHelper h, ServerPlayer player) {
        player.hurt(h.getLevel().damageSources().genericKill(), 1000);
        h.assertTrue(player.isDeadOrDying(), "They stopped");
    }

    /** On their feet again, as respawning leaves them to anyone watching. */
    private static void revive(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
    }

    /** The day ends. Her clock moves and the next real poll settles it. */
    private static void skip(GameTestHelper h) {
        ShepherdReactions.skipDay(h.getLevel().getServer());
    }

    private static LogEntry last(GameTestHelper h) {
        var log = MillerPattern.log(h.getLevel().getServer());
        h.assertTrue(!log.isEmpty(), "She wrote the day down");
        return log.getFirst();
    }

    private static void assertNote(GameTestHelper h, String note) {
        String written = last(h).note();
        h.assertTrue(note == null ? written == null : (NOTE + note).equals(written), "Expected " + note + ", wrote " + written);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_day", timeoutTicks = 600)
    public static void shepherdADayLeavesAnEntry(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> {
                        h.assertTrue(MillerPattern.log(h.getLevel().getServer()).isEmpty(), "Nothing has settled yet");
                        skip(h);
                    })
                    .thenExecuteAfter(A_POLL, () -> {
                        h.assertTrue(MillerPattern.log(h.getLevel().getServer()).size() == 1, "One entry");
                        assertNote(h, null);
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_cold", timeoutTicks = 800, skyAccess = true)
    public static void shepherdAColdDayIsLogged(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var state = ApocalypseState.get(h.getLevel().getServer());
            state.setApocalypseTicks((long) StrictMath.ceil(STARS * state.getTotalDays()) * ShepherdPolicy.DAY_TICKS + MIDNIGHT, h.getLevel().getServer());
            var at = h.absoluteVec(Vec3.atCenterOf(SPOT));
            var player = ShepherdTestWorld.onlinePlayer(h, new Vec3(at.x + 1, TOP_Y, at.z));
            var she = awake(h, player);
            she.teleportTo(player.getX() + 1, player.getY() + 1, player.getZ());
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> h.assertTrue(PlayerTickHandler.resolvedTemperature(player) <= ShepherdPolicy.COLD_LINE_C, "Cold: " + PlayerTickHandler.resolvedTemperature(player)))
                    .thenExecuteAfter(A_POLL, () -> skip(h))
                    .thenExecuteAfter(A_POLL, () -> {
                        assertNote(h, "cold.product");
                        h.assertTrue(ShepherdReactions.said() == null, "No line played: she has not met them, and it was logged anyway");
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_death", timeoutTicks = 800)
    public static void shepherdADeathSheSawIsLogged(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> kill(h, player))
                    .thenExecuteAfter(A_POLL, () -> skip(h))
                    .thenExecuteAfter(A_POLL, () -> {
                        assertNote(h, null);
                        revive(player);
                        skip(h);
                    })
                    .thenExecuteAfter(2 * A_POLL, () -> assertNote(h, "back.product"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_unseen", timeoutTicks = 800)
    public static void shepherdADeathSheDidNotSeeLeavesNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            data(h).waiting(true);
            player.setPos(player.position().add(ShepherdPolicy.HEARD_BLOCKS + 8, 0, 0));
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> {
                        kill(h, player);
                        revive(player);
                        player.setPos(h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
                    })
                    .thenExecuteAfter(A_POLL, () -> skip(h))
                    .thenExecuteAfter(A_POLL, () -> assertNote(h, null))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_elsewhere", timeoutTicks = 800)
    public static void shepherdAReturnSheNeverSawLeavesNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var other = player(h);
            awake(h, player);
            data(h).waiting(true);
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> {
                        kill(h, player);
                        player.setPos(player.position().add(ShepherdPolicy.HEARD_BLOCKS + 8, 0, 0));
                        revive(player);
                    })
                    .thenExecuteAfter(A_POLL, () -> skip(h))
                    .thenExecuteAfter(A_POLL, () -> {
                        h.assertTrue(other.distanceTo(player) > ShepherdPolicy.HEARD_BLOCKS, "They came back somewhere else");
                        assertNote(h, null);
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_aloud", timeoutTicks = 800)
    public static void shepherdADeathSheSawChangesNothingAloud(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var server = h.getLevel().getServer();
            var player = player(h);
            awake(h, player);
            data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
            data(h).affinity(100);
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> kill(h, player))
                    .thenExecuteAfter(A_POLL, () -> revive(player))
                    .thenExecuteAfter(A_POLL, () -> {
                        var mind = MillerPattern.snapshot(server);
                        h.assertTrue(mind.strongest() == null, "No feeling: " + mind.strongest());
                        h.assertTrue(mind.history().isEmpty(), "No reason kept");
                        h.assertTrue(mind.keepsakes().getOrDefault(player.getUUID(), 0) == 0, "No keepsake");
                        h.assertTrue(data(h).affinity() == 100, "No score");
                        h.assertTrue(ShepherdReactions.said() == null, "Nothing said: " + ShepherdReactions.said());
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_close_call", timeoutTicks = 600)
    public static void shepherdACloseCallIsLogged(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            player.setHealth(6);
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> {
                        player.hurt(h.getLevel().damageSources().generic(), 2);
                        h.assertTrue(player.isAlive() && player.getHealth() <= ShepherdPolicy.CLOSE_CALL_HEALTH, "Left on two hearts: " + player.getHealth());
                        skip(h);
                    })
                    .thenExecuteAfter(A_POLL, () -> assertNote(h, "close_call.1"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_log_fight", timeoutTicks = 600)
    public static void shepherdAFightIsLogged(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            var zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(16.5, 4, 10.5));
            h.startSequence()
                    .thenExecuteAfter(A_POLL, () -> {
                        zombie.hurt(h.getLevel().damageSources().playerAttack(player), 1000);
                        skip(h);
                    })
                    .thenExecuteAfter(A_POLL, () -> assertNote(h, "fight.product"))
                    .thenSucceed();
        });
    }
}
