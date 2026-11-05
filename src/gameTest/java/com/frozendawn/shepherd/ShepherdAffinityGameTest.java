package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.event.PlayerTickHandler;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.shepherd.ShepherdTalk.Action;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What her score does to her. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdAffinityGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);
    private static final int CONTINUE = -1;

    @BeforeBatch(batch = "shepherd_tier_wary") public static void waryBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_tier_wary") public static void waryAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_tier_leaving") public static void leavingBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_tier_leaving") public static void leavingAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_tier_hub") public static void hubBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_tier_hub") public static void hubAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_tier_replay") public static void replayBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_tier_replay") public static void replayAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_tier_command") public static void commandBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_tier_command") public static void commandAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    @BeforeBatch(batch = "shepherd_deed_face") public static void faceDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_face") public static void faceDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_cooldown") public static void cooldownDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_cooldown") public static void cooldownDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_ends") public static void endsDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_ends") public static void endsDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_reload") public static void reloadDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_reload") public static void reloadDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_far") public static void farDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_far") public static void farDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_offline") public static void offlineDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_offline") public static void offlineDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_unloaded") public static void unloadedDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_unloaded") public static void unloadedDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_second") public static void secondDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_second") public static void secondDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_warm") public static void warmDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_warm") public static void warmDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_frozen") public static void frozenDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_frozen") public static void frozenDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_vault") public static void vaultDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_vault") public static void vaultDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_night") public static void nightDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_night") public static void nightDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_log") public static void logDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_log") public static void logDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_archivist") public static void archivistDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_archivist") public static void archivistDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_named") public static void namedDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_named") public static void namedDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_left") public static void leftDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_left") public static void leftDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_stayed") public static void stayedDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_stayed") public static void stayedDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_talking") public static void talkingDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_talking") public static void talkingDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_deed_wary") public static void waryDeedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_deed_wary") public static void waryDeedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_name_friendly") public static void friendlyNameBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_name_friendly") public static void friendlyNameAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_name_first") public static void firstNameBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_name_first") public static void firstNameAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_name_wary") public static void waryNameBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_name_wary") public static void waryNameAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_name_falling") public static void fallingNameBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_name_falling") public static void fallingNameAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_name_withdrawn") public static void withdrawnNameBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_name_withdrawn") public static void withdrawnNameAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_name_open") public static void openNameBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_name_open") public static void openNameAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_name_knows") public static void knowsNameBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_name_knows") public static void knowsNameAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_name_replay") public static void replayNameBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_name_replay") public static void replayNameAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_skip_day") public static void skipBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_skip_day") public static void skipAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_react_heavy") public static void heavyBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_react_heavy") public static void heavyAfter(ServerLevel l) { after(l); }

    /** Some deeds move the world's clock on, so the phase comes back. */
    private static long apocalypseTicks;

    private static void before(ServerLevel l) {
        ShepherdTestWorld.fresh(l);
        apocalypseTicks = ApocalypseState.get(l.getServer()).getApocalypseTicks();
    }

    private static void after(ServerLevel l) {
        ApocalypseState.get(l.getServer()).setApocalypseTicks(apocalypseTicks, l.getServer());
        ShepherdTestWorld.clear(l);
    }

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

    private static void command(GameTestHelper h, String command) {
        var server = h.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static void assertFace(GameTestHelper h, ShepherdEntity she, Mood mood) {
        h.assertTrue(she.mood() == mood, "Her face: " + she.mood());
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_tier_wary", timeoutTicks = 400)
    public static void shepherdAtWarySheRestsOnTheRecordingFace(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            data(h).affinity(-300);
            h.startSequence()
                    .thenWaitUntil(() -> assertFace(h, she, Mood.RECORDING))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        assertFace(h, she, Mood.RECORDING);
                        ShepherdTalk.close(player);
                        assertFace(h, she, Mood.RECORDING);
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_tier_leaving", timeoutTicks = 400)
    public static void shepherdLeavingWaryBringsHerFaceBack(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = awake(h, player(h));
            data(h).affinity(-300);
            h.startSequence()
                    .thenWaitUntil(() -> assertFace(h, she, Mood.RECORDING))
                    .thenExecute(() -> data(h).affinity(0))
                    .thenWaitUntil(() -> assertFace(h, she, Mood.NEUTRAL))
                    .thenSucceed();
        });
    }

    private static void assertTalkOpens(GameTestHelper h, ShepherdEntity she, ServerPlayer player, int score, String node) {
        data(h).affinity(score);
        she.interact(player, InteractionHand.MAIN_HAND);
        ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.TALK);
        h.assertTrue(node.equals(ShepherdTalk.liveNode()), "At " + score + ": " + ShepherdTalk.liveNode());
        ShepherdTalk.reply(player, ShepherdTalk.liveId(), CONTINUE);
        ShepherdTalk.close(player);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_tier_hub", timeoutTicks = 400)
    public static void shepherdHubTalkFollowsTheTier(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            assertTalkOpens(h, she, player, -300, "hub.talk");
            assertTalkOpens(h, she, player, 0, "hub.talk.plain");
            assertTalkOpens(h, she, player, 300, "hub.talk.warm");
            assertTalkOpens(h, she, player, 900, "hub.talk.warm");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_tier_replay", timeoutTicks = 400)
    public static void shepherdReplayClearsTheScore(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            awake(h, player(h));
            data(h).affinity(300);
            data(h).deedReady(Deed.READ_LOG.name(), clock(h) + 1000);
            data(h).lastNear(clock(h));
            command(h, "fd shepherd replay");
            h.assertTrue(data(h).tier() == ShepherdAffinity.Tier.NEUTRAL, "Back to nothing: " + data(h).affinity());
            h.assertTrue(data(h).deedReady(Deed.READ_LOG.name()) == 0, "Her cooldowns go too");
            h.assertTrue(data(h).lastNear() == ShepherdSavedData.NOT_SET, "And her clock on you");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_tier_command", timeoutTicks = 400)
    public static void shepherdAffinityCommandSetsTheScore(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            command(h, "fd shepherd affinity 260");
            h.assertTrue(data(h).affinity() == 260, "Set: " + data(h).affinity());
            command(h, "fd shepherd affinity -300");
            h.assertTrue(data(h).tier() == ShepherdAffinity.Tier.WARY, "Negative too: " + data(h).affinity());
            h.succeed();
        });
    }

    /** The clock is hers to read: the shards are of any age. */
    private static long clock(GameTestHelper h) {
        return h.getLevel().getGameTime();
    }

    private static void wind(GameTestHelper h, long to) {
        ((ServerLevelData) h.getLevel().getLevelData()).setGameTime(to);
    }

    private static void assertScore(GameTestHelper h, int score) {
        h.assertTrue(data(h).affinity() == score, "Her score: " + data(h).affinity());
    }

    /** Stone on every side and a roof, high enough to feel the weather, with her in it. */
    private static BlockPos hut(GameTestHelper h) {
        var at = h.absolutePos(SPOT);
        var center = new BlockPos(at.getX(), 64, at.getZ());
        for (int x = -3; x <= 3; x++) for (int y = -1; y <= 3; y++) for (int z = -3; z <= 3; z++) {
            boolean wall = Math.abs(x) == 3 || Math.abs(z) == 3 || y == -1 || y == 3;
            h.getLevel().setBlockAndUpdate(center.offset(x, y, z), (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        }
        return center;
    }

    private static ServerPlayer inTheHut(GameTestHelper h, BlockPos hut) {
        var player = ShepherdTestWorld.onlinePlayer(h, Vec3.atBottomCenterOf(hut));
        data(h).stage(Stage.AWAKE);
        ShepherdEntity.spawnAt(h.getLevel(), Vec3.atBottomCenterOf(hut.east().above()), player.getUUID());
        return player;
    }

    private static void endgame(GameTestHelper h) {
        var state = ApocalypseState.get(h.getLevel().getServer());
        state.setApocalypseTicks((long) state.getTotalDays() * 24000, h.getLevel().getServer());
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_face", timeoutTicks = 400)
    public static void shepherdADeedMovesTheScoreAndHerFace(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            ShepherdReactions.saw(player, Deed.CALLED_SHEPHERD);
            assertScore(h, -5);
            assertFace(h, she, Mood.UNIMPRESSED);
            h.startSequence()
                    .thenExecuteAfter(ShepherdPolicy.REACT_TICKS - 10, () -> assertFace(h, she, Mood.UNIMPRESSED))
                    .thenExecuteAfter(15, () -> assertFace(h, she, Mood.NEUTRAL))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_cooldown", timeoutTicks = 400)
    public static void shepherdACooldownStopsTheSecondDeed(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            ShepherdReactions.saw(player, Deed.READ_LOG);
            ShepherdReactions.saw(player, Deed.READ_LOG);
            assertScore(h, 10);
            ShepherdReactions.saw(player, Deed.CALLED_SHEPHERD);
            assertScore(h, 5);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_ends", timeoutTicks = 400)
    public static void shepherdACooldownEndsOnTime(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            long start = clock(h);
            ShepherdReactions.saw(player, Deed.READ_LOG);
            wind(h, start + Deed.READ_LOG.cooldownTicks() - 1);
            ShepherdReactions.saw(player, Deed.READ_LOG);
            assertScore(h, 10);
            wind(h, start + Deed.READ_LOG.cooldownTicks());
            ShepherdReactions.saw(player, Deed.READ_LOG);
            assertScore(h, 20);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_reload", timeoutTicks = 400)
    public static void shepherdACooldownSurvivesAReload(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            ShepherdReactions.saw(player, Deed.READ_LOG);
            var registries = h.getLevel().registryAccess();
            var again = ShepherdSavedData.load(data(h).save(new CompoundTag(), registries), registries);
            h.assertTrue(again.deedReady(Deed.READ_LOG.name()) == data(h).deedReady(Deed.READ_LOG.name()), "The cooldown saves");
            h.assertTrue(again.deedReady(Deed.READ_LOG.name()) > clock(h), "And still runs");
            h.assertTrue(again.lastNear() == data(h).lastNear(), "So does her clock on you");
            h.assertTrue(ShepherdSavedData.load(new CompoundTag(), registries).lastNear() == ShepherdSavedData.NOT_SET, "An older save has neither");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_far", timeoutTicks = 400)
    public static void shepherdSheOnlyKnowsWhatSheSaw(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            she.setNoAi(true);
            she.teleportTo(player.getX() + ShepherdPolicy.HEARD_BLOCKS + 1, player.getY(), player.getZ());
            ShepherdReactions.saw(player, Deed.ARCHIVIST_KILLED);
            assertScore(h, 0);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_offline", timeoutTicks = 400)
    public static void shepherdOfflineSheSeesNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player).goOffline();
            ShepherdReactions.saw(player, Deed.READ_LOG);
            assertScore(h, 0);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_unloaded", timeoutTicks = 400)
    public static void shepherdAnUnloadedBodySeesNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            data(h).entity(UUID.randomUUID());
            ShepherdReactions.saw(player, Deed.READ_LOG);
            assertScore(h, 0);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_second", timeoutTicks = 400)
    public static void shepherdASecondPlayersDeedCounts(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var follower = player(h);
            var stranger = ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(0, 0, 3));
            awake(h, follower);
            ShepherdReactions.saw(stranger, Deed.READ_LOG);
            assertScore(h, 10);
            ShepherdReactions.saw(follower, Deed.READ_LOG);
            assertScore(h, 10);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_warm", timeoutTicks = 600)
    public static void shepherdAWarmShelterPleasesHer(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var hut = hut(h);
            var heater = hut.west(2);
            h.getLevel().setBlockAndUpdate(heater, ModBlocks.THERMAL_HEATER.get().defaultBlockState());
            var player = inTheHut(h, hut);
            ((ThermalHeaterBlockEntity) h.getLevel().getBlockEntity(heater)).addFuel(24000);
            h.succeedWhen(() -> assertScore(h, Deed.WARM_SHELTER.score()));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_frozen", timeoutTicks = 600)
    public static void shepherdAFrozenShelterWithAHeaterCosts(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var hut = hut(h);
            h.getLevel().setBlockAndUpdate(hut.west(2), ModBlocks.THERMAL_HEATER.get().defaultBlockState());
            endgame(h);
            inTheHut(h, hut);
            h.succeedWhen(() -> assertScore(h, Deed.SHELTER_FROZE.score()));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_vault", timeoutTicks = 600)
    public static void shepherdAColdVaultIsNotAFrozenShelter(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var hut = hut(h);
            endgame(h);
            var player = inTheHut(h, hut);
            h.startSequence()
                    .thenExecuteAfter(ShepherdReactions.POLL_TICKS + 10, () -> {
                        h.assertTrue(PlayerTickHandler.resolvedTemperature(player) <= ShepherdPolicy.FROZEN_C, "Cold enough: " + PlayerTickHandler.resolvedTemperature(player));
                        assertScore(h, 0);
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_night", timeoutTicks = 400)
    public static void shepherdAClearNightPleasesHer(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var level = h.getLevel();
            level.setWeatherParameters(24000, 0, false, false);
            level.setDayTime(18000);
            awake(h, player(h));
            h.succeedWhen(() -> assertScore(h, Deed.CLEAR_NIGHT.score()));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_log", timeoutTicks = 400)
    public static void shepherdArchivingADocumentPleasesHer(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            var tag = new CompoundTag();
            tag.putString("doc_type", "satellite_log");
            var document = new ItemStack(ModItems.ORSA_DOCUMENT.get());
            document.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            player.setItemInHand(InteractionHand.MAIN_HAND, document);
            document.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            assertScore(h, Deed.READ_LOG.score());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_archivist", timeoutTicks = 400)
    public static void shepherdKillingAnArchivistHurtsHer(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            var archivist = h.spawnWithNoFreeWill(ModEntities.ARCHIVIST.get(), new Vec3(12.5, 4, 10.5));
            archivist.die(h.getLevel().damageSources().playerAttack(player));
            assertScore(h, Deed.ARCHIVIST_KILLED.score());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_named", timeoutTicks = 400)
    public static void shepherdCallingHerShepherdCosts(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            NeoForge.EVENT_BUS.post(new ServerChatEvent(player, "good dog", Component.literal("good dog")));
            NeoForge.EVENT_BUS.post(new ServerChatEvent(player, "thanks, ShEpHeRd", Component.literal("thanks, ShEpHeRd")));
            h.succeedWhen(() -> assertScore(h, Deed.CALLED_SHEPHERD.score()));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_left", timeoutTicks = 400)
    public static void shepherdComingBackAfterADayCosts(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            awake(h, player(h));
            data(h).lastNear(clock(h) - ShepherdPolicy.DAY_TICKS);
            h.succeedWhen(() -> assertScore(h, Deed.LEFT_BEHIND.score()));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_stayed", timeoutTicks = 400)
    public static void shepherdStayingNearCostsNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            awake(h, player(h));
            data(h).lastNear(clock(h) - ShepherdPolicy.DAY_TICKS + 2 * ShepherdReactions.POLL_TICKS);
            h.startSequence()
                    .thenExecuteAfter(ShepherdReactions.POLL_TICKS + 5, () -> {
                        h.assertTrue(clock(h) - data(h).lastNear() <= ShepherdReactions.POLL_TICKS + 5, "Her clock on you moved");
                        assertScore(h, 0);
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_talking", timeoutTicks = 400)
    public static void shepherdNoReactionWhileTalking(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            she.interact(player, InteractionHand.MAIN_HAND);
            ShepherdReactions.saw(player, Deed.CALLED_SHEPHERD);
            assertScore(h, -5);
            assertFace(h, she, Mood.NEUTRAL);
            h.startSequence()
                    .thenExecuteAfter(5, () -> {
                        ShepherdTalk.close(player);
                        ShepherdReactions.saw(player, Deed.CALLED_SHEPHERD);
                        assertScore(h, -5);
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_deed_wary", timeoutTicks = 400)
    public static void shepherdAtWaryADeedDoesNotMoveHerFace(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            data(h).affinity(-300);
            h.startSequence()
                    .thenWaitUntil(() -> assertFace(h, she, Mood.RECORDING))
                    .thenExecute(() -> {
                        ShepherdReactions.saw(player, Deed.CALLED_SHEPHERD);
                        assertScore(h, -305);
                    })
                    .thenExecuteAfter(2, () -> assertFace(h, she, Mood.RECORDING))
                    .thenSucceed();
        });
    }

    /** She is past her first meeting, {@code score} near a tier, and nothing else stands in the way. */
    private static ShepherdEntity settled(GameTestHelper h, ServerPlayer player, int score) {
        var she = awake(h, player);
        data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
        data(h).affinity(score);
        return she;
    }

    private static void assertPrompt(GameTestHelper h, String node) {
        h.assertTrue(Objects.equals(node, data(h).promptNode()), "Waiting: " + data(h).promptNode());
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_name_friendly", timeoutTicks = 400)
    public static void shepherdAtFriendlySheAsksAboutTheName(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = settled(h, player, 260);
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_NAME))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        h.assertTrue(ShepherdPrompt.THE_NAME.equals(ShepherdTalk.liveNode()) || "t1.name.unasked".equals(ShepherdTalk.liveNode()), "On it: " + ShepherdTalk.liveNode());
                        ShepherdTalk.reply(player, ShepherdTalk.liveId(), 1);
                        h.assertTrue(data(h).promptNode() == null, "Spent");
                        ShepherdTalk.reply(player, ShepherdTalk.liveId(), CONTINUE);
                        h.assertTrue("t1.end".equals(ShepherdTalk.liveNode()), "To the end: " + ShepherdTalk.liveNode());
                        ShepherdTalk.reply(player, ShepherdTalk.liveId(), CONTINUE);
                    })
                    .thenExecuteAfter(15, () -> {
                        assertPrompt(h, null);
                        h.assertTrue(data(h).affinity() == 280, "Her answer scored: " + data(h).affinity());
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_name_first", timeoutTicks = 400)
    public static void shepherdNotBeforeTheFirstMeetingIsDone(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            settled(h, player(h), 260);
            data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, false);
            h.startSequence().thenExecuteAfter(15, () -> assertPrompt(h, null)).thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_name_wary", timeoutTicks = 400)
    public static void shepherdAtWarySheOffersNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            settled(h, player(h), -300);
            h.startSequence().thenExecuteAfter(15, () -> assertPrompt(h, null)).thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_name_falling", timeoutTicks = 400)
    public static void shepherdAFallingScoreLeavesTheNameUnasked(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            settled(h, player(h), 240);
            h.startSequence()
                    .thenExecuteAfter(15, () -> {
                        assertPrompt(h, null);
                        h.assertTrue(!data(h).flag(ShepherdPrompt.NAME_ASKED), "Not used up");
                        data(h).affinity(260);
                    })
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_NAME))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_name_withdrawn", timeoutTicks = 400)
    public static void shepherdAFallingScoreWithdrawsTheWaitingName(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            settled(h, player(h), 260);
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_NAME))
                    .thenExecute(() -> data(h).affinity(-300))
                    .thenWaitUntil(() -> assertPrompt(h, null))
                    .thenExecute(() -> data(h).affinity(260))
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_NAME))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_name_open", timeoutTicks = 400)
    public static void shepherdNotOverAnOpenScreen(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = settled(h, player, 240);
            she.interact(player, InteractionHand.MAIN_HAND);
            data(h).affinity(260);
            h.startSequence()
                    .thenExecuteAfter(15, () -> {
                        assertPrompt(h, null);
                        ShepherdTalk.close(player);
                    })
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_NAME))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_name_knows", timeoutTicks = 400)
    public static void shepherdTheNameKnowsIfYouAsked(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            ShepherdTalk.start(player, ShepherdPrompt.THE_NAME);
            String never = ShepherdTalk.liveLines().getFirst();
            ShepherdTalk.close(player);
            data(h).resumeNode(null);
            data(h).flag(ShepherdPrompt.ASKED_NAME, true);
            ShepherdTalk.start(player, ShepherdPrompt.THE_NAME);
            h.assertTrue(!never.equals(ShepherdTalk.liveLines().getFirst()), "The first line differs: " + never);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_name_replay", timeoutTicks = 400)
    public static void shepherdReplayEarnsTheNameAgain(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            settled(h, player(h), 260);
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_NAME))
                    .thenExecute(() -> {
                        command(h, "fd shepherd replay");
                        h.assertTrue(!data(h).flag(ShepherdPrompt.NAME_ASKED), "Her asking goes with the rest");
                        assertPrompt(h, null);
                        data(h).resumeNode(null);
                        data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
                        data(h).affinity(260);
                    })
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_NAME))
                    .thenSucceed();
        });
    }

    /** Sleep and /time leave game time alone, so a playtest moves her clocks instead. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_skip_day", timeoutTicks = 400)
    public static void shepherdSkippingADayMovesHerClocks(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player);
            ShepherdReactions.saw(player, Deed.WARM_SHELTER);
            long ready = data(h).deedReady(Deed.WARM_SHELTER.name());
            data(h).lastNear(clock(h));
            long last = data(h).lastNear();
            command(h, "fd shepherd skipday");
            h.assertTrue(data(h).deedReady(Deed.WARM_SHELTER.name()) == ready - ShepherdPolicy.DAY_TICKS, "The cooldown is a day shorter");
            h.assertTrue(data(h).lastNear() == last, "Someone is near her, so nobody left her");
            she.setNoAi(true);
            she.teleportTo(player.getX() + ShepherdPolicy.HEARD_BLOCKS + 5, player.getY(), player.getZ());
            command(h, "fd shepherd skipday");
            h.assertTrue(data(h).lastNear() == last - ShepherdPolicy.DAY_TICKS, "Out of sight, the day counts as gone");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_react_heavy", timeoutTicks = 400)
    public static void shepherdHeavySwapsAReactionLine(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            awake(h, player);
            data(h).heavy(true);
            var swears = Deed.AIR_FAILING.lines().stream().filter(say -> say.heavyKey() != null).findFirst().orElseThrow();
            boolean heard = false;
            for (int i = 0; i < 60; i++) {
                data(h).deedReady(Deed.AIR_FAILING.name(), 0);
                ShepherdReactions.saw(player, Deed.AIR_FAILING);
                h.assertTrue(!swears.key().equals(ShepherdReactions.said()), "Never the mild form");
                heard |= swears.heavyKey().equals(ShepherdReactions.said());
            }
            h.assertTrue(heard, "The heavy form plays");
            h.succeed();
        });
    }
}
