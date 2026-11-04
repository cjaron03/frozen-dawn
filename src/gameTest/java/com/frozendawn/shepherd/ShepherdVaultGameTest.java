package com.frozendawn.shepherd;

import static com.frozendawn.shepherd.ShepherdDockBlock.STATE;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.HelionSiteState;
import com.frozendawn.defense.TerminusLog;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModItems;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Her place in the vault. A dock 100 blocks from a test area never ticks, so each test puts the hall where its dock lands in it. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdVaultGameTest {
    private static final BlockPos SPOT = new BlockPos(3, 3, 3);

    private static final int ASK = 0, KIND = 1, CONTINUE = -1;
    private static BlockPos worldVault;

    private static void before(ServerLevel l) {
        ShepherdTestWorld.fresh(l);
        var state = HelionSiteState.get(l.getServer());
        worldVault = state.vault();
        state.setVaultForTest(null);
    }

    private static void after(ServerLevel l) {
        ShepherdTestWorld.clear(l);
        HelionSiteState.get(l.getServer()).setVaultForTest(worldVault);
    }

    @BeforeBatch(batch = "shepherd_vault_reset") public static void resetBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_vault_reset") public static void resetAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_vault_reset_awake") public static void awakeBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_vault_reset_awake") public static void awakeAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_vault_reset_boot") public static void bootBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_vault_reset_boot") public static void bootAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_vault_empty") public static void emptyBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_vault_empty") public static void emptyAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_vault_room") public static void roomBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_vault_room") public static void roomAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_vault_creative") public static void creativeBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_vault_creative") public static void creativeAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_vault_hand") public static void handBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_vault_hand") public static void handAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_vault_used") public static void usedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_vault_used") public static void usedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_vault_cradle") public static void cradleBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_vault_cradle") public static void cradleAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_vault_esc") public static void escBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_vault_esc") public static void escAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_vault_replay") public static void replayBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_vault_replay") public static void replayAfter(ServerLevel l) { after(l); }

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

    private static ServerPlayer player(GameTestHelper h) {
        return ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
    }

    /** She is awake, met in the vault, and follows {@code player}. */
    private static ShepherdEntity metInTheVault(GameTestHelper h, ServerPlayer player) {
        data(h).stage(Stage.AWAKE);
        data(h).flag(ShepherdSavedData.WOKE_IN_VAULT, true);
        return ShepherdEntity.spawnFrom(h.getLevel(), h.absolutePos(SPOT), player.getUUID());
    }

    private static void say(ServerPlayer player, int... slots) {
        for (int slot : slots) ShepherdTalk.reply(player, ShepherdTalk.liveId(), slot);
    }

    /** Wakes her on {@code dock} and, once the first meeting opens, walks it as far as "Aurora it is". */
    private static void wakeAndNameHer(GameTestHelper h, Block dock, java.util.function.Consumer<ServerPlayer> then) {
        h.setBlock(SPOT, dock);
        var at = h.absolutePos(SPOT);
        var player = player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, PowerCellItem.charged(1000));
        h.getLevel().getBlockState(at).useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, new BlockHitResult(at.getCenter(), Direction.UP, at, false));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue("fm.boot".equals(ShepherdTalk.liveNode()), "Waiting for fm.boot: " + ShepherdTalk.liveNode()))
                .thenExecute(() -> {
                    say(player, KIND, CONTINUE, KIND, CONTINUE, KIND, KIND);
                    then.accept(player);
                })
                .thenSucceed();
    }

    private static void assertLogLine(GameTestHelper h, String key) {
        h.assertTrue("fm.room".equals(ShepherdTalk.liveNode()), "On fm.room: " + ShepherdTalk.liveNode());
        var lines = ShepherdTalk.liveLines();
        h.assertTrue(lines.size() == 2 && lines.get(1).equals(key), "The log line: " + lines);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_vault_room", timeoutTicks = 400)
    public static void shepherdVaultDockLeadsToTheRoom(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> wakeAndNameHer(h, ModBlocks.VAULT_CS1_DOCK.get(), player -> {
            h.assertTrue("fm.room".equals(ShepherdTalk.liveNode()), "After her name, him: " + ShepherdTalk.liveNode());
            say(player, ASK, CONTINUE);
            h.assertTrue("fm.end".equals(ShepherdTalk.liveNode()), "Then the rules: " + ShepherdTalk.liveNode());
            h.assertTrue(data(h).flag("asked_transfer"), "The Ask reply leaves its flag");
        }));
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_vault_creative", timeoutTicks = 400)
    public static void shepherdCreativeDockSkipsTheRoom(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> wakeAndNameHer(h, ModBlocks.CS1_DOCK.get(), player ->
                h.assertTrue("fm.end".equals(ShepherdTalk.liveNode()), "No technician here: " + ShepherdTalk.liveNode())));
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_vault_hand", timeoutTicks = 400)
    public static void shepherdTheWandInHandChangesTheLine(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            metInTheVault(h, player);
            player.getInventory().add(new ItemStack(ModItems.TERMINUS.get()));
            ShepherdTalk.start(player, "fm.room");
            assertLogLine(h, "shepherd.fm.room.log.hand");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_vault_used", timeoutTicks = 400)
    public static void shepherdAUsedWandIsNotQuoted(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            metInTheVault(h, player);
            var wand = new ItemStack(ModItems.TERMINUS.get());
            wand.set(ModDataComponents.TERMINUS_LOG.get(), new TerminusLog(2, 2));
            player.getInventory().add(wand);
            ShepherdTalk.start(player, "fm.room");
            assertLogLine(h, "shepherd.fm.room.log.other");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_vault_cradle", timeoutTicks = 400)
    public static void shepherdTheWandInTheCradleIsQuoted(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            metInTheVault(h, player);
            var cradle = new BlockPos(6, 3, 6);
            h.setBlock(cradle, ModBlocks.VAULT_CRADLE.get());
            HelionSiteState.get(h.getLevel().getServer()).setVaultForTest(h.absolutePos(cradle).subtract(TerminusVaultBuilder.CRADLE));
            ShepherdTalk.start(player, "fm.room");
            assertLogLine(h, "shepherd.fm.room.log.cradle");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_vault_esc", timeoutTicks = 400)
    public static void shepherdEscResumesTheRoom(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = metInTheVault(h, player);
            ShepherdTalk.start(player, "fm.room");
            ShepherdTalk.close(player);
            h.assertTrue(!ShepherdTalk.live(), "Esc walks away");
            she.interact(player, InteractionHand.MAIN_HAND);
            h.assertTrue("fm.room".equals(ShepherdTalk.liveNode()), "She picks him back up: " + ShepherdTalk.liveNode());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_vault_replay", timeoutTicks = 400)
    public static void shepherdReplayStillReachesTheRoom(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = metInTheVault(h, player);
            var server = h.getLevel().getServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "fd shepherd replay");
            she.interact(player, InteractionHand.MAIN_HAND);
            say(player, KIND, CONTINUE, KIND, CONTINUE, KIND, KIND);
            h.assertTrue("fm.room".equals(ShepherdTalk.liveNode()), "A replay is still a vault meeting: " + ShepherdTalk.liveNode());
            h.succeed();
        });
    }
}
