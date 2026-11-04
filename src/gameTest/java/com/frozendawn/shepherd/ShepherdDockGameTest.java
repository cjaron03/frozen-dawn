package com.frozendawn.shepherd;

import static com.frozendawn.shepherd.ShepherdDockBlock.STATE;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.shepherd.ShepherdDockBlock.DockState;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdDockGameTest {
    private static final BlockPos DOCK = new BlockPos(3, 3, 3);

    @BeforeBatch(batch = "shepherd_dock_wake") public static void wakeBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_dock_wake") public static void wakeAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_dock_refuse") public static void refuseBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_dock_refuse") public static void refuseAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_dock_dark") public static void darkBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_dock_dark") public static void darkAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_dock_broken") public static void brokenBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_dock_broken") public static void brokenAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static BlockPos dock(GameTestHelper h, BlockPos at) {
        h.setBlock(at, ModBlocks.CS1_DOCK.get());
        return h.absolutePos(at);
    }

    private static FakePlayer holding(GameTestHelper h, BlockPos dock, int joules) {
        var player = ShepherdTestWorld.player(h.getLevel(), dock.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, PowerCellItem.charged(joules));
        return player;
    }

    private static void use(GameTestHelper h, BlockPos dock, FakePlayer player) {
        var level = h.getLevel();
        var hit = new BlockHitResult(dock.getCenter(), Direction.UP, dock, false);
        level.getBlockState(dock).useItemOn(player.getItemInHand(InteractionHand.MAIN_HAND), level, player, InteractionHand.MAIN_HAND, hit);
    }

    private static DockState state(GameTestHelper h, BlockPos dock) {
        return h.getLevel().getBlockState(dock).getValue(STATE);
    }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_dock_wake", timeoutTicks = 400)
    public static void shepherdChargedCellWakesAndIsSpent(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h, DOCK);
            h.assertTrue(state(h, dock) == DockState.FOUND, "A dock in a world where she sleeps is found");
            var player = holding(h, dock, 1000);
            use(h, dock, player);
            h.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "The cell is gone");
            h.assertTrue(data(h).stage() == Stage.AWAKE, "She is awake");
            h.assertTrue(state(h, dock) == DockState.BOOT, "The light goes green");
            // Left booting, the dock would build her in the next batch's world.
            h.succeedWhen(() -> h.assertTrue(data(h).entity() != null, "She lifts off"));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_dock_refuse", timeoutTicks = 400)
    public static void shepherdEmptyCellIsRefused(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h, DOCK);
            var player = holding(h, dock, 0);
            use(h, dock, player);
            ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
            h.assertTrue(!held.isEmpty(), "An empty cell stays in hand");
            h.assertTrue(data(h).stage() == Stage.DORMANT, "She sleeps on");
            h.assertTrue(state(h, dock) == DockState.FOUND, "The dock does not change");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_dock_dark", timeoutTicks = 400)
    public static void shepherdSecondDockStaysDark(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            data(h).stage(Stage.AWAKE);
            var dock = dock(h, DOCK);
            var player = holding(h, dock, 1000);
            use(h, dock, player);
            h.assertTrue(!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "A second dock takes no cell");
            h.succeedWhen(() -> h.assertTrue(state(h, dock) == DockState.DARK, "A dock placed while she is awake goes dark"));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_dock_broken", timeoutTicks = 400)
    public static void shepherdBrokenDormantDockLeavesHerDormant(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var first = dock(h, DOCK);
            h.setBlock(DOCK, Blocks.AIR);
            h.assertTrue(data(h).stage() == Stage.DORMANT, "Breaking a dock wakes nothing");
            var dock = dock(h, DOCK.east(2));
            h.assertTrue(state(h, dock) == DockState.FOUND && !dock.equals(first), "The next dock is found");
            var player = holding(h, dock, 1000);
            use(h, dock, player);
            h.assertTrue(data(h).stage() == Stage.AWAKE, "And it wakes her");
            h.succeed();
        });
    }
}
