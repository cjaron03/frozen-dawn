package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.event.PlayerTickHandler;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.phase.PhaseManager;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdAmbient.Trigger;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
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
    @BeforeBatch(batch = "shepherd_face_watch") public static void watchFaceBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_face_watch") public static void watchFaceAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_face_wall") public static void wallFaceBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_face_wall") public static void wallFaceAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_face_flinch") public static void flinchFaceBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_face_flinch") public static void flinchFaceAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_face_glad") public static void gladFaceBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_face_glad") public static void gladFaceAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_face_wary") public static void waryFaceBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_face_wary") public static void waryFaceAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_face_free") public static void freeFaceBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_face_free") public static void freeFaceAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_ambient_fight") public static void fightAmbientBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_ambient_fight") public static void fightAmbientAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_ambient_gap") public static void gapAmbientBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_ambient_gap") public static void gapAmbientAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_ambient_reaction") public static void reactionAmbientBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_ambient_reaction") public static void reactionAmbientAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_ambient_wary") public static void waryAmbientBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_ambient_wary") public static void waryAmbientAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_ambient_talk") public static void talkAmbientBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_ambient_talk") public static void talkAmbientAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_ambient_silence") public static void silenceAmbientBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_ambient_silence") public static void silenceAmbientAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_ambient_early") public static void earlyAmbientBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_ambient_early") public static void earlyAmbientAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_ambient_aurora") public static void auroraAmbientBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_ambient_aurora") public static void auroraAmbientAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_ambient_blizzard") public static void blizzardAmbientBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_ambient_blizzard") public static void blizzardAmbientAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_egg_war") public static void eggWarBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_egg_war") public static void eggWarAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_egg_swarm") public static void eggSwarmBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_egg_swarm") public static void eggSwarmAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_egg_three") public static void eggThreeBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_egg_three") public static void eggThreeAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_egg_boom") public static void eggBoomBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_egg_boom") public static void eggBoomAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_egg_resupply") public static void eggResupplyBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_egg_resupply") public static void eggResupplyAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_egg_part") public static void eggPartBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_egg_part") public static void eggPartAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_egg_fish") public static void eggFishBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_egg_fish") public static void eggFishAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_egg_nofish") public static void eggNofishBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_egg_nofish") public static void eggNofishAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_lore_wall") public static void loreWallBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_lore_wall") public static void loreWallAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_lore_none") public static void loreNoneBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_lore_none") public static void loreNoneAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_lore_erased") public static void loreErasedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_lore_erased") public static void loreErasedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_egg_replay") public static void eggReplayBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_egg_replay") public static void eggReplayAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_fix_unasked") public static void fixUnaskedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_fix_unasked") public static void fixUnaskedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_fix_withdrawn") public static void fixWithdrawnBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_fix_withdrawn") public static void fixWithdrawnAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_fix_died") public static void fixDiedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_fix_died") public static void fixDiedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_fix_stale") public static void fixStaleBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_fix_stale") public static void fixStaleAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_fix_residents") public static void fixResidentsBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_fix_residents") public static void fixResidentsAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_fix_hunted") public static void fixHuntedBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_fix_hunted") public static void fixHuntedAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_fix_asking") public static void fixAskingBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_fix_asking") public static void fixAskingAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_fix_stranger") public static void fixStrangerBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_fix_stranger") public static void fixStrangerAfter(ServerLevel l) { after(l); }
    @BeforeBatch(batch = "shepherd_fix_skipday") public static void fixSkipdayBefore(ServerLevel l) { before(l); }
    @AfterBatch(batch = "shepherd_fix_skipday") public static void fixSkipdayAfter(ServerLevel l) { after(l); }

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
            // Her feeling would hold the same face past the reaction, so this takes it away to see the reaction end.
            MillerPattern.reset(h.getLevel().getServer());
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

    private static Zombie zombieBeside(GameTestHelper h) {
        return h.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(16.5, 4, 10.5));
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_face_watch", timeoutTicks = 400)
    public static void shepherdSheWatchesAHostile(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = awake(h, player(h));
            var zombie = zombieBeside(h);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(she.watching() == zombie, "Turned to it"))
                    .thenExecute(() -> assertFace(h, she, Mood.ALARMED))
                    .thenExecute(zombie::discard)
                    .thenWaitUntil(() -> h.assertTrue(she.watching() == null, "It has gone"))
                    .thenWaitUntil(() -> assertFace(h, she, Mood.NEUTRAL))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_face_wall", timeoutTicks = 400)
    public static void shepherdSheDoesNotWatchThroughAWall(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = awake(h, player(h));
            she.setNoAi(true);
            for (int y = 0; y <= 9; y++) for (int z = 2; z <= 18; z++) h.setBlock(new BlockPos(14, y, z), Blocks.STONE);
            zombieBeside(h);
            h.startSequence()
                    .thenExecuteAfter(15, () -> {
                        h.assertTrue(she.watching() == null, "She saw nothing");
                        assertFace(h, she, Mood.NEUTRAL);
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_face_flinch", timeoutTicks = 400)
    public static void shepherdSheFlinchesWhenYouAreHurt(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            player.setGameMode(GameType.SURVIVAL);
            var she = awake(h, player);
            h.startSequence()
                    .thenExecuteAfter(2, () -> {
                        player.hurt(h.getLevel().damageSources().genericKill(), 1);
                        assertFace(h, she, Mood.ALARMED);
                    })
                    .thenExecuteAfter(ShepherdPolicy.FLINCH_TICKS + 5, () -> assertFace(h, she, Mood.NEUTRAL))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_face_glad", timeoutTicks = 400)
    public static void shepherdSheIsGladYouAreBack(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = awake(h, player(h));
            data(h).lastNear(clock(h) - ShepherdPolicy.GLAD_AWAY_TICKS - ShepherdReactions.POLL_TICKS);
            h.succeedWhen(() -> assertFace(h, she, Mood.HAPPY));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_face_wary", timeoutTicks = 400)
    public static void shepherdAtWarySheLooksButHerFaceHolds(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = awake(h, player(h));
            data(h).affinity(-300);
            var zombie = zombieBeside(h);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(she.watching() == zombie, "Turned to it"))
                    .thenExecuteAfter(2, () -> assertFace(h, she, Mood.RECORDING))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_face_free", timeoutTicks = 400)
    public static void shepherdFacesMoveNoScore(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            player.setGameMode(GameType.SURVIVAL);
            var she = awake(h, player);
            zombieBeside(h);
            data(h).lastNear(clock(h) - ShepherdPolicy.GLAD_AWAY_TICKS - ShepherdReactions.POLL_TICKS);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(she.watching() != null, "Turned to it"))
                    .thenExecute(() -> player.hurt(h.getLevel().damageSources().genericKill(), 1))
                    .thenExecuteAfter(ShepherdReactions.POLL_TICKS + 5, () -> assertScore(h, 0))
                    .thenSucceed();
        });
    }

    private static ShepherdEntity met(GameTestHelper h, ServerPlayer player) {
        return settled(h, player, 0);
    }

    private static void assertSaidFrom(GameTestHelper h, Trigger trigger) {
        var keys = ShepherdAmbients.pools(trigger).stream().flatMap(pool -> pool.variants().stream()).map(ShepherdAffinity.Say::key).toList();
        h.assertTrue(keys.contains(ShepherdReactions.said()), trigger + ": " + ShepherdReactions.said());
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_ambient_fight", timeoutTicks = 400)
    public static void shepherdAFightEarnsALine(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            var zombie = zombieBeside(h);
            zombie.hurt(h.getLevel().damageSources().playerAttack(player), 1000);
            h.succeedWhen(() -> assertSaidFrom(h, Trigger.AFTER_FIGHT));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_ambient_gap", timeoutTicks = 400)
    public static void shepherdTheGapHolds(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            h.assertTrue(ShepherdReactions.murmur(h.getLevel(), Trigger.LOOKED_HEATER), "The first speaks");
            h.assertTrue(!ShepherdReactions.murmur(h.getLevel(), Trigger.LOOKED_STORAGE), "The second waits");
            data(h).lastAmbient(clock(h) - ShepherdPolicy.AMBIENT_GAP_TICKS);
            h.assertTrue(ShepherdReactions.murmur(h.getLevel(), Trigger.LOOKED_STORAGE), "Then it can");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_ambient_reaction", timeoutTicks = 400)
    public static void shepherdAReactionIgnoresTheGap(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            ShepherdReactions.murmur(h.getLevel(), Trigger.LOOKED_HEATER);
            String murmured = ShepherdReactions.said();
            ShepherdReactions.saw(player, Deed.CALLED_SHEPHERD);
            h.assertTrue(!murmured.equals(ShepherdReactions.said()), "A reaction still plays: " + ShepherdReactions.said());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_ambient_wary", timeoutTicks = 400)
    public static void shepherdNoCommentaryAtWary(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            data(h).affinity(-300);
            h.assertTrue(!ShepherdReactions.murmur(h.getLevel(), Trigger.LOOKED_HEATER), "She keeps it formal");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_ambient_talk", timeoutTicks = 400)
    public static void shepherdNoCommentaryWhileTalking(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = met(h, player);
            she.interact(player, InteractionHand.MAIN_HAND);
            h.assertTrue(!ShepherdReactions.murmur(h.getLevel(), Trigger.LOOKED_HEATER), "She is silent while the screen is open");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_ambient_silence", timeoutTicks = 400)
    public static void shepherdSheSpeaksUpAfterALongSilence(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            data(h).lastTalk(clock(h) - ShepherdPolicy.SILENCE_TICKS);
            h.succeedWhen(() -> assertSaidFrom(h, Trigger.SILENCE));
        });
    }

    /** A test's roof hides the sky unless it asks for it. */
    private static void nightIn(GameTestHelper h, int phase) {
        var state = ApocalypseState.get(h.getLevel().getServer());
        long day = PhaseManager.getPhaseStartDay(phase, state.getTotalDays());
        h.getLevel().setDayTime(day * 24000 + 18000);
        state.setApocalypseTicks(h.getLevel().getDayTime(), h.getLevel().getServer());
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_ambient_early", timeoutTicks = 400, skyAccess = true)
    public static void shepherdNoImaginedAuroraBeforePhaseFour(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            nightIn(h, 3);
            met(h, player(h));
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(ShepherdReactions.said() != null, "Something is said"))
                    .thenExecute(() -> {
                        h.assertTrue(ApocalypseState.get(h.getLevel().getServer()).getPhase() == 3, "The world's clock holds");
                        assertSaidFrom(h, Trigger.NIGHT);
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_ambient_aurora", timeoutTicks = 400, skyAccess = true)
    public static void shepherdFromPhaseFourSheImaginesTheAurora(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            nightIn(h, 4);
            met(h, player(h));
            h.succeedWhen(() -> {
                h.assertTrue(ApocalypseState.get(h.getLevel().getServer()).getPhase() == 4, "The world's clock holds");
                assertSaidFrom(h, Trigger.IMAGINED_AURORA);
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_ambient_blizzard", timeoutTicks = 400, skyAccess = true)
    public static void shepherdNoImaginedAuroraInABlizzard(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            nightIn(h, 5);
            met(h, player(h));
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(ShepherdReactions.said() != null, "Something is said"))
                    .thenExecute(() -> {
                        h.assertTrue(ApocalypseState.get(h.getLevel().getServer()).getPhase() == 5, "The world's clock holds");
                        h.assertTrue(!ShepherdReactions.said().contains("imagined_aurora"), "Not through that: " + ShepherdReactions.said());
                    })
                    .thenSucceed();
        });
    }

    /** Its line plays, then does not play again however long the gap has run. */
    private static void assertOnce(GameTestHelper h, String line) {
        h.startSequence()
                .thenWaitUntil(() -> assertSaid(h, line))
                .thenExecute(() -> {
                    ShepherdReactions.clearForTests();
                    data(h).lastAmbient(ShepherdSavedData.NOT_SET);
                })
                .thenExecuteAfter(ShepherdReactions.POLL_TICKS + 10, () -> h.assertTrue(!line.equals(ShepherdReactions.said()), "Only once"))
                .thenSucceed();
    }

    private static void assertSaid(GameTestHelper h, String line) {
        h.assertTrue(line.equals(ShepherdReactions.said()), "Said: " + ShepherdReactions.said());
    }

    private static void architectBeside(GameTestHelper h) {
        h.spawnWithNoFreeWill(ModEntities.ARCHITECT.get(), new Vec3(16.5, 4, 10.5));
    }

    private static void zombies(GameTestHelper h, int count) {
        for (int i = 0; i < count; i++) h.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(14.5 + i, 4, 10.5));
    }

    private static void boom(GameTestHelper h) {
        var at = h.absoluteVec(Vec3.atCenterOf(SPOT));
        h.getLevel().explode(null, at.x, at.y, at.z, 1, Level.ExplosionInteraction.NONE);
    }

    private static void say(ServerPlayer player, String text) {
        NeoForge.EVENT_BUS.post(new ServerChatEvent(player, text, Component.literal(text)));
    }

    private static void eat(ServerPlayer player, Item food) {
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, new ItemStack(food), 0, ItemStack.EMPTY));
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_egg_war", timeoutTicks = 400)
    public static void shepherdSheNamesTheWarOnce(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            architectBeside(h);
            assertOnce(h, ShepherdEggs.ARCHITECT_LINE);
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_egg_swarm", timeoutTicks = 400)
    public static void shepherdFourHostilesEarnALineOnce(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            zombies(h, ShepherdPolicy.SWARM_HOSTILES);
            assertOnce(h, ShepherdEggs.SWARM_LINE);
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_egg_three", timeoutTicks = 400)
    public static void shepherdFewerThanFourHostilesSayNothing(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            zombies(h, ShepherdPolicy.SWARM_HOSTILES - 1);
            h.startSequence()
                    .thenExecuteAfter(ShepherdReactions.POLL_TICKS + 10, () -> h.assertTrue(ShepherdReactions.said() == null, "Said: " + ShepherdReactions.said()))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_egg_boom", timeoutTicks = 400)
    public static void shepherdAnExplosionEarnsALineOnce(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            boom(h);
            assertSaid(h, ShepherdEggs.EXPLOSION_LINE);
            ShepherdReactions.clearForTests();
            data(h).lastAmbient(ShepherdSavedData.NOT_SET);
            boom(h);
            h.assertTrue(ShepherdReactions.said() == null, "Only once");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_egg_resupply", timeoutTicks = 400)
    public static void shepherdTheResupplyCodeIsAnsweredOnceADay(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            data(h).lastAmbient(clock(h));
            say(player, "Down Down Up Right");
            h.startSequence()
                    .thenWaitUntil(() -> assertSaid(h, ShepherdEggs.RESUPPLY_LINE))
                    .thenExecute(() -> {
                        ShepherdReactions.clearForTests();
                        say(player, "down down up right");
                    })
                    .thenIdle(5)
                    .thenExecute(() -> h.assertTrue(ShepherdReactions.said() == null, "Once a day"))
                    .thenExecute(() -> {
                        ShepherdSavedData.get(h.getLevel().getServer()).deedReady("egg.resupply", clock(h));
                        say(player, "down down up right");
                    })
                    .thenWaitUntil(() -> assertSaid(h, ShepherdEggs.RESUPPLY_LINE))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_egg_part", timeoutTicks = 400)
    public static void shepherdFiveMinutesStillPlaysTheRecordingOnce(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var she = met(h, player(h));
            she.stillFor(ShepherdPolicy.YOUR_PART_TICKS);
            assertOnce(h, ShepherdEggs.YOUR_PART_LINE);
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_egg_fish", timeoutTicks = 400)
    public static void shepherdRawFishIsNarratedOnceADay(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            data(h).flag("fm_narrated", true);
            eat(player, Items.COOKED_COD);
            h.assertTrue(ShepherdReactions.said() == null, "Cooked is fine");
            eat(player, Items.COD);
            assertSaid(h, ShepherdEggs.RAW_FISH_LINE);
            ShepherdReactions.clearForTests();
            data(h).lastAmbient(ShepherdSavedData.NOT_SET);
            eat(player, Items.SALMON);
            h.assertTrue(ShepherdReactions.said() == null, "Once a day");
            data(h).deedReady("egg.raw_fish", clock(h));
            eat(player, Items.SALMON);
            assertSaid(h, ShepherdEggs.RAW_FISH_LINE);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_egg_nofish", timeoutTicks = 400)
    public static void shepherdNoNarrationWithoutTheReply(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            eat(player, Items.COD);
            h.assertTrue(ShepherdReactions.said() == null, "Said: " + ShepherdReactions.said());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_lore_wall", timeoutTicks = 400)
    public static void shepherdTheWallIsHeldOnce(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            ShepherdEggs.wall(h.getLevel(), List.of(player), List.of(player.blockPosition().east(ShepherdPolicy.WALL_BLOCKS - 1)), false);
            assertSaid(h, ShepherdEggs.WALL_LINE);
            ShepherdReactions.clearForTests();
            data(h).lastAmbient(ShepherdSavedData.NOT_SET);
            ShepherdEggs.wall(h.getLevel(), List.of(player), List.of(player.blockPosition()), false);
            h.assertTrue(ShepherdReactions.said() == null, "Only once");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_lore_none", timeoutTicks = 400)
    public static void shepherdTheWallNeedsAHearth(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            ShepherdEggs.wall(h.getLevel(), List.of(player), List.of(), false);
            ShepherdEggs.wall(h.getLevel(), List.of(player), List.of(player.blockPosition().east(ShepherdPolicy.WALL_BLOCKS)), false);
            h.assertTrue(ShepherdReactions.said() == null, "Said: " + ShepherdReactions.said());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_lore_erased", timeoutTicks = 400)
    public static void shepherdTheWallStopsOnceMaeveIsErased(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            ShepherdEggs.wall(h.getLevel(), List.of(player), List.of(player.blockPosition()), true);
            h.assertTrue(ShepherdReactions.said() == null, "Said: " + ShepherdReactions.said());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_egg_replay", timeoutTicks = 400)
    public static void shepherdReplayResetsTheEggs(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            boom(h);
            assertSaid(h, ShepherdEggs.EXPLOSION_LINE);
            say(player, "down down up right");
            h.startSequence()
                    .thenWaitUntil(() -> assertSaid(h, ShepherdEggs.RESUPPLY_LINE))
                    .thenExecute(() -> {
                        command(h, "fd shepherd replay");
                        h.assertTrue(!data(h).flag(ShepherdEggs.EXPLOSION), "The flag goes");
                        h.assertTrue(clock(h) >= data(h).deedReady("egg.resupply"), "The day goes");
                        h.assertTrue(data(h).lastAmbient() == ShepherdSavedData.NOT_SET, "The gap goes");
                    })
                    .thenSucceed();
        });
    }

    private static void assertNothingSaidAfterAPoll(GameTestHelper h) {
        h.startSequence()
                .thenExecuteAfter(ShepherdReactions.POLL_TICKS + 10, () -> h.assertTrue(ShepherdReactions.said() == null, "Said: " + ShepherdReactions.said()))
                .thenSucceed();
    }

    private static void killAZombie(GameTestHelper h, ServerPlayer player) {
        zombieBeside(h).hurt(h.getLevel().damageSources().playerAttack(player), 1000);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_fix_unasked", timeoutTicks = 400)
    public static void shepherdTheUnaskedNameSurvivesAnEarlyClose(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = settled(h, player, 260);
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_NAME))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        h.assertTrue("t1.name.unasked".equals(ShepherdTalk.liveNode()), "Unasked: " + ShepherdTalk.liveNode());
                        ShepherdTalk.close(player);
                        she.interact(player, InteractionHand.MAIN_HAND);
                        ShepherdTalk.reply(player, ShepherdTalk.liveId(), 1);
                        assertPrompt(h, null);
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_fix_withdrawn", timeoutTicks = 400)
    public static void shepherdAnUnaskedNameClosedEarlyCanStillBeWithdrawn(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = settled(h, player, 260);
            h.startSequence()
                    .thenWaitUntil(() -> assertPrompt(h, ShepherdPrompt.THE_NAME))
                    .thenExecute(() -> {
                        she.interact(player, InteractionHand.MAIN_HAND);
                        ShepherdTalk.close(player);
                        data(h).affinity(0);
                    })
                    .thenWaitUntil(() -> assertPrompt(h, null))
                    .thenExecute(() -> h.assertTrue(data(h).resumeNode() == null, "Nothing left to resume: " + data(h).resumeNode()))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_fix_died", timeoutTicks = 400)
    public static void shepherdNoFightLineAfterADeath(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            killAZombie(h, player);
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, h.getLevel().damageSources().generic()));
            assertNothingSaidAfterAPoll(h);
        });
    }

    /** A test's clock is game time, so a wound clock would run a timed step at once, before any poll: this counts real ticks. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_fix_stale", timeoutTicks = 4000)
    public static void shepherdAnOldFightIsLeftAlone(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            killAZombie(h, player);
            int[] ticks = {0};
            h.startSequence()
                    .thenExecute(() -> wind(h, clock(h) + ShepherdPolicy.FIGHT_LINE_TICKS + 1))
                    .thenWaitUntil(() -> h.assertTrue(++ticks[0] > ShepherdReactions.POLL_TICKS + 10, "A poll has run"))
                    .thenExecute(() -> h.assertTrue(ShepherdReactions.said() == null, "Said: " + ShepherdReactions.said()))
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_fix_residents", timeoutTicks = 400)
    public static void shepherdTheWallIsNotPutOffByResidents(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            zombieBeside(h);
            ShepherdEggs.wall(h.getLevel(), List.of(player), List.of(player.blockPosition()), false);
            assertSaid(h, ShepherdEggs.WALL_LINE);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_fix_hunted", timeoutTicks = 400)
    public static void shepherdNoWallMidFight(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            met(h, player);
            zombieBeside(h).setTarget(player);
            ShepherdEggs.wall(h.getLevel(), List.of(player), List.of(player.blockPosition()), false);
            h.assertTrue(ShepherdReactions.said() == null, "Said: " + ShepherdReactions.said());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_fix_asking", timeoutTicks = 400)
    public static void shepherdNoCommentaryOverHerAsking(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h)).askFor(100);
            h.assertTrue(!ShepherdReactions.murmur(h.getLevel(), Trigger.LOOKED_HEATER), "The strip is hers already");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_fix_stranger", timeoutTicks = 400)
    public static void shepherdNoCommentaryBeforeTheFirstMeeting(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            awake(h, player(h));
            h.assertTrue(!ShepherdReactions.murmur(h.getLevel(), Trigger.LOOKED_HEATER), "She has not introduced herself");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_fix_skipday", timeoutTicks = 400)
    public static void shepherdASkippedDayCountsAsSilence(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            met(h, player(h));
            data(h).lastTalk(clock(h));
            command(h, "fd shepherd skipday");
            h.succeedWhen(() -> assertSaidFrom(h, Trigger.SILENCE));
        });
    }
}
