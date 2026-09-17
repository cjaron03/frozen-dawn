package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.architect.ArchitectApproachRecovery;
import com.frozendawn.entity.architect.ArchitectApproachState;
import com.frozendawn.init.ModEntities;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side integration, not network login emulation. Players join/leave the real level list.
 * All assertions and cleanup run in one server callback so players cannot leak into other arenas.
 * Reflection observes private production selection/state without adding a gameplay test API. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArchitectRoamingIntegrationGameTest {
    @GameTest(template = "lab/scaffold_ascent", timeoutTicks = 40)
    public static void roamingCommitmentHonorsApproachCooldown(GameTestHelper helper) {
        var actor = actor(helper);
        Vec3 origin = actor.position();
        var first = join(helper.getLevel(), UUID.randomUUID(), "fd_roam_first", origin.add(2, 0, 0));
        var second = join(helper.getLevel(), UUID.randomUUID(), "fd_roam_second", origin.add(4, 0, 0));
        try {
            helper.assertTrue(select(actor) == first, "Initially commit to the nearer equal-armored player");
            ArchitectApproachRecovery.abandon(state(actor), first.getUUID(), actor.tickCount);
            helper.assertTrue(select(actor) == second,
                    "Suppressed commitment must not hide another reachable player");
            actor.setLastHurtByMob(first);
            helper.assertTrue(select(actor) == second,
                    "Remembered retaliation must not bypass the unreachable-target cooldown");
            actor.setLastHurtByMob(null);
            actor.tickCount += ArchitectApproachRecovery.TARGET_RETRY_COOLDOWN_TICKS;
            helper.assertTrue(select(actor) == second,
                    "Expired suppression must not reclaim a distance bookmark it never earned");
            second.setGameMode(GameType.CREATIVE);
            helper.assertTrue(select(actor) == first, "Expired target becomes eligible when the replacement leaves");
            helper.succeed();
        } finally {
            first.discard();
            second.discard();
        }
    }

    @GameTest(template = "lab/scaffold_ascent", timeoutTicks = 40)
    public static void roamingCommitmentHandlesPlayerLifecycle(GameTestHelper helper) {
        var actor = actor(helper);
        Vec3 origin = actor.position();
        UUID firstId = UUID.randomUUID();
        var first = join(helper.getLevel(), firstId, "fd_roam_first", origin.add(2, 0, 0));
        var second = join(helper.getLevel(), UUID.randomUUID(), "fd_roam_second", origin.add(4, 0, 0));
        FakePlayer rejoined = null;
        try {
            helper.assertTrue(select(actor) == first, "Establish the initial commitment");
            for (int tick = 0; tick < 120; tick++) {
                first.setPos(origin.add(tick % 2 == 0 ? 5 : 2, 0, 0));
                second.setPos(origin.add(tick % 2 == 0 ? 2 : 5, 0, 0));
                helper.assertTrue(select(actor) == first, "Distance swaps must preserve commitment");
            }
            first.setPos(origin.add(200, 0, 0));
            helper.assertTrue(select(actor) == second, "Out-of-range target permits replacement");
            first.setPos(origin.add(2, 0, 0));
            helper.assertTrue(select(actor) == first, "A genuine distance bookmark reclaims commitment");
            first.discard();
            helper.assertTrue(select(actor) == second, "Disconnect selects the remaining player");
            rejoined = join(helper.getLevel(), firstId, "fd_roam_first", origin.add(1, 0, 0));
            helper.assertTrue(select(actor) == second, "Same-UUID reconnect must not revive a discarded bookmark");
            second.setHealth(0);
            helper.assertTrue(select(actor) == rejoined, "Target death selects the other living player");
            second.setHealth(20);
            helper.assertTrue(select(actor) == rejoined, "Reviving a player does not steal the commitment");
            rejoined.setGameMode(GameType.CREATIVE);
            helper.assertTrue(select(actor) == second, "Creative players are ineligible");
            rejoined.setGameMode(GameType.SURVIVAL);
            helper.assertTrue(select(actor) == second, "Returning to Survival does not restore a creative bookmark");
            second.setGameMode(GameType.SPECTATOR);
            helper.assertTrue(select(actor) == rejoined, "Spectators are ineligible");
            helper.succeed();
        } finally {
            first.discard();
            second.discard();
            if (rejoined != null) rejoined.discard();
        }
    }

    @GameTest(template = "lab/scaffold_ascent", timeoutTicks = 40)
    public static void roamingDisconnectCannotResumeDisplacedScaffold(GameTestHelper helper) {
        // Use the actual scaffold fixture for local placement/collision assertions.
        var actor = ModEntities.ARCHITECT.get().create(helper.getLevel());
        BlockPos feet = helper.absolutePos(new BlockPos(12, 4, 10));
        actor.setPos(Vec3.atBottomCenterOf(feet));
        actor.setOnGround(true);
        helper.assertTrue(actor.placeScaffoldIce(feet.below(2)), "First owned scaffold support");
        helper.assertTrue(actor.placeScaffoldIce(feet.below()), "Second owned scaffold support");
        var state = state(actor);
        state.scaffoldTarget = feet.above();
        state.scaffoldDelay = 8;
        UUID guestId = UUID.randomUUID();
        var guest = join(helper.getLevel(), guestId, "fd_roam_guest", actor.position().add(2, 0, 0));
        var host = join(helper.getLevel(), UUID.randomUUID(), "fd_roam_host", actor.position().add(4, 0, 0));
        FakePlayer rejoined = null;
        try {
            helper.assertTrue(select(actor) == guest, "Commit to guest while a scaffold lift is pending");
            guest.discard();
            helper.assertTrue(select(actor) == host, "A mid-scaffold disconnect selects the remaining host");
            Vec3 displaced = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(16, 2, 4)));
            actor.setPos(displaced);
            var breaker = new ArchitectBlockBreaker(actor, pos -> {});
            ArchitectApproachMovementSupport.tickPendingScaffold(actor, state, breaker);
            helper.assertTrue(state.scaffoldTarget == null, "Old lift is cancelled after displacement");
            rejoined = join(helper.getLevel(), guestId, "fd_roam_guest", displaced.add(1, 0, 0));
            helper.assertTrue(select(actor) == host, "Rejoining guest does not steal an equal-armored host's commitment");
            for (int tick = 0; tick < ArchitectEntity.SCAFFOLD_PLACE_TICKS; tick++) {
                ArchitectApproachMovementSupport.tickPendingScaffold(actor, state, breaker);
            }
            helper.assertTrue(actor.position().equals(displaced), "No remote lift after disconnect/rejoin");
            helper.assertTrue(helper.getLevel().getBlockState(feet).isAir(), "No remote scaffold placement");
            helper.assertTrue(actor.getScaffoldIceCount() == 2, "Existing scaffold ownership survives");
            helper.succeed();
        } finally {
            guest.discard();
            host.discard();
            if (rejoined != null) rejoined.discard();
        }
    }

    private static ArchitectEntity actor(GameTestHelper helper) {
        var actor = ModEntities.ARCHITECT.get().create(helper.getLevel());
        // Selection-only cases are isolated from the concurrent Hearth players and villagers.
        actor.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4096, 2, 4096))));
        return actor;
    }

    private static FakePlayer join(ServerLevel level, UUID id, String name, Vec3 pos) {
        var player = new FakePlayer(level, new GameProfile(id, name));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(pos);
        level.addNewPlayer(player);
        return player;
    }

    private static LivingEntity select(ArchitectEntity actor) {
        try {
            Method method = ArchitectEntity.class.getDeclaredMethod("findTarget");
            method.setAccessible(true);
            return (LivingEntity) method.invoke(actor);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot invoke production target selection", error);
        }
    }

    private static ArchitectApproachState state(ArchitectEntity actor) {
        try {
            Field field = ArchitectEntity.class.getDeclaredField("approachState");
            field.setAccessible(true);
            return (ArchitectApproachState) field.get(actor);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot inspect production approach state", error);
        }
    }
}
