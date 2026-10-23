package com.frozendawn.gametest;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.PacifistSavedData;
import com.frozendawn.data.WinConditionState;
import com.frozendawn.entity.RocketLaunchEntity;
import com.frozendawn.init.ModEntities;
import com.frozendawn.world.PacifistAdvancement;
import com.frozendawn.world.RocketLaunchManager;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PacifistLaunchGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void pacifistDirectKillSurvivesPlayerReplacement(GameTestHelper helper) {
        withScene(helper, scene -> {
            var killer = scene.player();
            var peaceful = scene.player();
            var returned = scene.mob(ModEntities.RETURNED.get());
            returned.hurt(scene.level().damageSources().playerAttack(killer), 10000);
            helper.assertTrue(returned.isDeadOrDying(), "Lethal player damage must really kill");
            helper.assertFalse(PacifistAdvancement.eligible(killer), "Real death event must disqualify killer");
            helper.assertTrue(PacifistAdvancement.eligible(peaceful), "Other players retain eligibility");
            var replacement = new ServerPlayer(scene.level().getServer(), scene.level(),
                    killer.getGameProfile(), ClientInformation.createDefault());
            helper.assertFalse(PacifistAdvancement.eligible(replacement), "New player instance with same UUID retains conduct");
            var ledger = PacifistSavedData.get(scene.level().getServer());
            var loaded = PacifistSavedData.load(ledger.save(new CompoundTag(), scene.level().registryAccess()),
                    scene.level().registryAccess());
            helper.assertTrue(loaded.disqualified(killer.getUUID()), "Saved ledger retains kill across reload");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void pacifistProjectilesExplosionsAndPetsAreAttributed(GameTestHelper helper) {
        withScene(helper, scene -> {
            var archer = scene.player();
            var arrow = new Arrow(EntityType.ARROW, scene.level());
            arrow.setOwner(archer);
            var target = scene.mob(ModEntities.UNDONE.get());
            target.hurt(scene.level().damageSources().arrow(arrow, archer), 10000);
            helper.assertFalse(PacifistAdvancement.eligible(archer), "Player arrow killing descendant must count");

            var bomber = scene.player();
            var explosive = new net.minecraft.world.entity.item.PrimedTnt(scene.level(), 0, 0, 0, bomber);
            scene.mob(ModEntities.MIMIC.get()).hurt(scene.level().damageSources().explosion(explosive, bomber), 10000);
            helper.assertFalse(PacifistAdvancement.eligible(bomber), "Player explosion must count");

            var owner = scene.player();
            Wolf wolf = scene.mob(EntityType.WOLF);
            wolf.tame(owner);
            scene.mob(ModEntities.BLOOMBOUND_UNDONE.get()).hurt(scene.level().damageSources().mobAttack(wolf), 10000);
            helper.assertFalse(PacifistAdvancement.eligible(owner), "Owned pet killing descendant must count");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void pacifistUnrelatedUnattributedAndCancelledDeathsAreAllowed(GameTestHelper helper) {
        withScene(helper, scene -> {
            var player = scene.player();
            scene.mob(EntityType.ZOMBIE).hurt(scene.level().damageSources().playerAttack(player), 10000);
            helper.assertTrue(PacifistAdvancement.eligible(player), "Unrelated combat is allowed");
            scene.mob(ModEntities.RETURNED.get()).hurt(scene.level().damageSources().generic(), 10000);
            helper.assertTrue(PacifistAdvancement.eligible(player), "Unattributed environment death is allowed");
            var spared = scene.mob(ModEntities.RETURNED.get());
            Consumer<LivingDeathEvent> cancel = event -> {
                if (event.getEntity() == spared) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(cancel);
            try {
                spared.hurt(scene.level().damageSources().playerAttack(player), 10000);
                helper.assertTrue(PacifistAdvancement.eligible(player), "Cancelled death cannot disqualify");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            for (var type : List.of(ModEntities.RETURNED.get(), ModEntities.MIMIC.get(), ModEntities.ARCHITECT.get(),
                    ModEntities.UNDONE.get(), ModEntities.BLOOMBOUND_UNDONE.get(), ModEntities.UNDONE_ARCHITECT.get(),
                    ModEntities.REMNANT.get(), ModEntities.AGGREGATE.get(), ModEntities.AGGREGATE_FRAGMENT.get(),
                    ModEntities.HEART_SUCCESSOR.get())) {
                helper.assertTrue(type.is(PacifistAdvancement.RETURNED_LINEAGE), "Returned lineage includes " + type);
            }
            for (var type : List.of(ModEntities.HOLLOW.get(), ModEntities.RESONANT.get(), ModEntities.FROSTBITTEN.get(),
                    ModEntities.RIMEBOUND.get(), ModEntities.FROSTMITE.get(), ModEntities.FROSTWRITHE.get())) {
                helper.assertFalse(type.is(PacifistAdvancement.RETURNED_LINEAGE), "Unrelated threat excluded: " + type);
            }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void pacifistHistoricalStatisticsPreventOldSaveAmnesty(GameTestHelper helper) {
        withScene(helper, scene -> {
            for (var type : BuiltInRegistries.ENTITY_TYPE) {
                if (!type.is(PacifistAdvancement.RETURNED_LINEAGE)) continue;
                var player = scene.player();
                player.getStats().setValue(player, Stats.ENTITY_KILLED.get(type), 1);
                helper.assertFalse(PacifistAdvancement.eligible(player), "Old recorded kill must exclude " + type);
                player.getStats().setValue(player, Stats.ENTITY_KILLED.get(type), 0);
                helper.assertFalse(PacifistAdvancement.eligible(player), "Seeded permanent conduct cannot be reset by stats");
            }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void pacifistSuccessfulLaunchAwardsOnlyEligiblePassenger(GameTestHelper helper) {
        withScene(helper, scene -> {
            var server = scene.level().getServer();
            var state = WinConditionState.get(server);
            var original = WinConditionState.load(state.save(new CompoundTag(), scene.level().registryAccess()),
                    scene.level().registryAccess());
            var advancement = server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, PacifistAdvancement.ID));
            helper.assertTrue(advancement != null, "Hidden advancement must load");
            var display = advancement.value().display().orElseThrow();
            helper.assertTrue(display.isHidden() && display.shouldShowToast() && !display.shouldAnnounceChat(),
                    "Hidden challenge has private completion toast");
            var clean = scene.player();
            var killer = scene.player();
            var bystander = scene.player();
            PacifistSavedData.get(server).recordKill(killer.getUUID());
            helper.assertFalse(clean.getAdvancements().getOrStartProgress(advancement).isDone(),
                    "No award before successful launch");
            try {
                state.setEndingTriggered(false);
                var rocket = new RocketLaunchEntity(scene.level(), helper.absolutePos(new net.minecraft.core.BlockPos(5, 3, 5)));
                scene.entities.add(rocket);
                scene.level().addFreshEntity(rocket);
                helper.assertTrue(clean.startRiding(rocket, true), "Actual rocket must accept eligible passenger");
                RocketLaunchManager.finishLaunch(scene.level(), rocket);
                helper.assertTrue(clean.getAdvancements().getOrStartProgress(advancement).isDone(),
                        "Successful launch grants actual advancement");
                helper.assertFalse(bystander.getAdvancements().getOrStartProgress(advancement).isDone(),
                        "Non-passenger is not awarded");
                helper.assertFalse(killer.getAdvancements().getOrStartProgress(advancement).isDone(),
                        "Other player's kill does not affect clean passenger");

                state.setEndingTriggered(false);
                var second = new RocketLaunchEntity(scene.level(), helper.absolutePos(new net.minecraft.core.BlockPos(5, 3, 5)));
                scene.entities.add(second);
                scene.level().addFreshEntity(second);
                helper.assertTrue(killer.startRiding(second, true), "Actual rocket must accept disqualified passenger");
                RocketLaunchManager.finishLaunch(scene.level(), second);
                helper.assertFalse(killer.getAdvancements().getOrStartProgress(advancement).isDone(),
                        "Disqualified passenger receives no pacifist advancement");
            } finally {
                server.overworld().getDataStorage().set("frozendawn_win_condition", original);
            }
        });
    }

    private static void withScene(GameTestHelper helper, Consumer<Scene> test) {
        var scene = new Scene(helper);
        try {
            test.accept(scene);
            helper.succeed();
        } finally {
            scene.entities.forEach(Entity::discard);
        }
    }

    private static final class Scene {
        final GameTestHelper helper;
        final List<Entity> entities = new ArrayList<>();
        Scene(GameTestHelper helper) { this.helper = helper; }
        net.minecraft.server.level.ServerLevel level() { return helper.getLevel(); }
        ServerPlayer player() {
            var profile = new GameProfile(UUID.randomUUID(), "pacifist_test");
            var transport = new FakePlayer(level(), profile);
            var player = new ServerPlayer(level().getServer(), level(), profile, ClientInformation.createDefault());
            player.connection = transport.connection;
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(3, 3, 3)).getCenter());
            level().addNewPlayer(player);
            entities.add(player);
            return player;
        }
        <T extends Mob> T mob(EntityType<T> type) {
            var mob = type.create(level());
            if (mob == null) throw new IllegalStateException("Could not create " + type);
            mob.setNoAi(true);
            mob.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(4, 3, 4)).getCenter());
            level().addFreshEntity(mob);
            entities.add(mob);
            return mob;
        }
    }
}
