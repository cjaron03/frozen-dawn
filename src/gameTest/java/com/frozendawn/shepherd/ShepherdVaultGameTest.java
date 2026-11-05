package com.frozendawn.shepherd;

import static com.frozendawn.shepherd.ShepherdDockBlock.STATE;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.HelionSiteState;
import com.frozendawn.defense.TerminusLog;
import com.frozendawn.event.PlayerTickHandler;
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
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
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
    private static long apocalypseTicks;

    private static void before(ServerLevel l) {
        ShepherdTestWorld.fresh(l);
        var state = HelionSiteState.get(l.getServer());
        worldVault = state.vault();
        state.setVaultForTest(null);
        apocalypseTicks = ApocalypseState.get(l.getServer()).getApocalypseTicks();
    }

    private static void after(ServerLevel l) {
        ShepherdTestWorld.clear(l);
        HelionSiteState.get(l.getServer()).setVaultForTest(worldVault);
        ApocalypseState.get(l.getServer()).setApocalypseTicks(apocalypseTicks, l.getServer());
    }

    @BeforeBatch(batch = "shepherd_prompt_door") public static void doorBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_door") public static void doorAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_once") public static void onceBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_once") public static void onceAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_exit") public static void exitBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_exit") public static void exitAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_take") public static void takeBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_take") public static void takeAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_esc") public static void promptEscBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_esc") public static void promptEscAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_away") public static void awayBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_away") public static void awayAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_creative") public static void creativeWakeBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_creative") public static void creativeWakeAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_open") public static void openBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_open") public static void openAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_hostile") public static void hostileBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_hostile") public static void hostileAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_hurt") public static void hurtBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_hurt") public static void hurtAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_three") public static void threeBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_three") public static void threeAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_reload") public static void reloadBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_reload") public static void reloadAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_kept") public static void keptBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_kept") public static void keptAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_kept_hit") public static void keptHitBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_kept_hit") public static void keptHitAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_spent") public static void spentBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_spent") public static void spentAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_resume") public static void promptResumeBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_resume") public static void promptResumeAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_prompt_newer") public static void newerBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_prompt_newer") public static void newerAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_ask_float") public static void floatBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_ask_float") public static void floatAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_ask_drop") public static void dropBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_ask_drop") public static void dropAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_ask_stranger") public static void strangerBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_ask_stranger") public static void strangerAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_ask_click") public static void askClickBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_ask_click") public static void askClickAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_calm_farhit") public static void farhitCalmBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_calm_farhit") public static void farhitCalmAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_calm_nearhit") public static void nearhitCalmBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_calm_nearhit") public static void nearhitCalmAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_calm_place") public static void placeCalmBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_calm_place") public static void placeCalmAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_calm_displaced") public static void displacedCalmBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_calm_displaced") public static void displacedCalmAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_calm_cold") public static void coldCalmBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_calm_cold") public static void coldCalmAfter(ServerLevel l) { after(l); }
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
        ShepherdTestWorld.boot(h, dock, player);
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
        ShepherdTestWorld.boot(h, at, player);
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
            ShepherdBoot.answer(player, true);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue("fm.boot".equals(ShepherdTalk.liveNode()), "Waiting for fm.boot: " + ShepherdTalk.liveNode()))
                    .thenExecute(() -> {
                        say(player, KIND, CONTINUE, KIND, CONTINUE, KIND, KIND);
                        h.assertTrue("fm.room".equals(ShepherdTalk.liveNode()), "A replay is still a vault meeting: " + ShepherdTalk.liveNode());
                    }).thenSucceed();
        });
    }

    /** She has finished the first meeting, and the world's vault stands so that the spot beside her is in the cot room. */
    private static ShepherdEntity readyToLeave(GameTestHelper h, ServerPlayer player) {
        var she = metInTheVault(h, player);
        data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
        inTheCotRoom(h);
        return she;
    }

    private static void inTheCotRoom(GameTestHelper h) {
        HelionSiteState.get(h.getLevel().getServer()).setVaultForTest(h.absolutePos(SPOT).offset(-98, 14, 0));
    }

    private static void onTheLanding(GameTestHelper h) {
        HelionSiteState.get(h.getLevel().getServer()).setVaultForTest(h.absolutePos(SPOT).offset(-1, 14, 0));
    }

    private static void assertAsked(GameTestHelper h, String node) {
        h.assertTrue(node.equals(data(h).promptNode()), "She asks: " + data(h).promptNode());
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_door", timeoutTicks = 400)
    public static void shepherdAtTheLastDoorSheAsks(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            readyToLeave(h, player(h));
            h.succeedWhen(() -> {
                assertAsked(h, ShepherdPrompt.DOORS);
                h.assertTrue(ShepherdPrompt.shows() == 1, "And the strip shows: " + ShepherdPrompt.shows());
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_once", timeoutTicks = 400)
    public static void shepherdAsksAboutTheDoorOnce(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = readyToLeave(h, player);
            h.startSequence()
                    .thenWaitUntil(() -> assertAsked(h, ShepherdPrompt.DOORS))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        say(player, ASK, CONTINUE);
                    })
                    .thenExecuteAfter(15, () -> h.assertTrue(data(h).promptNode() == null, "The box again raises nothing: " + data(h).promptNode()))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_exit", timeoutTicks = 400)
    public static void shepherdAtTheExitTheLineReplacesTheDoors(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            readyToLeave(h, player(h));
            h.startSequence()
                    .thenWaitUntil(() -> assertAsked(h, ShepherdPrompt.DOORS))
                    .thenExecute(() -> onTheLanding(h))
                    .thenWaitUntil(() -> assertAsked(h, ShepherdPrompt.EXIT))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_take", timeoutTicks = 400)
    public static void shepherdRightClickTakesThePrompt(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = readyToLeave(h, player);
            h.startSequence()
                    .thenWaitUntil(() -> assertAsked(h, ShepherdPrompt.DOORS))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        h.assertTrue(ShepherdPrompt.DOORS.equals(ShepherdTalk.liveNode()), "The right-click plays it: " + ShepherdTalk.liveNode());
                        say(player, ASK);
                        h.assertTrue(data(h).promptNode() == null, "An answer spends it");
                        say(player, CONTINUE);
                        she.interact(player, InteractionHand.MAIN_HAND);
                        h.assertTrue(ShepherdTalk.live() && ShepherdTalk.liveNode() == null, "The next is the hub: " + ShepherdTalk.liveNode());
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_esc", timeoutTicks = 400)
    public static void shepherdEscResumesThePrompt(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = readyToLeave(h, player);
            h.startSequence()
                    .thenWaitUntil(() -> assertAsked(h, ShepherdPrompt.DOORS))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        ShepherdTalk.close(player);
                        she.interact(player, InteractionHand.MAIN_HAND);
                        h.assertTrue(ShepherdPrompt.DOORS.equals(ShepherdTalk.liveNode()), "She picks it back up: " + ShepherdTalk.liveNode());
                    })
                    .thenSucceed();
        });
    }

    /** She is met and done, with the exit line waiting and open on {@code player}'s screen. */
    private static ShepherdEntity onTheExitLine(GameTestHelper h, ServerPlayer player) {
        var she = metInTheVault(h, player);
        data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
        data(h).promptNode(ShepherdPrompt.EXIT);
        she.interact(player, InteractionHand.MAIN_HAND);
        h.assertTrue(ShepherdPrompt.EXIT.equals(ShepherdTalk.liveNode()), "On the exit line: " + ShepherdTalk.liveNode());
        return she;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_kept", timeoutTicks = 400)
    public static void shepherdEscKeepsTheExitLineWaiting(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = onTheExitLine(h, player);
            ShepherdTalk.close(player);
            she.interact(player, InteractionHand.MAIN_HAND);
            h.assertTrue(ShepherdPrompt.EXIT.equals(ShepherdTalk.liveNode()), "It has no replies to resume on, and still waits: " + ShepherdTalk.liveNode());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_kept_hit", timeoutTicks = 400)
    public static void shepherdAHitKeepsTheExitLineWaiting(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            player.setGameMode(GameType.SURVIVAL);
            var she = onTheExitLine(h, player);
            player.hurt(h.getLevel().damageSources().genericKill(), 1);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(!ShepherdTalk.live(), "A hit closes the screen"))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        h.assertTrue(ShepherdPrompt.EXIT.equals(ShepherdTalk.liveNode()), "And she still waits: " + ShepherdTalk.liveNode());
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_spent", timeoutTicks = 400)
    public static void shepherdAClickSpendsTheExitLine(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = onTheExitLine(h, player);
            say(player, CONTINUE);
            h.assertTrue(data(h).promptNode() == null, "A click has heard it: " + data(h).promptNode());
            she.interact(player, InteractionHand.MAIN_HAND);
            h.assertTrue(ShepherdTalk.live() && ShepherdTalk.liveNode() == null, "The next is the hub: " + ShepherdTalk.liveNode());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_newer", timeoutTicks = 400)
    public static void shepherdANewerPromptSurvivesTheAnswer(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            onTheExitLine(h, player);
            data(h).promptNode(ShepherdPrompt.DOORS);
            say(player, CONTINUE);
            h.assertTrue(ShepherdPrompt.DOORS.equals(data(h).promptNode()), "Raised while the other was open: " + data(h).promptNode());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_resume", timeoutTicks = 400)
    public static void shepherdAResumeLeavesThePromptWaiting(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = metInTheVault(h, player);
            data(h).resumeNode("fm.room");
            data(h).promptNode(ShepherdPrompt.EXIT);
            she.interact(player, InteractionHand.MAIN_HAND);
            h.assertTrue("fm.room".equals(ShepherdTalk.liveNode()), "The unfinished one first: " + ShepherdTalk.liveNode());
            say(player, ASK);
            h.assertTrue(ShepherdPrompt.EXIT.equals(data(h).promptNode()), "The prompt is not what was answered: " + data(h).promptNode());
            h.succeed();
        });
    }

    /** A player facing +z in the open, with her 2.5 blocks behind them and a prompt waiting. */
    private static ServerPlayer facingSouth(GameTestHelper h) {
        var player = ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(new Vec3(10.5, 3, 10.5)));
        player.setYRot(0);
        player.setYHeadRot(0);
        return player;
    }

    private static ShepherdEntity behind(GameTestHelper h, ServerPlayer player) {
        var she = metInTheVault(h, player);
        she.teleportTo(player.getX(), player.getY() + 1, player.getZ() - 2.5);
        data(h).promptNode(ShepherdPrompt.EXIT);
        return she;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_ask_float", timeoutTicks = 400)
    public static void shepherdSheFloatsUpWhenSheAsks(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = facingSouth(h);
            var she = behind(h, player);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(she.asking(), "She starts to ask"))
                    .thenExecute(() -> h.assertTrue(she.mood() == ShepherdFace.Mood.THINKING, "On her thinking face: " + she.mood()))
                    .thenExecuteAfter(60, () -> {
                        var front = player.getEyePosition().add(0, -0.3, ShepherdPolicy.ASK_BLOCKS);
                        h.assertTrue(she.position().distanceTo(front) <= 1.5, "In front of them: " + she.position().subtract(front));
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_ask_drop", timeoutTicks = 400)
    public static void shepherdSheDropsBackAfterAsking(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = facingSouth(h);
            var she = behind(h, player);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(she.asking(), "She starts to ask"))
                    .thenWaitUntil(() -> h.assertTrue(!she.asking(), "And stops"))
                    .thenExecute(() -> h.assertTrue(she.mood() == ShepherdFace.Mood.NEUTRAL, "Her face comes back: " + she.mood()))
                    .thenWaitUntil(() -> h.assertTrue(Math.abs(she.getY() - player.getY() - 1) < 0.6 && she.distanceTo(player) < 3.5, "Back at her hover: " + she.position().subtract(player.position())))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_ask_stranger", timeoutTicks = 400)
    public static void shepherdSheDoesNotFloatToAStranger(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = facingSouth(h);
            var she = behind(h, player);
            data(h).follower(UUID.randomUUID());
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(ShepherdPrompt.shows() == 1, "The strip shows: " + ShepherdPrompt.shows()))
                    .thenExecute(() -> h.assertTrue(!she.asking(), "She stays where she is"))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_ask_click", timeoutTicks = 400)
    public static void shepherdRightClickingEndsTheAsk(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = facingSouth(h);
            var she = behind(h, player);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(she.asking(), "She starts to ask"))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        h.assertTrue(!she.asking(), "The talk takes over");
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_calm_farhit", timeoutTicks = 400)
    public static void shepherdAHitOnAFarPlayerDoesNotDelayTheStrip(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var far = ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(ShepherdPolicy.HEARD_BLOCKS + 10, 0, 0));
            far.setGameMode(GameType.SURVIVAL);
            far.hurt(h.getLevel().damageSources().genericKill(), 1);
            readyToLeave(h, player(h));
            h.succeedWhen(() -> h.assertTrue(ShepherdPrompt.shows() == 1, "The strip shows: " + ShepherdPrompt.shows()));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_calm_nearhit", timeoutTicks = 400)
    public static void shepherdAHitOnANearPlayerStillDelaysIt(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var near = ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(0, 0, 3));
            near.setGameMode(GameType.SURVIVAL);
            near.hurt(h.getLevel().damageSources().genericKill(), 1);
            readyToLeave(h, player(h));
            h.startSequence()
                    .thenWaitUntil(() -> assertAsked(h, ShepherdPrompt.DOORS))
                    .thenExecuteAfter(15, () -> h.assertTrue(ShepherdPrompt.shows() == 0, "Not while a near player is hurt: " + ShepherdPrompt.shows()))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_calm_place", timeoutTicks = 400)
    public static void shepherdAPlacePromptGoesFirst(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = metInTheVault(h, player);
            data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
            data(h).promptNode(ShepherdPrompt.EXIT);
            data(h).affinity(260);
            h.startSequence()
                    .thenExecuteAfter(15, () -> {
                        h.assertTrue(ShepherdPrompt.EXIT.equals(data(h).promptNode()), "Not replaced: " + data(h).promptNode());
                        she.interact(player, InteractionHand.MAIN_HAND);
                        say(player, CONTINUE);
                    })
                    .thenWaitUntil(() -> assertAsked(h, ShepherdPrompt.THE_NAME))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_calm_displaced", timeoutTicks = 400)
    public static void shepherdAPlacePromptDoesNotLoseTheName(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = readyToLeave(h, player);
            data(h).flag(ShepherdPrompt.DOORS_ASKED, true);
            data(h).affinity(260);
            h.startSequence()
                    .thenWaitUntil(() -> assertAsked(h, ShepherdPrompt.THE_NAME))
                    .thenExecute(() -> onTheLanding(h))
                    .thenWaitUntil(() -> assertAsked(h, ShepherdPrompt.EXIT))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        say(player, CONTINUE);
                    })
                    .thenWaitUntil(() -> assertAsked(h, ShepherdPrompt.THE_NAME))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_calm_cold", timeoutTicks = 400)
    public static void shepherdColdHoldsATierUpButNotAPlacePrompt(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var state = ApocalypseState.get(h.getLevel().getServer());
            state.setApocalypseTicks((long) state.getTotalDays() * 24000, h.getLevel().getServer());
            var player = ShepherdTestWorld.onlinePlayer(h, Vec3.atBottomCenterOf(h.absolutePos(SPOT).atY(120)));
            var she = metInTheVault(h, player);
            she.teleportTo(player.getX() + 1, player.getY() + 1, player.getZ());
            data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
            data(h).affinity(260);
            h.startSequence()
                    .thenExecuteAfter(15, () -> {
                        h.assertTrue(PlayerTickHandler.resolvedTemperature(player) <= ShepherdPolicy.COLD_LINE_C, "Cold: " + PlayerTickHandler.resolvedTemperature(player));
                        h.assertTrue(data(h).promptNode() == null, "Too cold to get personal: " + data(h).promptNode());
                        HelionSiteState.get(h.getLevel().getServer()).setVaultForTest(player.blockPosition().offset(-1, 14, 0));
                        data(h).flag(ShepherdSavedData.WOKE_IN_VAULT, true);
                    })
                    .thenWaitUntil(() -> assertAsked(h, ShepherdPrompt.EXIT))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_away", timeoutTicks = 600)
    public static void shepherdOfflineOrAwaySheDoesNotAsk(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = metInTheVault(h, player);
            data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
            she.goOffline();
            inTheCotRoom(h);
            Runnable nothing = () -> {
                h.assertTrue(data(h).promptNode() == null, "No prompt: " + data(h).promptNode());
                h.assertTrue(!data(h).flag(ShepherdPrompt.DOORS_ASKED), "None used up");
            };
            h.startSequence()
                    .thenExecuteAfter(15, nothing)
                    .thenExecute(() -> {
                        she.rebootIn(1);
                        data(h).waiting(true);
                    })
                    .thenWaitUntil(() -> h.assertTrue(!she.offline(), "Back up"))
                    .thenExecuteAfter(15, nothing)
                    .thenExecute(() -> {
                        data(h).waiting(false);
                        she.setNoAi(true);
                        var far = she.position().add(0, 0, 20);
                        she.teleportTo(far.x, far.y, far.z);
                    })
                    .thenExecuteAfter(15, nothing)
                    .thenExecute(() -> she.teleportTo(player.getX(), player.getY() + 1, player.getZ()))
                    .thenWaitUntil(() -> assertAsked(h, ShepherdPrompt.DOORS))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_creative", timeoutTicks = 400)
    public static void shepherdACreativeWakeNeverAsks(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            readyToLeave(h, player(h));
            data(h).flag(ShepherdSavedData.WOKE_IN_VAULT, false);
            h.startSequence().thenExecuteAfter(15, () -> h.assertTrue(data(h).promptNode() == null, "Not woken here")).thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_open", timeoutTicks = 400)
    public static void shepherdAnOpenFirstMeetingIsNotInterrupted(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            readyToLeave(h, player(h));
            data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, false);
            h.startSequence().thenExecuteAfter(15, () -> h.assertTrue(data(h).promptNode() == null, "The meeting comes first")).thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_hostile", timeoutTicks = 400)
    public static void shepherdAHostileDelaysThePrompt(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(12.5, 3, 12.5));
            readyToLeave(h, player(h));
            h.startSequence()
                    .thenWaitUntil(() -> assertAsked(h, ShepherdPrompt.DOORS))
                    .thenExecuteAfter(15, () -> h.assertTrue(ShepherdPrompt.shows() == 0, "Not while it is near: " + ShepherdPrompt.shows()))
                    .thenExecute(zombie::discard)
                    .thenWaitUntil(() -> h.assertTrue(ShepherdPrompt.shows() == 1, "Calm again: " + ShepherdPrompt.shows()))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_hurt", timeoutTicks = 400)
    public static void shepherdAHurtDelaysThePrompt(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            player.setGameMode(GameType.SURVIVAL);
            player.hurt(h.getLevel().damageSources().genericKill(), 1);
            readyToLeave(h, player);
            h.startSequence()
                    .thenWaitUntil(() -> assertAsked(h, ShepherdPrompt.DOORS))
                    .thenExecuteAfter(15, () -> h.assertTrue(ShepherdPrompt.shows() == 0, "Not straight after a hit: " + ShepherdPrompt.shows()))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_three", timeoutTicks = 400)
    public static void shepherdThePromptStopsAtThree(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = readyToLeave(h, player);
            ShepherdPrompt.repeatEvery(5);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(ShepherdPrompt.shows() == ShepherdPolicy.PROMPT_SHOWS, "Three showings: " + ShepherdPrompt.shows()))
                    .thenExecuteAfter(30, () -> {
                        h.assertTrue(ShepherdPrompt.shows() == ShepherdPolicy.PROMPT_SHOWS, "Then she stops: " + ShepherdPrompt.shows());
                        assertAsked(h, ShepherdPrompt.DOORS);
                        she.interact(player, InteractionHand.MAIN_HAND);
                        h.assertTrue(ShepherdPrompt.DOORS.equals(ShepherdTalk.liveNode()), "She still waits: " + ShepherdTalk.liveNode());
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_prompt_reload", timeoutTicks = 400)
    public static void shepherdThePromptSurvivesAReload(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            data(h).promptNode(ShepherdPrompt.EXIT);
            var registries = h.getLevel().registryAccess();
            var again = ShepherdSavedData.load(data(h).save(new CompoundTag(), registries), registries);
            h.assertTrue(ShepherdPrompt.EXIT.equals(again.promptNode()), "Still waiting: " + again.promptNode());
            h.succeed();
        });
    }
}
