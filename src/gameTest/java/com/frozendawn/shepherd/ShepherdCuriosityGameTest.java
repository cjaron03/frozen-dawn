package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** She wanders off to look at things when you stand still. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdCuriosityGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10), CHEST = new BlockPos(15, 4, 10);

    @BeforeBatch(batch = "shepherd_curious_chest") public static void chestBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_curious_chest") public static void chestAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_curious_moving") public static void movingBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_curious_moving") public static void movingAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_curious_nothing") public static void nothingBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_curious_nothing") public static void nothingAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_curious_wall") public static void wallBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_curious_wall") public static void wallAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_curious_move") public static void moveBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_curious_move") public static void moveAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_curious_hostile") public static void hostileBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_curious_hostile") public static void hostileAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_curious_broken") public static void brokenBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_curious_broken") public static void brokenAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_curious_wait") public static void waitBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_curious_wait") public static void waitAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_curious_wary") public static void waryBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_curious_wary") public static void waryAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_curious_gap") public static void gapBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_curious_gap") public static void gapAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

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

    private static ShepherdEntity withAChest(GameTestHelper h, ServerPlayer player) {
        h.setBlock(CHEST, Blocks.CHEST);
        var she = awake(h, player);
        she.stillFor(ShepherdPolicy.STILL_TICKS);
        return she;
    }

    private static void assertLooking(GameTestHelper h, ShepherdEntity she) {
        h.assertTrue(she.lookingAt() != null, "She goes to look");
    }

    private static void assertNotLooking(GameTestHelper h, ShepherdEntity she) {
        h.assertTrue(she.lookingAt() == null, "She does not look: " + she.lookingAt());
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_curious_chest", timeoutTicks = 400)
    public static void shepherdSheGoesToLookAtAChest(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = withAChest(h, player(h));
            var chest = h.absoluteVec(Vec3.atCenterOf(CHEST));
            h.startSequence()
                    .thenWaitUntil(() -> assertLooking(h, she))
                    .thenExecute(() -> h.assertTrue(she.lookingAt().kind() == ShepherdCuriosity.Kind.STORAGE, "A chest: " + she.lookingAt().kind()))
                    .thenWaitUntil(() -> h.assertTrue(she.position().distanceTo(chest) <= 2.5, "A block short of it: " + she.position().distanceTo(chest)))
                    .thenExecute(() -> h.assertTrue(she.position().distanceTo(chest) >= 1, "Not in it"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_curious_moving", timeoutTicks = 400)
    public static void shepherdNotUntilYouStandStill(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            h.setBlock(CHEST, Blocks.CHEST);
            var she = awake(h, player(h));
            h.startSequence().thenExecuteAfter(40, () -> assertNotLooking(h, she)).thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_curious_nothing", timeoutTicks = 400)
    public static void shepherdNothingToLookAtNothingHappens(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = awake(h, player(h));
            she.stillFor(ShepherdPolicy.STILL_TICKS);
            h.startSequence().thenExecuteAfter(40, () -> assertNotLooking(h, she)).thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_curious_wall", timeoutTicks = 400)
    public static void shepherdNotThroughAWall(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            for (int y = 0; y <= 9; y++) for (int z = 2; z <= 18; z++) h.setBlock(new BlockPos(14, y, z), Blocks.STONE);
            var she = withAChest(h, player(h));
            h.startSequence().thenExecuteAfter(40, () -> assertNotLooking(h, she)).thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_curious_move", timeoutTicks = 400)
    public static void shepherdSheComesBackWhenYouMove(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = withAChest(h, player);
            h.startSequence()
                    .thenWaitUntil(() -> assertLooking(h, she))
                    .thenExecute(() -> player.teleportTo(player.getX() - 5, player.getY(), player.getZ()))
                    .thenWaitUntil(() -> assertNotLooking(h, she))
                    .thenWaitUntil(() -> h.assertTrue(she.distanceTo(player) < 4, "Back at your side: " + she.distanceTo(player)))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_curious_hostile", timeoutTicks = 400)
    public static void shepherdSheComesBackForAHostile(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = withAChest(h, player(h));
            h.startSequence()
                    .thenWaitUntil(() -> assertLooking(h, she))
                    .thenExecute(() -> h.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(5.5, 4, 10.5)))
                    .thenWaitUntil(() -> assertNotLooking(h, she))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_curious_broken", timeoutTicks = 400)
    public static void shepherdATargetBrokenMidLookEndsTheLook(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = withAChest(h, player(h));
            h.startSequence()
                    .thenWaitUntil(() -> assertLooking(h, she))
                    .thenExecute(() -> h.setBlock(CHEST, Blocks.AIR))
                    .thenWaitUntil(() -> assertNotLooking(h, she))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_curious_wait", timeoutTicks = 400)
    public static void shepherdWaitingSheStaysPut(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = withAChest(h, player(h));
            data(h).waiting(true);
            h.startSequence().thenExecuteAfter(40, () -> assertNotLooking(h, she)).thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_curious_wary", timeoutTicks = 400)
    public static void shepherdAtWarySheStaysPut(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = withAChest(h, player(h));
            data(h).affinity(-300);
            h.startSequence().thenExecuteAfter(40, () -> assertNotLooking(h, she)).thenSucceed();
        });
    }

    /** A test's clock is game time too, so its timeout has to cover the wind. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_curious_gap", timeoutTicks = 2400)
    public static void shepherdAFullGapBetweenLooks(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = withAChest(h, player(h));
            h.startSequence()
                    .thenWaitUntil(() -> assertLooking(h, she))
                    .thenWaitUntil(() -> assertNotLooking(h, she))
                    .thenExecute(() -> she.stillFor(ShepherdPolicy.STILL_TICKS))
                    .thenExecuteAfter(40, () -> {
                        assertNotLooking(h, she);
                        var level = h.getLevel();
                        ((ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + ShepherdPolicy.CURIOUS_GAP_TICKS);
                        she.stillFor(ShepherdPolicy.STILL_TICKS);
                    })
                    .thenWaitUntil(() -> assertLooking(h, she))
                    .thenSucceed();
        });
    }
}
