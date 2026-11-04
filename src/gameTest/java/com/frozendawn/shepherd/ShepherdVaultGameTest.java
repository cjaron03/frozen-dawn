package com.frozendawn.shepherd;

import static com.frozendawn.shepherd.ShepherdDockBlock.STATE;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModEntities;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.shepherd.ShepherdDockBlock.DockState;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.vault.TerminusVaultBuilder;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Her place in the vault. A dock 100 blocks from a test area never ticks, so each test puts the hall where its dock lands in it. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdVaultGameTest {
    private static final BlockPos SPOT = new BlockPos(3, 3, 3);

    @BeforeBatch(batch = "shepherd_vault_reset") public static void resetBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_vault_reset") public static void resetAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_vault_reset_awake") public static void awakeBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_vault_reset_awake") public static void awakeAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_vault_reset_boot") public static void bootBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_vault_reset_boot") public static void bootAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_vault_empty") public static void emptyBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_vault_empty") public static void emptyAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    /** The dock as the vault builds it, at the test's spot. */
    private static BlockPos dock(GameTestHelper h) {
        var at = h.absolutePos(SPOT);
        TerminusVaultBuilder.dock(h.getLevel(), at);
        return at;
    }

    private static BlockPos hall(BlockPos dock) {
        return dock.subtract(TerminusVaultBuilder.DOCK);
    }

    private static DockState state(GameTestHelper h, BlockPos dock) {
        return h.getLevel().getBlockState(dock).getValue(STATE);
    }

    private static void show(GameTestHelper h, BlockPos dock, DockState state) {
        h.getLevel().setBlockAndUpdate(dock, h.getLevel().getBlockState(dock).setValue(STATE, state));
    }

    private static FakePlayer holdingACell(GameTestHelper h, BlockPos dock) {
        var player = ShepherdTestWorld.player(h.getLevel(), dock.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, PowerCellItem.charged(1000));
        return player;
    }

    private static void useCell(GameTestHelper h, BlockPos dock, FakePlayer player) {
        var level = h.getLevel();
        var hit = new BlockHitResult(dock.getCenter(), Direction.UP, dock, false);
        level.getBlockState(dock).useItemOn(player.getItemInHand(InteractionHand.MAIN_HAND), level, player, InteractionHand.MAIN_HAND, hit);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_vault_reset", timeoutTicks = 400)
    public static void shepherdVaultResetPutsTheDockBack(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h);
            show(h, dock, DockState.EMPTY);
            TerminusVaultBuilder.reset(h.getLevel(), hall(dock));
            h.assertTrue(state(h, dock) == DockState.FOUND, "She sleeps, so the dock is found again");
            h.assertTrue(h.getLevel().getBlockState(dock).is(ModBlocks.VAULT_CS1_DOCK.get()), "Still the sealed one");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_vault_reset_awake", timeoutTicks = 400)
    public static void shepherdVaultResetLeavesHerSavedStateAlone(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h);
            var data = data(h);
            var follower = UUID.randomUUID();
            data.stage(Stage.AWAKE);
            data.affinity(40);
            data.flag("asked_transfer", true);
            data.follower(follower);
            show(h, dock, DockState.EMPTY);
            TerminusVaultBuilder.reset(h.getLevel(), hall(dock));
            h.assertTrue(state(h, dock) == DockState.DARK, "She cannot be on the dock and awake");
            h.assertTrue(data.stage() == Stage.AWAKE, "Still awake");
            h.assertTrue(data.affinity() == 40, "Her score stands");
            h.assertTrue(data.flag("asked_transfer"), "Her flags stand");
            h.assertTrue(follower.equals(data.follower()), "So does her follower");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_vault_reset_boot", timeoutTicks = 400)
    public static void shepherdVaultResetDuringBootStillBringsHerUp(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h);
            useCell(h, dock, holdingACell(h, dock));
            h.assertTrue(state(h, dock) == DockState.BOOT, "The light goes green");
            TerminusVaultBuilder.reset(h.getLevel(), hall(dock));
            h.succeedWhen(() -> {
                h.assertTrue(h.getLevel().getEntities(ModEntities.CS1_UNIT.get(), e -> true).size() == 1, "One of her");
                h.assertTrue(state(h, dock) == DockState.EMPTY, "The dock she left");
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_vault_empty", timeoutTicks = 400)
    public static void shepherdEmptyVaultDockRefusesACell(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h);
            data(h).stage(Stage.AWAKE);
            show(h, dock, DockState.EMPTY);
            var player = holdingACell(h, dock);
            useCell(h, dock, player);
            h.assertTrue(!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "Dressing takes no cell");
            h.assertTrue(state(h, dock) == DockState.EMPTY, "And does not change");
            h.succeed();
        });
    }
}
