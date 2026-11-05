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
    private static final int ASK = 0, KIND = 1, BLUNT = 2, SARCASTIC = 3, CONTINUE = -1;

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

    @BeforeBatch(batch = "shepherd_nine_handle") public static void handleBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_nine_handle") public static void handleAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_t2_opens") public static void t2OpensBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_t2_opens") public static void t2OpensAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_t2_ask") public static void t2AskBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_t2_ask") public static void t2AskAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_t2_pit") public static void t2PitBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_t2_pit") public static void t2PitAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_t2_talk") public static void t2TalkBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_t2_talk") public static void t2TalkAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_site_waits") public static void siteWaitsBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_site_waits") public static void siteWaitsAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_site_wary") public static void siteWaryBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_site_wary") public static void siteWaryAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_site_kind") public static void siteKindBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_site_kind") public static void siteKindAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_site_scrap") public static void siteScrapBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_site_scrap") public static void siteScrapAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_site_after") public static void siteAfterBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_site_after") public static void siteAfterAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_site_closed") public static void siteClosedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_site_closed") public static void siteClosedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_t2_wall") public static void t2WallBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_t2_wall") public static void t2WallAfter(ServerLevel l) { after(l); }

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

    private static void say(ServerPlayer player, int... slots) {
        for (int slot : slots) ShepherdTalk.reply(player, ShepherdTalk.liveId(), slot);
    }

    private static void assertNode(GameTestHelper h, String node) {
        h.assertTrue(node.equals(ShepherdTalk.liveNode()), "On " + node + ": " + ShepherdTalk.liveNode());
    }

    private static void assertPrompt(GameTestHelper h, String node) {
        h.assertTrue(java.util.Objects.equals(node, data(h).promptNode()), "Waiting: " + data(h).promptNode());
    }

    private static int lamps(ServerPlayer player) {
        return player.getInventory().countItem(ModItems.NINE_LAMP.get());
    }

    /** She is up and Friendly with T1 asked, a day clear of it, and the pit is a long way off. */
    private static ShepherdEntity readyForT2(GameTestHelper h, ServerPlayer player) {
        var she = met(h, player);
        data(h).affinity(260);
        data(h).flag("t1_asked", true);
        ShepherdTestWorld.pitAt(h.getLevel(), player.blockPosition().offset(300, 0, -400));
        return she;
    }

    /** Standing at the husk with her beside it, ready for the scene. */
    private static ShepherdEntity atTheHusk(GameTestHelper h, ServerPlayer player, int score) {
        husk(h);
        player.setPos(h.absoluteVec(Vec3.atCenterOf(NINE.offset(-3, 1, -3))));
        var she = met(h, player);
        she.teleportTo(player.getX() + 1, player.getY() + 1, player.getZ());
        data(h).affinity(score);
        data(h).flag(ShepherdNine.OPEN, true);
        data(h).flag("t2_asked", true);
        data(h).flag("t1_asked", true);
        return she;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t2_opens", timeoutTicks = 300)
    public static void shepherdT2OpensThePit(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = readyForT2(h, player);
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_OTHERS))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        assertNode(h, "t2.others");
                        say(player, KIND);
                        assertNode(h, "t2.go");
                        h.assertTrue(data(h).affinity() == 275, "Scored: " + data(h).affinity());
                        h.assertFalse(data(h).flag(ShepherdNine.OPEN), "Not until the ground ref");
                        say(player, CONTINUE);
                        assertNode(h, "t2.mark");
                        h.assertTrue(data(h).flag(ShepherdNine.OPEN) && data(h).flag("t2_heard"), "Open");
                        var pit = ShepherdTestWorld.pit(h.getLevel());
                        var args = ShepherdTalk.liveSent().lines().getFirst().args();
                        h.assertTrue(args.equals(java.util.List.of(String.valueOf(pit.getX()), String.valueOf(pit.getZ()))), "The ground ref: " + args);
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t2_ask", timeoutTicks = 300)
    public static void shepherdAskAboutTheFindReturnsToTheChoice(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            readyForT2(h, player);
            ShepherdTalk.start(player, "t2.others");
            say(player, ASK);
            assertNode(h, "t2.want");
            say(player, ASK);
            assertNode(h, "t2.want");
            say(player, SARCASTIC);
            assertNode(h, "t2.twelve");
            say(player, CONTINUE);
            assertNode(h, "t2.mark");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t2_pit", timeoutTicks = 300)
    public static void shepherdNoT2AtThePit(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            readyForT2(h, player);
            ShepherdTestWorld.pitAt(h.getLevel(), player.blockPosition().offset(60, 0, 40));
            h.startSequence()
                    .thenExecuteAfter(15, () -> {
                        assertPrompt(h, null);
                        ShepherdTestWorld.pitAt(h.getLevel(), player.blockPosition().offset(100, 0, 0));
                    })
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_OTHERS))
                    .thenExecute(() -> ShepherdTestWorld.pitAt(h.getLevel(), player.blockPosition().offset(90, 0, 0)))
                    .thenWaitUntil(() -> assertPrompt(h, null))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t2_talk", timeoutTicks = 300)
    public static void shepherdTalkSaysHowFarUntilSheHasSeenIt(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = readyForT2(h, player);
            data(h).flag(ShepherdNine.OPEN, true);
            she.interact(player, InteractionHand.MAIN_HAND);
            ShepherdTalk.action(player, ShepherdTalk.liveId(), ShepherdTalk.Action.TALK);
            assertNode(h, "hub.talk.pit");
            var args = ShepherdTalk.liveSent().lines().getFirst().args();
            h.assertTrue(args.equals(java.util.List.of("500", "north-east")), "How far: " + args);
            ShepherdTalk.close(player);
            data(h).flag(ShepherdNine.SEEN, true);
            she.interact(player, InteractionHand.MAIN_HAND);
            ShepherdTalk.action(player, ShepherdTalk.liveId(), ShepherdTalk.Action.TALK);
            h.assertFalse("hub.talk.pit".equals(ShepherdTalk.liveNode()), "Not once she has: " + ShepherdTalk.liveNode());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_site_waits", timeoutTicks = 300)
    public static void shepherdTheHuskWaitsForHer(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = atTheHusk(h, player, 260);
            var husk = data(h).huskPos();
            data(h).waiting(true);
            she.teleportTo(husk.getX() + 40, husk.getY() + 3, husk.getZ());
            player.setPos(Vec3.atCenterOf(husk.above()).add(1, 0, 0));
            h.startSequence()
                    .thenExecuteAfter(15, () -> {
                        ShepherdTestWorld.touch(h, husk, player);
                        h.assertTrue(lamps(player) == 0, "It stays inert");
                        h.assertTrue(h.getLevel().getBlockState(husk).getValue(ShepherdHuskBlock.LAMP), "With its lamp in");
                        assertPrompt(h, null);
                        data(h).waiting(false);
                        she.teleportTo(husk.getX() + 3, husk.getY() + 3, husk.getZ());
                    })
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.AT_NINE))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_site_wary", timeoutTicks = 300)
    public static void shepherdNoSiteSceneAtWary(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            atTheHusk(h, player, -300);
            h.startSequence()
                    .thenExecuteAfter(15, () -> {
                        assertPrompt(h, null);
                        data(h).affinity(0);
                    })
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.AT_NINE))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_site_kind", timeoutTicks = 300)
    public static void shepherdKindRepliesHandOverTheLamp(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            atTheHusk(h, player, 260);
            ShepherdTalk.start(player, ShepherdPrompt.AT_NINE);
            h.assertTrue(data(h).flag(ShepherdNine.SEEN), "She has seen it");
            h.assertTrue(ShepherdReactions.quiet(data(h), h.getLevel().getServer().overworld().getGameTime()), "The quiet starts here");
            h.assertTrue(com.frozendawn.miller.MillerPattern.remembers(h.getLevel().getServer(), player.getUUID(), "nine_found"), "She keeps it");
            say(player, KIND);
            assertNode(h, "t2.kind");
            h.assertTrue(lamps(player) == 1, "The lamp is in hand");
            h.assertTrue(data(h).affinity() == 280, "Scored: " + data(h).affinity());
            say(player, CONTINUE);
            assertNode(h, "t2.sheet");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_site_scrap", timeoutTicks = 300)
    public static void shepherdScrapHandsOverNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            atTheHusk(h, player, 260);
            ShepherdTalk.start(player, ShepherdPrompt.AT_NINE);
            say(player, BLUNT);
            assertNode(h, "t2.scrap");
            h.assertTrue(lamps(player) == 0, "Nothing handed over");
            h.assertTrue(data(h).affinity() == 235, "Scored: " + data(h).affinity());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_site_after", timeoutTicks = 300)
    public static void shepherdScrapStillOpensTheHusk(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            atTheHusk(h, player, 260);
            var husk = data(h).huskPos();
            ShepherdTalk.start(player, ShepherdPrompt.AT_NINE);
            say(player, BLUNT, CONTINUE);
            h.assertFalse(ShepherdTalk.live(), "Over");
            ShepherdTestWorld.touch(h, husk, player);
            h.assertTrue(lamps(player) == 1 && data(h).flag(ShepherdNine.SHEET_TAKEN), "It gives them up to a hand");
            h.assertTrue("shepherd.t2.thanks".equals(ShepherdReactions.said()), "Even on a quiet day: " + ShepherdReactions.said());
            ShepherdTestWorld.touch(h, husk, player);
            h.assertTrue(lamps(player) == 1, "Once");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_site_closed", timeoutTicks = 300)
    public static void shepherdAClosedSiteSceneResumes(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = atTheHusk(h, player, 260);
            data(h).promptNode(ShepherdPrompt.AT_NINE);
            she.interact(player, InteractionHand.MAIN_HAND);
            assertNode(h, ShepherdPrompt.AT_NINE);
            long quietEnds = data(h).deedReady("nine_quiet");
            ShepherdTalk.close(player);
            h.startSequence()
                    .thenExecuteAfter(5, () -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        assertNode(h, ShepherdPrompt.AT_NINE);
                        h.assertTrue(data(h).deedReady("nine_quiet") == quietEnds, "One quiet day");
                        say(player, KIND);
                        assertNode(h, "t2.kind");
                        ShepherdTalk.close(player);
                    })
                    .thenExecuteAfter(5, () -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        assertNode(h, "t2.kind");
                        h.assertTrue(lamps(player) == 1, "One lamp");
                        h.assertTrue(player.getInventory().countItem(ModItems.ORSA_DOCUMENT.get()) == 1, "One sheet");
                        h.assertTrue(data(h).affinity() == 280, "Scored once: " + data(h).affinity());
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_t2_wall", timeoutTicks = 800)
    public static void shepherdKindRepliesReachExploitable(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            atTheHusk(h, player, 260);
            var server = h.getLevel().getServer();
            com.frozendawn.miller.MillerPattern.witness(server, com.frozendawn.miller.PatternLaws.EXPLOITABLE, 0);
            Runnable day = () -> {
                ShepherdTalk.close(player);
                ShepherdReactions.skipDay(server);
            };
            ShepherdTalk.start(player, ShepherdPrompt.THE_NAME);
            say(player, KIND);
            day.run();
            var poll = ShepherdReactions.POLL_TICKS + 10;
            h.startSequence()
                    .thenExecuteAfter(poll, () -> {
                        ShepherdTalk.start(player, "t2.others");
                        say(player, KIND);
                        day.run();
                    })
                    .thenExecuteAfter(poll, () -> {
                        ShepherdTalk.start(player, ShepherdPrompt.AT_NINE);
                        say(player, KIND);
                        day.run();
                    })
                    .thenExecuteAfter(poll, () -> h.assertTrue(com.frozendawn.miller.MillerPattern.snapshot(server).walls().contains(com.frozendawn.miller.PatternLaws.EXPLOITABLE),
                            "Three kind replies, with nothing else behind them: " + com.frozendawn.miller.MillerPattern.snapshot(server).walls()))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_nine_handle", timeoutTicks = 300)
    public static void shepherdSheTakesTheLamp(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = met(h, player);
            int lit = data(h).lamp() ? 1 : 0;
            var mood = she.mood();
            var lamp = new net.minecraft.world.item.ItemStack(ModItems.NINE_LAMP.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, lamp);
            she.interact(player, InteractionHand.MAIN_HAND);
            h.assertTrue(lamp.isEmpty(), "Spent");
            h.assertFalse(ShepherdTalk.live(), "Taken, not a conversation");
            h.assertTrue(data(h).flag(ShepherdNine.LAMP_WORN), "Hers now");
            h.assertTrue((data(h).lamp() ? 1 : 0) == lit && she.mood() == mood, "Her lamp and her display are as they were");
            var saved = data(h).save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
            h.assertTrue(ShepherdSavedData.load(saved, h.getLevel().registryAccess()).flag(ShepherdNine.LAMP_WORN), "It reloads");
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(she.lampWorn(), "Her body shows it"))
                    .thenExecute(() -> {
                        she.discard();
                        data(h).entity(null);
                    })
                    .thenExecuteAfter(1, () -> {
                        var rebuilt = ShepherdEntity.spawnFrom(h.getLevel(), h.absolutePos(SPOT), player.getUUID());
                        h.startSequence().thenWaitUntil(() -> h.assertTrue(rebuilt.lampWorn(), "A rebuilt body still has it")).thenSucceed();
                    });
        });
    }
}
