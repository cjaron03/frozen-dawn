package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.entity.RimeboundBurrowController;
import com.frozendawn.entity.RimeboundEntity;
import com.frozendawn.entity.RimeboundPolicy;
import com.frozendawn.entity.RimeboundState;
import com.frozendawn.bloom.BloomGrowthManager;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModEffects;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Comparator;
import java.util.UUID;

/** Loaded-entity-only authority for the Archivist's three-minute death mark. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class MarkedPursuitManager {
    public static final int DURATION_TICKS = 3 * 60 * 20;
    public static final double RECRUIT_RADIUS = 500.0D;
    private static final double PRESSURE_RADIUS = 128.0D;
    private static final int INITIAL_PRESSURE_FLOOR = 14;
    private static final int WAVE_INTERVAL_TICKS = 80;
    private static final int GLOBAL_REINFORCEMENT_CAP = 48;
    private static final String MARK_TICKS_TAG = "frozendawnMarkedTicks";
    private static final String WAVE_COOLDOWN_TAG = "frozendawnMarkedWaveCooldown";
    private static final String PURSUIT_TARGET_TAG = "frozendawnMarkedTarget";
    private static final String REINFORCEMENT_TAG = "frozendawnMarkedReinforcement";
    private static final ResourceLocation FOLLOW_RANGE_ID =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "marked_follow_range");
    private static final TagKey<EntityType<?>> PURSUERS = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "marked_pursuers"));

    private MarkedPursuitManager() {
    }

    public static void apply(ServerPlayer player) {
        player.getPersistentData().putInt(MARK_TICKS_TAG, DURATION_TICKS);
        player.addEffect(new MobEffectInstance(
                ModEffects.MARKED, DURATION_TICKS, 0,
                false, false, true));
        player.serverLevel().playSound(null, player.blockPosition(),
                ModSounds.ARCHIVIST_MARKED.get(), SoundSource.HOSTILE,
                1.35F, 1.0F);
        WorldTickHandler.grantAdvancement(player, "now_all_of_china_knows");
        recruitLoadedPursuers(player);
        maintainPressure(player, INITIAL_PRESSURE_FLOOR, INITIAL_PRESSURE_FLOOR);
        player.getPersistentData().putInt(WAVE_COOLDOWN_TAG, WAVE_INTERVAL_TICKS);
    }

    public static boolean isMarked(ServerPlayer player) {
        return player.isAlive() && player.getPersistentData().getInt(MARK_TICKS_TAG) > 0;
    }

    private static void recruitLoadedPursuers(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB area = player.getBoundingBox().inflate(RECRUIT_RADIUS);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area,
                mob -> isPursuer(mob) && mob.distanceToSqr(player)
                        <= RECRUIT_RADIUS * RECRUIT_RADIUS)) {
            assign(mob, player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        int ticks = data.getInt(MARK_TICKS_TAG);
        if (ticks <= 0) {
            MobEffectInstance commanded = player.getEffect(ModEffects.MARKED);
            if (commanded == null) {
                return;
            }
            ticks = commanded.getDuration();
        }
        if (!player.isAlive()) {
            clearPlayerMark(player);
            return;
        }
        ticks--;
        if (ticks <= 0) {
            clearPlayerMark(player);
            return;
        }
        data.putInt(MARK_TICKS_TAG, ticks);
        if (!player.hasEffect(ModEffects.MARKED)) {
            player.addEffect(new MobEffectInstance(
                    ModEffects.MARKED, ticks, 0,
                    false, false, true));
        }
        int waveCooldown = data.getInt(WAVE_COOLDOWN_TAG) - 1;
        if (waveCooldown <= 0) {
            int elapsed = DURATION_TICKS - ticks;
            int cap = elapsed >= 2 * 60 * 20 ? 30
                    : elapsed >= 60 * 20 ? 24 : 18;
            maintainPressure(player, cap, 4 + player.getRandom().nextInt(4));
            waveCooldown = WAVE_INTERVAL_TICKS;
        }
        data.putInt(WAVE_COOLDOWN_TAG, waveCooldown);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob)
                || mob.level().isClientSide()
                || !isPursuer(mob)) {
            return;
        }
        CompoundTag data = mob.getPersistentData();
        UUID assigned = data.hasUUID(PURSUIT_TARGET_TAG)
                ? data.getUUID(PURSUIT_TARGET_TAG) : null;
        ServerPlayer target = assigned == null ? nearestMarkedPlayer(mob)
                : mob.getServer().getPlayerList().getPlayer(assigned);
        if (target == null) {
            if (assigned == null) {
                return;
            }
            stopWithoutForgetting(mob);
            return;
        }
        if (!isMarked(target)) {
            clearPursuit(mob, assigned);
            return;
        }
        if (target.level() != mob.level()) {
            stopWithoutForgetting(mob);
            return;
        }
        if (assigned == null) {
            assign(mob, target);
        } else {
            forcePursuit(mob, target);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearPlayerMark(player);
        }
    }

    @SubscribeEvent
    public static void onMilkFinished(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getItem().is(Items.MILK_BUCKET)
                && (isMarked(player) || player.hasEffect(ModEffects.MARKED))) {
            clearPlayerMark(player);
        }
    }

    private static ServerPlayer nearestMarkedPlayer(Mob mob) {
        return mob.level().players().stream()
                .filter(ServerPlayer.class::isInstance)
                .map(ServerPlayer.class::cast)
                .filter(MarkedPursuitManager::isMarked)
                .filter(player -> player.distanceToSqr(mob)
                        <= RECRUIT_RADIUS * RECRUIT_RADIUS)
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
    }

    private static boolean isPursuer(Mob mob) {
        return mob.isAlive() && mob.getType().is(PURSUERS);
    }

    private static void maintainPressure(ServerPlayer player, int cap, int waveLimit) {
        ServerLevel level = player.serverLevel();
        int active = activePressureCount(level, player);
        int requested = Math.min(Math.max(0, cap - active), waveLimit);
        int globalReinforcements = level.getEntitiesOfClass(
                Mob.class, new AABB(player.blockPosition()).inflate(512.0D),
                mob -> mob.getPersistentData().getBoolean(REINFORCEMENT_TAG)).size();
        requested = Math.min(requested,
                Math.max(0, GLOBAL_REINFORCEMENT_CAP - globalReinforcements));
        for (int index = 0; index < requested; index++) {
            spawnReinforcement(level, player, level.random);
        }
    }

    private static int activePressureCount(ServerLevel level, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(PRESSURE_RADIUS), mob -> {
                    CompoundTag data = mob.getPersistentData();
                    return mob.isAlive() && data.hasUUID(PURSUIT_TARGET_TAG)
                            && data.getUUID(PURSUIT_TARGET_TAG).equals(playerId);
                }).size();
    }

    private static boolean spawnReinforcement(
            ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos spawn = LateThreatSpawnHelper.findUnrestrictedHybridSpawn(
                level, player, random, 18, 44, 32,
                LateThreatSpawnHelper.NO_LIGHT_LIMIT);
        if (spawn == null) {
            return false;
        }
        EntityType<? extends Mob> type = selectReinforcementType(random,
                DURATION_TICKS - player.getPersistentData().getInt(MARK_TICKS_TAG));
        if (type == ModEntities.FROSTBITTEN.get()
                && FrozenDawnConfig.ENABLE_RIMEBOUND.get()
                && RimeboundBurrowController.validDormantTerrain(level, spawn)) {
            float evolutionChance = RimeboundPolicy.evolutionChance(
                    RimeboundManager.ticksSinceErasure(level),
                    BloomGrowthManager.pressureMultiplier(level, spawn),
                    FrozenDawnConfig.RIMEBOUND_EVOLUTION_SHARE_MULTIPLIER.get());
            int nearby = level.getEntitiesOfClass(RimeboundEntity.class,
                    new AABB(spawn).inflate(64.0D)).size();
            if (nearby < FrozenDawnConfig.RIMEBOUND_NEARBY_CAP.get()
                    && random.nextFloat() < evolutionChance) {
                type = ModEntities.RIMEBOUND.get();
            }
        }
        Mob mob = type.create(level, null, spawn, MobSpawnType.EVENT, true, false);
        if (mob == null) {
            return false;
        }
        mob.getPersistentData().putBoolean(REINFORCEMENT_TAG, true);
        if (mob instanceof FrostbittenEntity frostbitten) {
            frostbitten.setEmerging(true);
        } else if (mob instanceof RimeboundEntity rimebound) {
            rimebound.setActivityState(RimeboundState.STALKING);
        }
        if (!level.addFreshEntity(mob)) {
            mob.discard();
            return false;
        }
        assign(mob, player);
        level.sendParticles(ParticleTypes.WHITE_ASH,
                mob.getX(), mob.getY() + 0.9D, mob.getZ(),
                18, 0.45D, 0.8D, 0.45D, 0.035D);
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                mob.getX(), mob.getY() + 1.0D, mob.getZ(),
                12, 0.35D, 0.65D, 0.35D, 0.045D);
        return true;
    }

    private static EntityType<? extends Mob> selectReinforcementType(
            RandomSource random, int elapsedTicks) {
        float roll = random.nextFloat();
        if (elapsedTicks < 60 * 20) {
            if (roll < 0.48F) return ModEntities.FROSTBITTEN.get();
            if (roll < 0.73F) return ModEntities.HOLLOW.get();
            if (roll < 0.88F) return ModEntities.UNDONE.get();
            return ModEntities.MIMIC.get();
        }
        if (elapsedTicks < 2 * 60 * 20) {
            if (roll < 0.35F) return ModEntities.FROSTBITTEN.get();
            if (roll < 0.55F) return ModEntities.HOLLOW.get();
            if (roll < 0.73F) return ModEntities.UNDONE.get();
            if (roll < 0.86F) return ModEntities.RETURNED.get();
            if (roll < 0.95F) return ModEntities.MIMIC.get();
            return ModEntities.ARCHITECT.get();
        }
        if (roll < 0.28F) return ModEntities.FROSTBITTEN.get();
        if (roll < 0.45F) return ModEntities.HOLLOW.get();
        if (roll < 0.63F) return ModEntities.UNDONE.get();
        if (roll < 0.76F) return ModEntities.RETURNED.get();
        if (roll < 0.86F) return ModEntities.MIMIC.get();
        if (roll < 0.95F) return ModEntities.ARCHITECT.get();
        return ModEntities.UNDONE_ARCHITECT.get();
    }

    private static void assign(Mob mob, ServerPlayer player) {
        mob.getPersistentData().putUUID(PURSUIT_TARGET_TAG, player.getUUID());
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.addOrUpdateTransientModifier(new AttributeModifier(
                    FOLLOW_RANGE_ID, RECRUIT_RADIUS,
                    AttributeModifier.Operation.ADD_VALUE));
        }
        forcePursuit(mob, player);
    }

    private static void forcePursuit(Mob mob, ServerPlayer player) {
        mob.setTarget(player);
        mob.setSprinting(true);
        if (mob.tickCount % 5 == 0 || mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(player, 1.22D);
        }
    }

    private static void stopWithoutForgetting(Mob mob) {
        if (mob.getTarget() instanceof ServerPlayer) {
            mob.setTarget(null);
        }
        mob.getNavigation().stop();
        mob.setSprinting(false);
    }

    private static void clearPursuit(Mob mob, UUID targetId) {
        mob.getPersistentData().remove(PURSUIT_TARGET_TAG);
        if (mob.getTarget() != null && mob.getTarget().getUUID().equals(targetId)) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        mob.setSprinting(false);
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.removeModifier(FOLLOW_RANGE_ID);
        }
    }

    private static void clearPlayerMark(ServerPlayer player) {
        player.getPersistentData().remove(MARK_TICKS_TAG);
        player.getPersistentData().remove(WAVE_COOLDOWN_TAG);
        player.removeEffect(ModEffects.MARKED);
    }
}
