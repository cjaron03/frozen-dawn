package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.defense.Terminus;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.shepherd.ShepherdDockBlock.DockState;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.vault.VaultWatch;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdEntityGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);

    @BeforeBatch(batch = "shepherd_lethal") public static void lethalBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_lethal") public static void lethalAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_kill") public static void killBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_kill") public static void killAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_void") public static void voidBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_void") public static void voidAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_reboot") public static void rebootBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_reboot") public static void rebootAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_follow") public static void followBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_follow") public static void followAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_wait") public static void waitBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_wait") public static void waitAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_discard") public static void discardBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_discard") public static void discardAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_stray") public static void strayBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_stray") public static void strayAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_watcher") public static void watcherBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_watcher") public static void watcherAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_terminus") public static void terminusBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_terminus") public static void terminusAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_liftoff") public static void liftoffBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_liftoff") public static void liftoffAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_broken") public static void bootBrokenBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_broken") public static void bootBrokenAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_lamp") public static void lampBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_lamp") public static void lampAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    /** She needs a follower to spawn from; this one is only a name, and is not on the player list. */
    private static ShepherdEntity awake(GameTestHelper h, ServerPlayer waker) {
        data(h).stage(Stage.AWAKE);
        return ShepherdEntity.spawnFrom(h.getLevel(), h.absolutePos(SPOT), waker.getUUID());
    }

    private static ShepherdEntity awake(GameTestHelper h) {
        return awake(h, ShepherdTestWorld.player(h.getLevel(), h.absolutePos(SPOT)));
    }

    private static int bodies(GameTestHelper h) {
        return h.getLevel().getEntities(ModEntities.CS1_UNIT.get(), e -> true).size();
    }

    private static void assertOfflineNotDead(GameTestHelper h, ShepherdEntity she, UUID id) {
        h.assertTrue(she.isAlive(), "She is still alive");
        h.assertTrue(she.offline(), "She went offline");
        h.assertTrue(!she.isNoGravity(), "She drops");
        h.assertTrue(she.getUUID().equals(id) && h.getLevel().getEntity(id) == she, "It is the same body");
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_lethal", timeoutTicks = 100)
    public static void shepherdLethalDamageGoesOfflineNotDead(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var she = awake(h);
            var id = she.getUUID();
            h.assertTrue(she.getType().is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES), "The world's cold is not what stops her");
            she.hurt(h.getLevel().damageSources().generic(), 1000);
            assertOfflineNotDead(h, she, id);
            h.assertTrue(!she.hurt(h.getLevel().damageSources().generic(), 1000), "Offline ignores all damage");
            h.runAfterDelay(5, () -> {
                assertOfflineNotDead(h, she, id);
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_kill", timeoutTicks = 100)
    public static void shepherdKillCommandGoesOfflineNotDead(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var she = awake(h);
            var id = she.getUUID();
            var server = h.getLevel().getServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "kill @e[type=frozendawn:cs1_unit]");
            assertOfflineNotDead(h, she, id);
            h.runAfterDelay(5, () -> {
                assertOfflineNotDead(h, she, id);
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_void", timeoutTicks = 100)
    public static void shepherdVoidReturnsHerToHerFollower(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var player = ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT.east(4))));
            var she = awake(h, player);
            var id = she.getUUID();
            she.setPos(she.getX(), h.getLevel().getMinBuildHeight() - 70, she.getZ());
            h.succeedWhen(() -> {
                h.assertTrue(she.distanceTo(player) < 5, "Back beside her follower: " + she.position());
                assertOfflineNotDead(h, she, id);
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_reboot", timeoutTicks = 200)
    public static void shepherdRebootsAfterTwoMinutes(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var she = awake(h);
            she.hurt(h.getLevel().damageSources().generic(), 1000);
            h.assertTrue(she.rebootTicks() == ShepherdPolicy.REBOOT_TICKS, "Two minutes: " + she.rebootTicks());
            she.rebootIn(40);
            h.runAfterDelay(20, () -> h.assertTrue(she.offline(), "Still down at one second"));
            h.succeedWhen(() -> {
                h.assertTrue(!she.offline(), "Waiting for the reboot");
                h.assertTrue(she.getHealth() == ShepherdPolicy.MAX_HEALTH, "Full health: " + she.getHealth());
                h.assertTrue(she.isNoGravity(), "Flying again");
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_follow", timeoutTicks = 100)
    public static void shepherdFollowsPastTheTeleportLine(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var player = ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(30, 0, 0));
            var she = awake(h, player);
            h.runAfterDelay(25, () -> {
                h.assertTrue(she.distanceTo(player) < 5, "She closed the gap: " + she.distanceTo(player));
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_wait", timeoutTicks = 100)
    public static void shepherdWaitHereHolds(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var player = ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(8, 0, 0));
            var she = awake(h, player);
            data(h).waiting(true);
            Vec3 at = she.position();
            h.runAfterDelay(40, () -> {
                h.assertTrue(she.position().distanceTo(at) < 0.1, "She held: " + she.position());
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_discard", timeoutTicks = 100)
    public static void shepherdADiscardedBodyComesBack(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var she = awake(h);
            var last = she.blockPosition();
            data(h).lastPos(last);
            she.discard();
            h.succeedWhen(() -> {
                h.assertTrue(bodies(h) == 1, "Exactly one body: " + bodies(h));
                var back = h.getLevel().getEntity(data(h).entity());
                h.assertTrue(back instanceof ShepherdEntity && back.blockPosition().closerThan(last, 3), "She is back where she was");
            });
        });
    }

    /** A chunk that loads late, or a /summon, can hand the world a body that is not hers. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_stray", timeoutTicks = 100)
    public static void shepherdAStrayBodyRemovesItself(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var she = awake(h);
            var stray = ModEntities.CS1_UNIT.get().create(h.getLevel());
            stray.moveTo(she.position());
            h.getLevel().addFreshEntity(stray);
            h.runAfterDelay(5, () -> {
                h.assertTrue(stray.isRemoved() && !she.isRemoved() && bodies(h) == 1, "Only the saved body stays: " + bodies(h));
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_watcher", timeoutTicks = 100)
    public static void shepherdIsNotAWatcher(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var she = awake(h);
            var target = she.getEyePosition().add(0, 0, 6);
            she.lookAt(EntityAnchorArgument.Anchor.EYES, target);
            h.assertTrue(!VaultWatch.watched(h.getLevel(), pos -> false, target), "A door does not count her");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_terminus", timeoutTicks = 100)
    public static void shepherdTerminusLeavesHerAlone(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var she = awake(h);
            var op = ShepherdTestWorld.player(h.getLevel(), h.absolutePos(SPOT));
            op.setPos(she.getX(), she.getY() - 1, she.getZ() - 6);
            op.setYRot(0);
            op.setXRot(0);
            var wand = new ItemStack(ModItems.TERMINUS.get());
            op.setItemInHand(InteractionHand.MAIN_HAND, wand);
            Terminus.discharge(op, wand, InteractionHand.MAIN_HAND);
            h.assertTrue(she.isAlive() && !she.offline() && she.getHealth() == ShepherdPolicy.MAX_HEALTH, "The wand does nothing to her");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_liftoff", timeoutTicks = 100)
    public static void shepherdLiftsOffTheDockAfterBoot(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var dock = wakeDock(h);
            h.assertTrue(bodies(h) == 0, "She stays on the dock for the recording");
            h.succeedWhen(() -> {
                h.assertTrue(bodies(h) == 1, "Waiting for her to lift off");
                h.assertTrue(h.getLevel().getBlockState(dock).getValue(ShepherdDockBlock.STATE) == DockState.EMPTY, "The dock is bare");
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_broken", timeoutTicks = 100)
    public static void shepherdBreakingABootingDockStillBringsHerUp(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var dock = wakeDock(h);
            h.getLevel().setBlockAndUpdate(dock, Blocks.AIR.defaultBlockState());
            h.succeedWhen(() -> h.assertTrue(bodies(h) == 1, "She is awake, so there has to be a body: " + bodies(h)));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_lamp", timeoutTicks = 100)
    public static void shepherdLampLightsWhereSheIsUntilSheGoesOffline(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var she = awake(h);
            h.runAfterDelay(3, () -> {
                var pos = she.blockPosition();
                h.assertTrue(h.getLevel().getBlockState(pos).is(ModBlocks.HELD_LANTERN_LIGHT), "Her lamp is lit");
                she.hurt(h.getLevel().damageSources().generic(), 1000);
                h.runAfterDelay(3, () -> {
                    h.assertTrue(!h.getLevel().getBlockState(pos).is(ModBlocks.HELD_LANTERN_LIGHT), "Offline, the lamp is out");
                    h.succeed();
                });
            });
        });
    }

    private static BlockPos wakeDock(GameTestHelper h) {
        h.setBlock(SPOT, ModBlocks.CS1_DOCK.get());
        var dock = h.absolutePos(SPOT);
        var player = ShepherdTestWorld.player(h.getLevel(), dock.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, PowerCellItem.charged(1000));
        var hit = new BlockHitResult(dock.getCenter(), Direction.UP, dock, false);
        h.getLevel().getBlockState(dock).useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        return dock;
    }
}
