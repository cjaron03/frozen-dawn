package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Nine at the Blast Pit. The pit here is a floor and a position: only where the husk lands and what it does are under test. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdNineGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10), PIT = new BlockPos(10, 3, 10), NINE = PIT.offset(9, -2, 7);
    private static final int A_LOOK = 25;

    private static void before(ServerLevel l) {
        ShepherdTestWorld.fresh(l);
        ShepherdTestWorld.keepThePit(l);
    }

    private static void after(ServerLevel l) {
        ShepherdTestWorld.restoreThePit(l);
        ShepherdTestWorld.clear(l);
    }

    @BeforeBatch(batch = "shepherd_nine_placed") public static void placedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_nine_placed") public static void placedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_nine_waits") public static void waitsBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_nine_waits") public static void waitsAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_nine_floor") public static void floorBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_nine_floor") public static void floorAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_nine_sight") public static void sightBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_nine_sight") public static void sightAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_nine_one") public static void oneBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_nine_one") public static void oneAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_nine_break") public static void breakBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_nine_break") public static void breakAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_nine_give") public static void giveBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_nine_give") public static void giveAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_nine_cell") public static void cellBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_nine_cell") public static void cellAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_nine_sheet") public static void sheetBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_nine_sheet") public static void sheetAfter(ServerLevel l) { after(l); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    private static ServerPlayer player(GameTestHelper h) {
        return ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
    }

    private static ShepherdEntity met(GameTestHelper h, ServerPlayer player) {
        data(h).stage(Stage.AWAKE);
        data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
        return ShepherdEntity.spawnFrom(h.getLevel(), h.absolutePos(SPOT), player.getUUID());
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < GameTestTemplates.EMPTY_LARGE_WIDTH; x++) {
            for (int z = 0; z < GameTestTemplates.EMPTY_LARGE_WIDTH; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    /** A floor, a pit over it, and the world told she has given the ground ref. */
    private static void openPit(GameTestHelper h) {
        floor(h);
        ShepherdTestWorld.pitAt(h.getLevel(), h.absolutePos(PIT));
        data(h).flag(ShepherdNine.OPEN, true);
    }

    /** The husk, in place, without waiting on the placement. */
    private static BlockPos husk(GameTestHelper h) {
        openPit(h);
        var at = h.absolutePos(NINE);
        h.getLevel().setBlockAndUpdate(at, ModBlocks.CS1_HUSK.get().defaultBlockState());
        data(h).huskPos(at);
        return at;
    }

    private static void assertHuskAt(GameTestHelper h, BlockPos relative) {
        h.assertTrue(h.absolutePos(relative).equals(data(h).huskPos()), "At " + data(h).huskPos());
        h.assertTrue(h.getBlockState(relative).is(ModBlocks.CS1_HUSK.get()), "The block: " + h.getBlockState(relative));
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_placed", timeoutTicks = 200)
    public static void shepherdHuskIsPlacedWhenThePitLoads(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            openPit(h);
            h.startSequence()
                    .thenWaitUntil(() -> assertHuskAt(h, NINE))
                    .thenExecute(() -> {
                        h.assertTrue(h.getBlockState(NINE).getValue(ShepherdHuskBlock.FACING) == Direction.WEST, "Looking at the mount");
                        h.assertTrue(h.getBlockState(NINE).getValue(ShepherdHuskBlock.LAMP), "With its lamp");
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_waits", timeoutTicks = 200)
    public static void shepherdHuskWaitsForThePit(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            floor(h);
            ShepherdTestWorld.pitPlanned(h.getLevel(), h.absolutePos(PIT));
            data(h).flag(ShepherdNine.OPEN, true);
            h.startSequence()
                    .thenExecuteAfter(A_LOOK, () -> {
                        h.assertTrue(data(h).huskPos() == null, "Not on a pit that is only planned");
                        ShepherdTestWorld.pitAt(h.getLevel(), h.absolutePos(PIT));
                    })
                    .thenWaitUntil(() -> assertHuskAt(h, NINE))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_floor", timeoutTicks = 200)
    public static void shepherdHuskFindsFreeFloor(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            openPit(h);
            h.setBlock(NINE, Blocks.COBBLESTONE);
            var next = PIT.offset(7, -2, 9);
            h.startSequence().thenWaitUntil(() -> assertHuskAt(h, next)).thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_sight", timeoutTicks = 200)
    public static void shepherdNoHuskInFrontOfAPlayer(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            openPit(h);
            h.startSequence()
                    .thenExecuteAfter(A_LOOK, () -> {
                        h.assertTrue(data(h).huskPos() == null, "Never in view");
                        player.setPos(player.position().add(60, 0, 0));
                    })
                    .thenWaitUntil(() -> assertHuskAt(h, NINE))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_one", timeoutTicks = 200)
    public static void shepherdOnlyOneHusk(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            openPit(h);
            h.startSequence()
                    .thenWaitUntil(() -> assertHuskAt(h, NINE))
                    .thenExecute(() -> h.setBlock(NINE, Blocks.AIR))
                    .thenExecuteAfter(A_LOOK, () -> h.assertTrue(h.getBlockState(NINE).isAir(), "Placed once, whatever becomes of it"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_break", timeoutTicks = 200)
    public static void shepherdTheHuskCannotBeBroken(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var at = husk(h);
            var state = h.getLevel().getBlockState(at);
            h.assertTrue(state.getDestroySpeed(h.getLevel(), at) < 0, "Unbreakable");
            h.assertTrue(state.getBlock().getExplosionResistance() >= 3600000, "Blast-proof");
            h.assertTrue(state.getPistonPushReaction() == PushReaction.BLOCK, "Unpushable");
            h.assertTrue(state.is(BlockTags.WITHER_IMMUNE) && state.is(BlockTags.DRAGON_IMMUNE), "Bosses leave it");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_give", timeoutTicks = 200)
    public static void shepherdHandOverGivesOnce(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var at = husk(h);
            var player = player(h);
            h.assertTrue(ShepherdNine.handOver(player), "It hands over");
            h.assertFalse(ShepherdNine.handOver(player), "And only once");
            h.assertTrue(player.getInventory().countItem(ModItems.NINE_LAMP.get()) == 1, "One lamp");
            h.assertTrue(player.getInventory().countItem(ModItems.ORSA_DOCUMENT.get()) == 1, "One sheet");
            h.assertTrue(data(h).flag(ShepherdNine.SHEET_TAKEN), "The sheet is out");
            h.assertFalse(h.getLevel().getBlockState(at).getValue(ShepherdHuskBlock.LAMP), "The socket is empty");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_cell", timeoutTicks = 200)
    public static void shepherdACellIsRefusedAndKept(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var at = husk(h);
            var player = player(h);
            met(h, player);
            data(h).flag(ShepherdNine.SEEN, true);
            data(h).flag(ShepherdNine.SHEET_TAKEN, true);
            var cell = PowerCellItem.charged(1000);
            player.setItemInHand(InteractionHand.MAIN_HAND, cell);
            ShepherdTestWorld.use(h, at, player);
            h.assertTrue(cell.getCount() == 1 && PowerCellItem.energy(cell) == 1000, "Not spent");
            h.assertTrue("shepherd.t2.cell".equals(ShepherdReactions.said()), "She says why: " + ShepherdReactions.said());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_sheet", timeoutTicks = 200)
    public static void shepherdTheSheetFilesAndStaysSilent(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            var sheet = ShepherdNine.register();
            h.assertTrue("Folded Sheet".equals(sheet.getHoverName().getString()), "Named: " + sheet.getHoverName().getString());
            player.setItemInHand(InteractionHand.MAIN_HAND, sheet);
            sheet.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            var filed = h.getLevel().getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "found_tithonus_register"));
            h.assertTrue(filed != null && player.getAdvancements().getOrStartProgress(filed).isDone(), "Filed");
            h.assertTrue(sheet.isEmpty(), "Archived");
            h.assertTrue(data(h).affinity() == ShepherdAffinity.Deed.READ_LOG.score(), "The deed still counts: " + data(h).affinity());
            h.assertTrue(ShepherdReactions.said() == null, "The strip stays silent: " + ShepherdReactions.said());
            h.succeed();
        });
    }
}
