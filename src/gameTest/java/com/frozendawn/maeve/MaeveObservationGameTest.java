package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.aggregate.AggregateReinforcementManager;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.homo.HearthSelectionPolicy;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModEntities;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual damage/item-use hooks. Shared world state is restored within one server callback. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveObservationGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void maeveDamageRequiresWitnessAndSeparatesPlayers(GameTestHelper helper) {
        withScene(helper, 0, scene -> {
            var observer = scene.architect(2, 4);
            var player = scene.player("maeve_damage", 8, 4);
            scene.wall(true);
            helper.assertTrue(scene.hit(observer, player, true, 2), "Hidden projectile must really damage the Architect");
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Through-wall damage must not identify a player pattern");
            scene.wall(false);
            player.setGameMode(GameType.CREATIVE);
            scene.hit(observer, player, true, 2);
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Creative player must not teach beliefs");
            player.setGameMode(GameType.SURVIVAL);
            observer.setNoAi(true);
            scene.hit(observer, player, true, 2);
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Disabled observer must not teach beliefs");
            observer.setNoAi(false);
            player.setPos(scene.origin.getX() + 90, scene.origin.getY(), scene.origin.getZ() + 4);
            scene.hit(observer, player, true, 2);
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Remote shooter exceeds the 48-block observation boundary");
            player.setPos(scene.position(8, 4));

            Consumer<LivingIncomingDamageEvent> cancel = event -> {
                if (event.getEntity() == observer) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(cancel);
            try {
                helper.assertTrue(!scene.hit(observer, player, true, 2), "Cancellation must prevent actual damage");
                helper.assertTrue(scene.beliefs(player).isEmpty(), "Canceled damage cannot create evidence");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(scene.hit(observer, player, true, 2), "Visible projectile should apply damage");
            helper.assertTrue(scene.beliefs(player).getFirst().evidence() == 1, "Final damage event must reach Maeve");
            scene.hit(observer, player, true, 2);
            scene.hit(scene.architect(3, 4), player, true, 2);
            helper.assertTrue(scene.beliefs(player).getFirst().evidence() == 1, "Duplicate witnesses share one encounter");
            scene.hit(observer, player, false, 2);
            helper.assertTrue(scene.beliefs(player).getFirst().contradictions() == 1, "Melee supplies contradictory evidence");

            var second = scene.player("maeve_second", 8, 7);
            observer.setHealth(1);
            scene.hit(observer, second, true, 100);
            helper.assertTrue(!observer.isAlive(), "The last observed hit must actually be fatal");
            helper.assertTrue(scene.beliefs(second).getFirst().evidence() == 1, "Death cannot prevent fatal-hit upload");
            helper.assertTrue(scene.beliefs(player).getFirst().evidence() == 1, "Second player cannot alter the first profile");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void maeveRecoveryRequiresCompletedVisibleAction(GameTestHelper helper) {
        withScene(helper, 1, scene -> {
            var observer = scene.architect(2, 4);
            scene.architect(3, 5);
            var player = scene.player("maeve_recovery", 8, 4);
            player.setHealth(10);
            player.heal(2);
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Passive healing is not an observed recovery action");
            player.setItemInHand(InteractionHand.MAIN_HAND, scene.potion());
            player.startUsingItem(InteractionHand.MAIN_HAND);
            player.stopUsingItem();
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Interrupted consumption supplies no evidence");
            scene.wall(true);
            player.finish(scene.potion());
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Hidden completed potion supplies no evidence");
            scene.wall(false);
            player.finish(new ItemStack(Items.MILK_BUCKET));
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Unrelated consumption supplies no evidence");
            scene.roof(true);
            helper.assertTrue(!scene.level.canSeeSky(player.blockPosition()), "Fixture must actually provide cover");
            helper.assertTrue(observer.hasLineOfSight(player), "Cover must preserve the Architect's sightline");
            player.finish(scene.potion());
            var covered = scene.beliefs(player).getFirst();
            helper.assertTrue(covered.evidence() == 1 && covered.contradictions() == 0,
                    "Visible finished potion under cover supplies one supporting observation despite two witnesses");
            player.finish(new ItemStack(Items.GOLDEN_APPLE));
            helper.assertTrue(scene.beliefs(player).getFirst().evidence() == 1, "Repeated recovery is encounter-capped");
            scene.roof(false);
            helper.assertTrue(scene.level.canSeeSky(player.blockPosition()), "Fixture must expose the sky");
            player.finish(scene.potion());
            helper.assertTrue(scene.beliefs(player).getFirst().contradictions() == 1, "Open-sky recovery contradicts the hypothesis");
            helper.assertTrue(scene.beliefs(player).getFirst().provenance().size() == 3, "Both credited polarities and the latest verification retain actual provenance");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void maeveExcludesMastersAndProjections(GameTestHelper helper) {
        withScene(helper, 2, scene -> {
            var player = scene.player("maeve_roles", 8, 4);
            var copy = scene.architect(2, 4);
            copy.initializeMasterMindCopy(UUID.randomUUID(), 100, 100, 0);
            var reinforcement = scene.architect(3, 5);
            reinforcement.getPersistentData().putBoolean(AggregateReinforcementManager.CHILD_TAG, true);
            scene.hit(copy, player, true, 2);
            scene.hit(reinforcement, player, true, 2);
            player.finish(scene.potion());
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Copies and Aggregate children cannot publish observations");
            var master = scene.architect(3, 3);
            master.bindToHearthMasterArchitect(UUID.randomUUID(), scene.origin, 0);
            helper.assertTrue(scene.hit(master, player, true, 2), "A real Master must actually take the projectile hit");
            player.finish(scene.potion());
            master.setTarget(player);
            MaeveDirector.observePresence(master);
            helper.assertTrue(scene.beliefs(player).isEmpty()
                    && MaeveDirector.worldSnapshot(scene.server, player.getUUID()).isEmpty(),
                    "Masters neither publish combat/recovery observations nor build the spatial model");
            var ordinary = scene.architect(2, 3);
            helper.assertTrue(scene.hit(ordinary, player, true, 2), "Ordinary combat still works");
            player.finish(scene.potion());
            helper.assertTrue(scene.beliefs(player).size() == 2, "Ordinary Architects still report both actions");
            ordinary.setTarget(player); MaeveDirector.observePresence(ordinary);
            scene.hit(master, player, false, master.getMaxHealth() * .3F);
            helper.assertTrue(MaeveDirector.worldSnapshot(scene.server, player.getUUID()).stream().noneMatch(point -> point.label().equals("DANGER_ZONE")),
                    "A Master taking heavy damage cannot add a danger point to an existing ordinary player's model");
            helper.assertTrue(scene.beliefs(player).stream().flatMap(belief -> belief.provenance().stream())
                    .allMatch(e -> e.observer().equals(ordinary.getUUID())), "Only ordinary witnesses contribute provenance");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void maeveLifecycleErasesBeliefsAndPreservesConduct(GameTestHelper helper) {
        withScene(helper, 3, scene -> {
            var observer = scene.architect(2, 4);
            var player = scene.player("maeve_lifecycle", 8, 4);
            scene.phase.setApocalypseTicks(0, scene.server);
            MaeveDirector.erase(scene.server);
            scene.storage(new MaeveSavedData());
            scene.hit(observer, player, true, 2);
            helper.assertTrue(MaeveDirector.snapshot(scene.server, player.getUUID()).lifecycle().equals("DORMANT"),
                    "Early apocalypse cannot activate the store");
            scene.phase.setApocalypseTicks(scene.phase.getTotalDays() * 24000L, scene.server);
            scene.hit(observer, player, true, 2);
            helper.assertTrue(scene.beliefs(player).size() == 1, "Late Phase 6 activates recording");
            scene.phase.setApocalypseTicks(0, scene.server);
            helper.assertTrue(MaeveDirector.snapshot(scene.server, player.getUUID()).lifecycle().equals("ACTIVE"),
                    "Activation must be one-way even if the phase is moved backward");
            CompoundTag saved = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            scene.storage(MaeveSavedData.load(saved, scene.level.registryAccess()));
            scene.hit(observer, player, true, 2);
            helper.assertTrue(scene.beliefs(player).getFirst().evidence() == 1, "Reload must preserve encounter limits");

            var plan = HearthSelectionPolicy.createPlan(13, scene.origin);
            scene.hearths.applySelectionPlan(plan, 1);
            scene.hearths.markPlayerOrsathae(player.getUUID(), plan.major().id(), 2);
            var conduct = scene.hearths.relationship(player.getUUID());
            helper.assertTrue(PostMaeveWorldState.markErased(scene.level), "First erasure changes the authoritative state");
            helper.assertTrue(!PostMaeveWorldState.markErased(scene.level), "Repeated erasure is harmless");
            helper.assertTrue(MaeveDirector.snapshot(scene.server, player.getUUID()).lifecycle().equals("ERASED"), "Erasure is immediate");
            helper.assertTrue(MaeveSavedData.get(scene.server).store() == null, "Erase must release the tactical store");
            helper.assertTrue(!MaeveSavedData.get(scene.server).save(new CompoundTag(), null).contains("beliefs"), "No archived tactical state");
            helper.assertTrue(scene.hearths.relationship(player.getUUID()) == conduct, "Permanent conduct survives erasure");
            scene.hit(observer, player, false, 2);
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Post-erasure observations are rejected");
            PostMaeveWorldState.setForDebug(scene.server, false);
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Debug reversal starts empty");
            scene.hit(observer, player, true, 2);
            helper.assertTrue(scene.beliefs(player).size() == 1, "A reversed debug state can build new beliefs");
            FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.set(true);
            MaeveDirector.tick(scene.server);
            helper.assertTrue(MaeveSavedData.get(scene.server).store() == null, "Forced erased mode clears tactical memory");
            FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.set(false);
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Removing the override cannot restore beliefs");
            scene.storage(MaeveSavedData.load(saved, scene.level.registryAccess()));
            scene.hearths.markMaeveErased(3);
            helper.assertTrue(scene.beliefs(player).isEmpty(), "An authoritative erased world rejects stale on-disk beliefs");
            helper.assertTrue(scene.hearths.relationship(player.getUUID()) == conduct, "Load cleanup preserves permanent conduct");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void maeveExplainCommandsTraceEventsAndRespectErasure(GameTestHelper helper) {
        withScene(helper, 4, scene -> {
            var observer = scene.architect(2, 4);
            var player = scene.player("maeve_explain", 8, 4);
            scene.wall(true);
            player.finish(scene.potion());
            scene.wall(false);
            scene.hit(observer, player, true, 2);
            scene.hit(observer, player, false, 2);
            scene.roof(true);
            player.finish(scene.potion());
            scene.roof(false);
            player.finish(scene.potion());
            var before = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            List<String> output = new ArrayList<>();
            String ranged = "fd maeve explain " + BeliefStore.RANGED;
            String recovery = "fd maeve explain " + BeliefStore.RECOVERY;
            helper.assertTrue(command(scene, player, 2, output, ranged) == 1, "Player can explain their own belief");
            String report = String.join("\n", output);
            helper.assertTrue(report.contains("SUPPORT WITNESSED_PROJECTILE_DAMAGE")
                            && report.contains("CONTRADICTION WITNESSED_MELEE_DAMAGE")
                            && report.contains("observer=" + observer.getUUID()),
                    "Explanation must trace both polarities to the actual damage hooks and observer");
            output.clear();
            helper.assertTrue(command(scene, player, 2, output, recovery) == 1, "Recovery explanation succeeds");
            report = String.join("\n", output);
            helper.assertTrue(report.contains("SUPPORT RECOVERY_ITEM_FINISHED_UNDER_COVER")
                            && report.contains("CONTRADICTION RECOVERY_ITEM_FINISHED_OPEN_SKY")
                            && report.contains("EVIDENCE: 1 contributing encounters")
                            && report.contains("CONTRADICTIONS: 1 contributing encounters"),
                    "Visible recovery events explain the hypothesis; hidden use contributed nothing");
            output.clear();
            helper.assertTrue(command(scene, null, 2, output, ranged) == 0, "Console requires an explicit subject");
            helper.assertTrue(output.stream().anyMatch(line -> line.contains("Console use requires")), "Console receives actionable syntax");
            output.clear();
            helper.assertTrue(command(scene, null, 2, output, ranged + " " + player.getUUID()) == 1,
                    "Explicit UUID works without an online-name lookup");
            helper.assertTrue(output.stream().anyMatch(line -> line.contains("SUPPORT WITNESSED_PROJECTILE_DAMAGE")),
                    "Console explanation selects the requested profile");
            output.clear();
            helper.assertTrue(command(scene, player, 1, output, ranged) == -1, "Non-operators cannot parse the diagnostic route");
            helper.assertTrue(output.isEmpty(), "Denied command must not expose a belief");
            var other = scene.player("maeve_unseen", 8, 7);
            command(scene, other, 2, output, ranged);
            helper.assertTrue(output.stream().anyMatch(line -> line.contains("No retained belief")), "Default subject isolates two players");
            helper.assertTrue(before.equals(MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess())),
                    "Repeated command reads must not mutate persistent tactical state");
            PostMaeveWorldState.markErased(scene.level);
            output.clear();
            command(scene, player, 2, output, ranged);
            helper.assertTrue(output.equals(List.of("Maeve ERASED | profiles=0 beliefs=0")), "Erasure exposes no former events");
            PostMaeveWorldState.setForDebug(scene.server, false);
            output.clear();
            command(scene, player, 2, output, ranged);
            helper.assertTrue(output.stream().anyMatch(line -> line.contains("No retained belief")), "Debug reversal cannot restore an explanation");
        });
    }

    private static int command(Scene scene, TestPlayer player, int permission, List<String> output, String command) {
        var sink = new net.minecraft.commands.CommandSource() {
            public void sendSystemMessage(net.minecraft.network.chat.Component message) { output.add(message.getString()); }
            public boolean acceptsSuccess() { return true; }
            public boolean acceptsFailure() { return true; }
            public boolean shouldInformAdmins() { return false; }
        };
        var source = new net.minecraft.commands.CommandSourceStack(sink, scene.position(8, 4),
                net.minecraft.world.phys.Vec2.ZERO, scene.level, permission, "diagnostic-test",
                net.minecraft.network.chat.Component.literal("diagnostic-test"), scene.server, player);
        try {
            return scene.server.getCommands().getDispatcher().execute(command, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
            return -1;
        }
    }

    static void withScene(GameTestHelper helper, int lane, Consumer<Scene> exercise) {
        withScene(helper, lane, 1, exercise);
    }

    static void withScene(GameTestHelper helper, int lane, int radius, Consumer<Scene> exercise) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2048 + lane * 512, 100, 2048));
        var acquired = new ArrayList<net.minecraft.world.level.ChunkPos>();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            var chunk = new net.minecraft.world.level.ChunkPos((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z);
            if (!level.getForcedChunks().contains(chunk.toLong())) {
                level.setChunkForced(chunk.x, chunk.z, true);
                acquired.add(chunk);
            }
            level.getChunk(chunk.x, chunk.z);
        }
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo old,
                                         net.minecraft.gametest.framework.GameTestInfo next,
                                         net.minecraft.gametest.framework.GameTestRunner runner) { }
            private void release() { acquired.forEach(c -> level.setChunkForced(c.x, c.z, false)); }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo info,
                                   net.minecraft.gametest.framework.GameTestRunner runner) { release(); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo info,
                                   net.minecraft.gametest.framework.GameTestRunner runner) { release(); }
        });
        // Terrain generation finishes before entity sections become accessible. Allow normal
        // server ticks to finish loading them, before swapping any shared SavedData.
        helper.startSequence().thenWaitUntil(() -> {
            for (var chunk : acquired) {
                helper.assertTrue(level.isPositionEntityTicking(chunk.getMiddleBlockPosition(origin.getY()))
                                && level.areEntitiesLoaded(chunk.toLong()),
                        "Waiting for fixture entity sections: " + chunk);
            }
        }).thenExecute(() -> {
            try (Scene scene = new Scene(helper, origin)) { exercise.accept(scene); }
        }).thenSucceed();
    }

    static final class Scene implements AutoCloseable {
        final ServerLevel level;
        final MinecraftServer server;
        final BlockPos origin;
        final ApocalypseState previousPhase;
        final ReturnedHearthSavedData previousHearths;
        final MaeveSavedData previousMaeve;
        final ApocalypseState phase = new ApocalypseState();
        final ReturnedHearthSavedData hearths = new ReturnedHearthSavedData();
        final boolean forced;
        final long dayTime;
        final long gameTime;
        final List<Entity> entities = new ArrayList<>();
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

        Scene(GameTestHelper helper, BlockPos origin) {
            level = helper.getLevel();
            server = level.getServer();
            this.origin = origin;
            previousPhase = ApocalypseState.get(server);
            previousHearths = ReturnedHearthSavedData.get(server);
            previousMaeve = MaeveSavedData.get(server);
            dayTime = server.overworld().getDayTime();
            gameTime = server.overworld().getGameTime();
            forced = FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.get();
            FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.set(false);
            server.overworld().getDataStorage().set("frozendawn_apocalypse", phase);
            server.overworld().getDataStorage().set("frozendawn_returned_hearths", hearths);
            storage(new MaeveSavedData());
            phase.setApocalypseTicks(phase.getTotalDays() * 24000L, server);
            for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) block(x, -1, z, Blocks.STONE.defaultBlockState());
        }

        void clock(long tick) {
            ((net.minecraft.world.level.storage.ServerLevelData) server.overworld().getLevelData()).setGameTime(tick);
        }

        void storage(MaeveSavedData data) {
            MaeveDirector.onServerStopped(server);
            server.overworld().getDataStorage().set(MaeveSavedData.NAME, data);
        }

        Vec3 position(int x, int z) { return Vec3.atBottomCenterOf(origin.offset(x, 0, z)); }

        ArchitectEntity architect(int x, int z) {
            ArchitectEntity actor = ModEntities.ARCHITECT.get().create(level);
            actor.setPos(position(x, z));
            level.addFreshEntity(actor);
            entities.add(actor);
            if (level.getEntity(actor.getUUID()) != actor) {
                throw new IllegalStateException("Fixture witness is not in an accessible entity section: " + actor.blockPosition());
            }
            return actor;
        }

        TestPlayer player(String name, int x, int z) {
            TestPlayer player = new TestPlayer(level, name);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(position(x, z));
            level.addNewPlayer(player);
            entities.add(player);
            return player;
        }

        boolean hit(ArchitectEntity observer, TestPlayer player, boolean ranged, float amount) {
            observer.invulnerableTime = 0;
            Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
            return observer.hurt(ranged ? level.damageSources().arrow(arrow, player)
                    : level.damageSources().playerAttack(player), amount);
        }

        List<MaeveDirector.BeliefSnapshot> beliefs(TestPlayer player) {
            return MaeveDirector.snapshot(server, player.getUUID()).beliefs();
        }

        ItemStack potion() { return PotionContents.createItemStack(Items.POTION, Potions.HEALING); }

        void wall(boolean present) {
            for (int y = 0; y <= 4; y++) for (int z = 0; z <= 10; z++) {
                block(5, y, z, present ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
        }

        void roof(boolean present) {
            for (int x = 7; x <= 9; x++) for (int z = 3; z <= 5; z++) {
                block(x, 4, z, present ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            settleLight();
        }

        void settleLight() {
            // Keep SavedData isolation inside one callback, but let the real lighting worker
            // finish: setBlock does not update canSeeSky synchronously.
            var light = level.getChunkSource().getLightEngine();
            var pending = new ArrayList<java.util.concurrent.CompletableFuture<?>>();
            var changedChunks = new java.util.LinkedHashSet<net.minecraft.world.level.ChunkPos>();
            blocks.keySet().forEach(pos -> changedChunks.add(new net.minecraft.world.level.ChunkPos(pos)));
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                changedChunks.add(new net.minecraft.world.level.ChunkPos((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z));
            }
            // Wider fixtures can place their roof beyond the central 3x3 chunks.
            // Await every edited chunk before using sky visibility as evidence.
            changedChunks.forEach(chunk -> pending.add(light.waitForPendingTasks(chunk.x, chunk.z)));
            var complete = java.util.concurrent.CompletableFuture.allOf(pending.toArray(java.util.concurrent.CompletableFuture[]::new))
                    .orTimeout(5, java.util.concurrent.TimeUnit.SECONDS);
            server.managedBlock(() -> {
                light.tryScheduleUpdate();
                return complete.isDone();
            });
            complete.join();
        }

        void block(int x, int y, int z, BlockState state) {
            BlockPos pos = origin.offset(x, y, z);
            blocks.putIfAbsent(pos, level.getBlockState(pos));
            level.setBlock(pos, state, 3);
        }

        @Override
        public void close() {
            entities.forEach(Entity::discard);
            blocks.forEach((pos, state) -> level.setBlock(pos, state, 3));
            FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.set(forced);
            server.overworld().setDayTime(dayTime);
            clock(gameTime);
            server.overworld().getDataStorage().set("frozendawn_apocalypse", previousPhase);
            server.overworld().getDataStorage().set("frozendawn_returned_hearths", previousHearths);
            storage(previousMaeve);
        }
    }

    static class TestPlayer extends FakePlayer {
        TestPlayer(ServerLevel level, String name) { super(level, new GameProfile(UUID.randomUUID(), name)); }

        void finish(ItemStack stack) {
            setItemInHand(InteractionHand.MAIN_HAND, stack);
            startUsingItem(InteractionHand.MAIN_HAND);
            completeUsingItem();
        }
    }
}
