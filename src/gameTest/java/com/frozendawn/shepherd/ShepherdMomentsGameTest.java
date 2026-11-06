package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModEntities;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.network.ShepherdLinePayload;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.world.HeaterRegistry;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdMomentsGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);
    private static final int FRIENDLY = 300, CLOSE = 900;
    /** Facing along +x, which is away from her, and along -x, which is at her. */
    private static final float AWAY = -90, AT_HER = 90;

    @BeforeBatch(batch = "shepherd_moment_burn") public static void burnBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_moment_burn") public static void burnAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_moment_otherway") public static void otherBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_moment_otherway") public static void otherAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_moment_calm") public static void calmBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_moment_calm") public static void calmAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_moment_day") public static void dayBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_moment_day") public static void dayAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_moment_follower") public static void followerBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_moment_follower") public static void followerAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_moment_gag") public static void gagBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_moment_gag") public static void gagAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_moment_clovis") public static void clovisBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_moment_clovis") public static void clovisAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    private static ServerPlayer player(GameTestHelper h, float facing) {
        var player = ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(4, 0, 0));
        face(player, facing);
        return player;
    }

    private static void face(ServerPlayer player, float yaw) {
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(0);
    }

    private static ShepherdEntity awake(GameTestHelper h, ServerPlayer player, int affinity) {
        data(h).stage(Stage.AWAKE);
        data(h).affinity(affinity);
        data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
        MillerPattern.setCoherence(h.getLevel().getServer(), 1);
        return ShepherdEntity.spawnFrom(h.getLevel(), h.absolutePos(SPOT), player.getUUID());
    }

    private static void look(GameTestHelper h, ShepherdEntity she, ServerPlayer... near) {
        ShepherdEggs.look(h.getLevel(), she, List.of(near));
    }

    private static Zombie hostile(GameTestHelper h, ServerPlayer player) {
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        zombie.moveTo(player.position().add(3, 0, 0));
        h.getLevel().addFreshEntity(zombie);
        return zombie;
    }

    private static void killHunter(GameTestHelper h, ServerPlayer player, net.minecraft.world.damagesource.DamageSource how) {
        var hunter = ModEntities.RETURNED.get().create(h.getLevel());
        hunter.moveTo(player.position().add(2, 0, 0));
        h.getLevel().addFreshEntity(hunter);
        hunter.hurt(how, 1000);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_moment_burn", timeoutTicks = 400)
    public static void shepherdReactsOnceToAHunterAtTheHeater(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h, AWAY);
            var she = awake(h, player, FRIENDLY);
            var heater = BlockPos.containing(player.position()).offset(3, 0, 0);
            var fire = h.getLevel().damageSources().onFire();
            HeaterRegistry.register(h.getLevel(), heater);
            try {
                killHunter(h, player, fire);
                h.assertTrue(ShepherdEggs.HUNTER_BURN_LINE.equals(ShepherdReactions.said()), "She saw it: " + ShepherdReactions.said());
                h.assertTrue(data(h).flag(ShepherdEggs.HUNTER_BURN), "Once");
                ShepherdReactions.clearForTests();
                data(h).lastAmbient(ShepherdSavedData.NOT_SET);
                killHunter(h, player, fire);
                h.assertTrue(ShepherdReactions.said() == null, "Not twice: " + ShepherdReactions.said());
            } finally {
                HeaterRegistry.unregister(h.getLevel(), heater);
            }
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_moment_otherway", timeoutTicks = 400)
    public static void shepherdIgnoresAHunterKilledAnotherWay(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h, AWAY);
            awake(h, player, FRIENDLY);
            var heater = BlockPos.containing(player.position()).offset(3, 0, 0);
            killHunter(h, player, h.getLevel().damageSources().onFire());
            h.assertTrue(ShepherdReactions.said() == null, "Fire with no heater near is not her trick: " + ShepherdReactions.said());
            HeaterRegistry.register(h.getLevel(), heater);
            try {
                killHunter(h, player, h.getLevel().damageSources().generic());
                h.assertTrue(ShepherdReactions.said() == null, "Nor is a sword beside one: " + ShepherdReactions.said());
            } finally {
                HeaterRegistry.unregister(h.getLevel(), heater);
            }
            h.assertTrue(!data(h).flag(ShepherdEggs.HUNTER_BURN), "Still unspent");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_moment_calm", timeoutTicks = 400)
    public static void shepherdGetsCaughtOnlyWhenCalm(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h, AWAY);
            var she = awake(h, player, FRIENDLY);
            var zombie = hostile(h, player);
            look(h, she, player);
            h.assertTrue(!ShepherdProjection.active(), "Not with something near");
            zombie.discard();
            face(player, AT_HER);
            look(h, she, player);
            h.assertTrue(!ShepherdProjection.active(), "Not with someone looking");
            face(player, AWAY);
            look(h, she, player);
            h.assertTrue(she.projection() == ShepherdProjection.PAIR, "Calm, with nobody looking: " + she.projection());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_moment_day", timeoutTicks = 400)
    public static void shepherdGetsCaughtOnceADay(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var server = h.getLevel().getServer();
            var player = player(h, AWAY);
            var she = awake(h, player, FRIENDLY);
            look(h, she, player);
            ShepherdProjection.tick(server);
            h.assertTrue(ShepherdProjection.active(), "Nobody saw");
            face(player, AT_HER);
            ShepherdProjection.tick(server);
            h.assertTrue(!ShepherdProjection.active() && she.projection() == 0, "It snaps off");
            h.assertTrue(she.flustered(), "She spins away");
            h.assertTrue(ShepherdEggs.CAUGHT_LINE.equals(ShepherdReactions.said()), "And says so: " + ShepherdReactions.said());
            face(player, AWAY);
            look(h, she, player);
            h.assertTrue(!ShepherdProjection.active(), "Not again today");
            long now = server.overworld().getGameTime();
            h.assertTrue(data(h).deedReady(ShepherdEggs.CAUGHT) >= now + ShepherdPolicy.DAY_TICKS - 2, "Not for a day: " + data(h).deedReady(ShepherdEggs.CAUGHT));
            data(h).deedReady(ShepherdEggs.CAUGHT, now);
            look(h, she, player);
            h.assertTrue(ShepherdProjection.active(), "The next day she tries again");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_moment_follower", timeoutTicks = 400)
    public static void shepherdCaughtShowsHerFollower(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var follower = player(h, AWAY);
            var other = player(h, AWAY);
            var she = awake(h, follower, FRIENDLY);
            look(h, she, follower, other);
            h.assertTrue(Optional.of(follower.getUUID()).equals(she.projectedPlayer()), "The small you is her follower: " + she.projectedPlayer());
            face(other, AT_HER);
            ShepherdProjection.tick(h.getLevel().getServer());
            h.assertTrue(she.flustered(), "Anyone can catch her");
            h.assertTrue(she.projectedPlayer().isEmpty(), "And it leaves nothing behind: " + she.projectedPlayer());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_moment_gag", timeoutTicks = 400)
    public static void shepherdTheGagWaitsForCalm(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var server = h.getLevel().getServer();
            var player = player(h, AT_HER);
            var she = awake(h, player, FRIENDLY);
            look(h, she, player);
            h.assertTrue(!ShepherdProjection.active(), "Only at Close");
            data(h).affinity(CLOSE);
            var zombie = hostile(h, player);
            look(h, she, player);
            h.assertTrue(!ShepherdProjection.active(), "Not with something near");
            zombie.discard();
            look(h, she, player);
            h.assertTrue(she.projection() == ShepherdProjection.ITEM, "Calm: " + she.projection());
            for (int i = 0; i < ShepherdProjection.BEAM_TICKS; i++) ShepherdProjection.tick(server);
            h.assertTrue(List.of(ShepherdEggs.GAG_LINE).equals(ShepherdProjection.spoken().stream().map(ShepherdLinePayload::key).toList()), "Cargo! " + ShepherdProjection.spoken());
            ShepherdProjection.stop();
            long now = server.overworld().getGameTime();
            look(h, she, player);
            h.assertTrue(!ShepherdProjection.active(), "Every few days at most");
            h.assertTrue(data(h).deedReady(ShepherdEggs.GAG) >= now + ShepherdPolicy.GAG_EVERY_TICKS - 2, "Spaced: " + data(h).deedReady(ShepherdEggs.GAG));
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_moment_clovis", timeoutTicks = 400)
    public static void shepherdNamesClovisOnlyAfterT5(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h, AT_HER);
            var she = awake(h, player, FRIENDLY);
            look(h, she, player);
            h.assertTrue(ShepherdReactions.said() == null, "Not before T5: " + ShepherdReactions.said());
            data(h).flag("t5_done", true);
            look(h, she, player);
            h.assertTrue(ShepherdEggs.CLOVIS_LINE.equals(ShepherdReactions.said()), "After it: " + ShepherdReactions.said());
            h.assertTrue(data(h).flag(ShepherdEggs.CLOVIS), "Once a world");
            ShepherdReactions.clearForTests();
            data(h).lastAmbient(ShepherdSavedData.NOT_SET);
            look(h, she, player);
            h.assertTrue(ShepherdReactions.said() == null, "Not twice: " + ShepherdReactions.said());
            h.succeed();
        });
    }
}
