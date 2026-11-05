package com.frozendawn.shepherd;

import static com.frozendawn.shepherd.ShepherdDockBlock.STATE;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.shepherd.ShepherdDockBlock.DockState;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdBootGameTest {
    private static final BlockPos DOCK = new BlockPos(3, 3, 3);
    private static final int FULL = 1000, OTHER = 777;

    @BeforeBatch(batch = "shepherd_boot_seat") public static void seatBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_seat") public static void seatAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_leave") public static void leaveBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_leave") public static void leaveAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_second") public static void secondBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_second") public static void secondAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_wakes") public static void wakesBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_wakes") public static void wakesAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_walk") public static void walkBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_walk") public static void walkAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_hurt") public static void hurtBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_hurt") public static void hurtAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_dark") public static void darkBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_dark") public static void darkAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_dropped") public static void brokenBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_dropped") public static void brokenAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_seat_reload") public static void seatReloadBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_seat_reload") public static void seatReloadAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_reload") public static void reloadBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_reload") public static void reloadAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_one") public static void oneBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_one") public static void oneAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_unoffered") public static void unofferedBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_unoffered") public static void unofferedAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_trouble") public static void troubleBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_trouble") public static void troubleAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    @BeforeBatch(batch = "shepherd_boot_replay_offer") public static void replayOfferBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_replay_offer") public static void replayOfferAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_replay_through") public static void replayThroughBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_replay_through") public static void replayThroughAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_replay_leave") public static void replayLeaveBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_replay_leave") public static void replayLeaveAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_resume") public static void resumeBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_resume") public static void resumeAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_reboot") public static void rebootBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_reboot") public static void rebootAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    @BeforeBatch(batch = "shepherd_boot_replay_second") public static void replaySecondBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_replay_second") public static void replaySecondAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_boot_replay_trouble") public static void replayTroubleBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_boot_replay_trouble") public static void replayTroubleAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static BlockPos dock(GameTestHelper h, BlockPos at) {
        h.setBlock(at, ModBlocks.CS1_DOCK.get());
        return h.absolutePos(at);
    }

    private static ServerPlayer holding(GameTestHelper h, BlockPos dock, int joules) {
        var player = ShepherdTestWorld.player(h.getLevel(), dock.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, PowerCellItem.charged(joules));
        return player;
    }

    /** In the level, so the dock can find them when she lifts off. */
    private static ServerPlayer online(GameTestHelper h, BlockPos dock) {
        var player = ShepherdTestWorld.onlinePlayer(h, Vec3.atBottomCenterOf(dock.above()).add(1, 0, 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, PowerCellItem.charged(FULL));
        return player;
    }

    private static DockState state(GameTestHelper h, BlockPos dock) {
        return h.getLevel().getBlockState(dock).getValue(STATE);
    }

    private static ShepherdDockBlockEntity entity(GameTestHelper h, BlockPos dock) {
        return (ShepherdDockBlockEntity) h.getLevel().getBlockEntity(dock);
    }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    private static ShepherdEntity she(GameTestHelper h) {
        return data(h).entity() != null && h.getLevel().getEntity(data(h).entity()) instanceof ShepherdEntity body ? body : null;
    }

    /** A cell pops out of the dock sideways and falls to the floor, which can carry it past four blocks. */
    private static boolean dropped(GameTestHelper h, BlockPos near, int joules) {
        var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(near).inflate(8), item -> item.getItem().is(ModItems.POWER_CELL));
        return items.stream().anyMatch(item -> PowerCellItem.energy(item.getItem()) == joules);
    }

    private static void stillDark(GameTestHelper h, BlockPos dock) {
        h.assertTrue(!ShepherdBoot.offered(), "The choice has closed");
        h.assertTrue(state(h, dock) == DockState.SEATED, "The cell stays seated: " + state(h, dock));
        h.assertTrue(data(h).stage() == Stage.DORMANT, "She stays dark");
        h.assertTrue(PowerCellItem.energy(entity(h, dock).seated()) == FULL, "And the cell is the one put in");
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_seat", timeoutTicks = 400)
    public static void shepherdACellIsSeatedNotSpent(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h, DOCK);
            var player = holding(h, dock, FULL);
            ShepherdTestWorld.use(h, dock, player);
            h.assertTrue(player.getMainHandItem().isEmpty(), "The cell has left the hand");
            h.assertTrue(state(h, dock) == DockState.SEATED, "It sits in the slot: " + state(h, dock));
            h.assertTrue(state(h, dock).holdsHer(), "She is still on the dock");
            h.assertTrue(data(h).stage() == Stage.DORMANT && data(h).entity() == null, "Nothing has woken");
            h.assertTrue(PowerCellItem.energy(entity(h, dock).seated()) == FULL, "It keeps its charge");
            h.assertTrue(ShepherdBoot.offered(), "The choice is up");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_leave", timeoutTicks = 400)
    public static void shepherdLeaveLeavesHerDark(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h, DOCK);
            var player = holding(h, dock, FULL);
            ShepherdTestWorld.use(h, dock, player);
            ShepherdBoot.answer(player, false);
            stillDark(h, dock);
            ShepherdTestWorld.touch(h, dock, player);
            h.assertTrue(ShepherdBoot.offered(), "An empty hand gets the choice again");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_second", timeoutTicks = 400)
    public static void shepherdASecondCellIsNotTaken(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h, DOCK);
            var player = holding(h, dock, FULL);
            ShepherdTestWorld.use(h, dock, player);
            ShepherdBoot.answer(player, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, PowerCellItem.charged(OTHER));
            ShepherdTestWorld.use(h, dock, player);
            h.assertTrue(PowerCellItem.energy(player.getMainHandItem()) == OTHER, "A full hand keeps its cell");
            h.assertTrue(PowerCellItem.energy(entity(h, dock).seated()) == FULL, "And the first stays in");
            h.assertTrue(ShepherdBoot.offered(), "It still offers");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_wakes", timeoutTicks = 400)
    public static void shepherdBootWakesHer(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h, DOCK);
            var player = online(h, dock);
            ShepherdTestWorld.boot(h, dock, player);
            h.assertTrue(data(h).stage() == Stage.AWAKE, "She is awake");
            h.assertTrue(state(h, dock) == DockState.BOOT, "The light goes green");
            h.assertTrue(entity(h, dock).seated().isEmpty(), "The cell is spent");
            h.runAfterDelay(ShepherdBoot.BIOS_TICKS - 10, () -> h.assertTrue(data(h).entity() == null, "She stays on the dock through the lines"));
            h.succeedWhen(() -> {
                h.assertTrue("fm.boot".equals(ShepherdTalk.liveNode()), "On fm.boot: " + ShepherdTalk.liveNode());
                h.assertTrue(she(h) != null && she(h).logo(), "With the logo");
                h.assertTrue(she(h).logoTicks() > ShepherdFace.LOGO_TICKS - 4, "Just started: " + she(h).logoTicks());
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_walk", timeoutTicks = 400)
    public static void shepherdWalkingOffCountsAsLeave(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h, DOCK);
            var player = holding(h, dock, FULL);
            ShepherdTestWorld.use(h, dock, player);
            h.assertTrue(ShepherdBoot.offered(), "The choice is up");
            player.setPos(dock.getCenter().add(ShepherdPolicy.TALK_RANGE + 1, 0, 0));
            h.succeedWhen(() -> stillDark(h, dock));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_hurt", timeoutTicks = 400)
    public static void shepherdBeingHurtCountsAsLeave(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h, DOCK);
            var player = online(h, dock);
            player.setGameMode(GameType.SURVIVAL);
            ShepherdTestWorld.use(h, dock, player);
            h.assertTrue(ShepherdBoot.offered(), "The choice is up");
            player.hurt(h.getLevel().damageSources().genericKill(), 1);
            h.succeedWhen(() -> stillDark(h, dock));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_dark", timeoutTicks = 400)
    public static void shepherdADockGoingDarkGivesBackItsCell(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var first = dock(h, DOCK);
            var left = holding(h, first, OTHER);
            ShepherdTestWorld.use(h, first, left);
            ShepherdBoot.answer(left, false);
            var second = dock(h, DOCK.east(3));
            ShepherdTestWorld.boot(h, second, holding(h, second, FULL));
            h.succeedWhen(() -> {
                h.assertTrue(state(h, first) == DockState.DARK, "The other dock goes dark: " + state(h, first));
                h.assertTrue(dropped(h, first, OTHER), "And hands back its cell, charge and all");
                h.assertTrue(entity(h, first).seated().isEmpty(), "Once");
                h.assertTrue(data(h).entity() != null, "She lifts off");
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_dropped", timeoutTicks = 400)
    public static void shepherdBreakingASeatedDockDropsTheCell(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h, DOCK);
            ShepherdTestWorld.use(h, dock, holding(h, dock, OTHER));
            h.setBlock(DOCK, Blocks.AIR);
            h.assertTrue(dropped(h, dock, OTHER), "The cell comes out, charge and all");
            h.succeedWhen(() -> h.assertTrue(!ShepherdBoot.offered(), "A choice with no dock closes"));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_seat_reload", timeoutTicks = 400)
    public static void shepherdASeatedDockSurvivesAReload(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h, DOCK);
            var player = holding(h, dock, FULL);
            ShepherdTestWorld.use(h, dock, player);
            ShepherdBoot.clearForTests();
            var registries = h.getLevel().registryAccess();
            var restored = new ShepherdDockBlockEntity(dock, h.getLevel().getBlockState(dock));
            restored.loadWithComponents(entity(h, dock).saveWithFullMetadata(registries), registries);
            h.assertTrue(PowerCellItem.energy(restored.seated()) == FULL, "The cell comes back with its charge");
            stillDark(h, dock);
            ShepherdTestWorld.touch(h, dock, player);
            h.assertTrue(ShepherdBoot.offered(), "And the choice comes back");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_reload", timeoutTicks = 400)
    public static void shepherdABootSurvivesAReload(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h, DOCK);
            var player = online(h, dock);
            ShepherdTestWorld.boot(h, dock, player);
            h.startSequence().thenExecuteAfter(20, () -> {
                var registries = h.getLevel().registryAccess();
                var saved = entity(h, dock).saveWithFullMetadata(registries);
                ShepherdBoot.clearForTests();
                entity(h, dock).loadWithComponents(saved, registries);
            }).thenWaitUntil(() -> h.assertTrue(she(h) != null, "She finishes and lifts off")).thenExecute(() -> {
                h.assertTrue(!ShepherdTalk.live(), "Nobody is on the BIOS any more, so nothing opens by itself");
                h.assertTrue("fm.boot".equals(data(h).resumeNode()), "The first meeting waits");
                she(h).interact(player, InteractionHand.MAIN_HAND);
                h.assertTrue("fm.boot".equals(ShepherdTalk.liveNode()), "A right-click opens it: " + ShepherdTalk.liveNode());
            }).thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_one", timeoutTicks = 400)
    public static void shepherdOnlyOneChoiceAtATime(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h, DOCK);
            var first = holding(h, dock, FULL);
            var second = holding(h, dock, OTHER);
            ShepherdTestWorld.use(h, dock, first);
            ShepherdTestWorld.use(h, dock, second);
            h.assertTrue(PowerCellItem.energy(second.getMainHandItem()) == OTHER, "The second player keeps their cell");
            ShepherdBoot.answer(second, true);
            h.assertTrue(data(h).stage() == Stage.DORMANT, "The choice is not theirs to make");
            ShepherdBoot.answer(first, true);
            h.assertTrue(data(h).stage() == Stage.AWAKE, "It stayed with the first");
            h.succeedWhen(() -> h.assertTrue(data(h).entity() != null, "She lifts off"));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_unoffered", timeoutTicks = 400)
    public static void shepherdAnUnofferedAnswerDoesNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h, DOCK);
            var player = holding(h, dock, FULL);
            ShepherdBoot.answer(player, true);
            h.assertTrue(state(h, dock) == DockState.FOUND && data(h).stage() == Stage.DORMANT, "No offer, no boot");
            ShepherdTestWorld.use(h, dock, player);
            ShepherdBoot.answer(ShepherdTestWorld.player(h.getLevel(), dock.above()), true);
            h.assertTrue(data(h).stage() == Stage.DORMANT && ShepherdBoot.offered(), "An answer from anyone else moves nothing");
            ShepherdBoot.answer(player, false);
            ShepherdBoot.answer(player, true);
            stillDark(h, dock);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_trouble", timeoutTicks = 400)
    public static void shepherdTheFirstMeetingWaitsOutTrouble(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var dock = dock(h, DOCK);
            var player = online(h, dock);
            player.setGameMode(GameType.SURVIVAL);
            ShepherdTestWorld.boot(h, dock, player);
            h.startSequence().thenExecuteAfter(20, () -> player.hurt(h.getLevel().damageSources().genericKill(), 1))
                    .thenWaitUntil(() -> h.assertTrue(she(h) != null, "She lifts off all the same"))
                    .thenExecute(() -> {
                        h.assertTrue(!ShepherdTalk.live(), "It does not open on someone hurt");
                        h.assertTrue("fm.boot".equals(data(h).resumeNode()), "It waits");
                        she(h).interact(player, InteractionHand.MAIN_HAND);
                        h.assertTrue("fm.boot".equals(ShepherdTalk.liveNode()), "On a right-click: " + ShepherdTalk.liveNode());
                    }).thenSucceed();
        });
    }

    /** Awake and past her first meeting, then put back to its start. */
    private static ShepherdEntity replayed(GameTestHelper h, ServerPlayer player) {
        var she = awake(h, player);
        h.assertTrue(ShepherdTalk.replay(h.getLevel().getServer()), "She is awake to replay");
        return she;
    }

    private static ShepherdEntity awake(GameTestHelper h, ServerPlayer player) {
        data(h).stage(Stage.AWAKE);
        return ShepherdEntity.spawnFrom(h.getLevel(), h.absolutePos(DOCK), player.getUUID());
    }

    private static ServerPlayer beside(GameTestHelper h) {
        return ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(DOCK)).add(1, 0, 0));
    }

    private static void rightClick(ShepherdEntity she, ServerPlayer player) {
        she.interact(player, InteractionHand.MAIN_HAND);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_replay_offer", timeoutTicks = 400)
    public static void shepherdReplayOffersTheChoice(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = beside(h);
            var she = replayed(h, player);
            rightClick(she, player);
            h.assertTrue(ShepherdBoot.offered(), "The choice comes first");
            h.assertTrue(ShepherdTalk.liveNode() == null && !ShepherdTalk.live(), "Nothing opens yet");
            h.assertTrue(she.bootDisplay() == ShepherdEntity.BOOT_DARK, "Her display goes black: " + she.bootDisplay());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_replay_through", timeoutTicks = 400)
    public static void shepherdReplayBootsThrough(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = beside(h);
            var she = replayed(h, player);
            rightClick(she, player);
            ShepherdBoot.answer(player, true);
            h.assertTrue(!data(h).flag(ShepherdSavedData.BOOT_WAITING), "Boot spends the wait");
            h.assertTrue(she.bootDisplay() == ShepherdEntity.BOOT_CURSOR, "The cursor shows: " + she.bootDisplay());
            h.runAfterDelay(ShepherdBoot.BIOS_TICKS - 10, () -> h.assertTrue(!ShepherdTalk.live(), "The lines run first"));
            h.succeedWhen(() -> {
                h.assertTrue("fm.boot".equals(ShepherdTalk.liveNode()), "On fm.boot: " + ShepherdTalk.liveNode());
                h.assertTrue(she.logo(), "With the logo");
                h.assertTrue(she.bootDisplay() == ShepherdEntity.BOOT_NONE, "And her own display back");
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_replay_leave", timeoutTicks = 400)
    public static void shepherdLeavingAReplayKeepsItWaiting(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = beside(h);
            var she = replayed(h, player);
            rightClick(she, player);
            ShepherdBoot.answer(player, false);
            h.assertTrue(!ShepherdBoot.offered(), "The choice closes");
            h.assertTrue(data(h).flag(ShepherdSavedData.BOOT_WAITING), "It still waits");
            h.assertTrue(she.bootDisplay() == ShepherdEntity.BOOT_NONE, "Her display comes back");
            rightClick(she, player);
            h.assertTrue(ShepherdBoot.offered(), "And the next right-click offers again");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_resume", timeoutTicks = 400)
    public static void shepherdResumingTheFirstMeetingSkipsTheChoice(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = beside(h);
            var she = awake(h, player);
            ShepherdTalk.start(player, ShepherdTalk.FIRST_MEETING);
            ShepherdTalk.close(player);
            rightClick(she, player);
            h.assertTrue(!ShepherdBoot.offered(), "No choice on the way back");
            h.assertTrue("fm.boot".equals(ShepherdTalk.liveNode()) && she.logo(), "The logo alone: " + ShepherdTalk.liveNode());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_reboot", timeoutTicks = 400)
    public static void shepherdRebootPlaysNoBoot(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = beside(h);
            var she = awake(h, player);
            she.goOffline();
            she.rebootIn(1);
            h.succeedWhen(() -> {
                h.assertTrue(!she.offline(), "She comes back");
                h.assertTrue(!she.logo() && !ShepherdBoot.offered() && she.bootDisplay() == ShepherdEntity.BOOT_NONE, "With no boot");
            });
        });
    }

    /** The other player would hold the talk, and the one on the BIOS would be left on a screen nothing closes. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_replay_second", timeoutTicks = 400)
    public static void shepherdNobodyElseOpensHerDuringTheLines(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = beside(h);
            var other = beside(h);
            var she = replayed(h, player);
            rightClick(she, player);
            ShepherdBoot.answer(player, true);
            rightClick(she, other);
            h.assertTrue(!ShepherdTalk.live(), "The lines are running: " + ShepherdTalk.liveNode());
            h.succeedWhen(() -> h.assertTrue(ShepherdTalk.speakingTo(player) && "fm.boot".equals(ShepherdTalk.liveNode()), "The first meeting is the booter's"));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_boot_replay_trouble", timeoutTicks = 400)
    public static void shepherdAReplayFinishesItsLinesBeforeATalk(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = beside(h);
            player.setGameMode(GameType.SURVIVAL);
            var she = replayed(h, player);
            rightClick(she, player);
            ShepherdBoot.answer(player, true);
            player.hurt(h.getLevel().damageSources().genericKill(), 1);
            h.startSequence().thenExecuteAfter(2, () -> {
                rightClick(she, player);
                h.assertTrue(!ShepherdTalk.live() && !ShepherdBoot.offered(), "Her body is still on the lines");
            }).thenWaitUntil(() -> h.assertTrue(she.bootDisplay() == ShepherdEntity.BOOT_NONE, "They run out")).thenExecute(() -> {
                h.assertTrue(!ShepherdTalk.live(), "The screen went, so nothing opens by itself");
                rightClick(she, player);
                h.assertTrue("fm.boot".equals(ShepherdTalk.liveNode()) && she.logo(), "A right-click opens it: " + ShepherdTalk.liveNode());
            }).thenSucceed();
        });
    }
}
