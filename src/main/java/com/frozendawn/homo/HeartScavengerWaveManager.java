package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.entity.FrostmiteEntity;
import com.frozendawn.entity.HeartSuccessorEntity;
import com.frozendawn.entity.HollowEntity;
import com.frozendawn.entity.MimicEntity;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.joml.Vector3f;

/** Server-owned scavenger waves and the node-bound Orsathae-vaen focal point. */
public final class HeartScavengerWaveManager {
    private static final String SCAVENGER_TAG_PREFIX = "frozendawn_heart_scavenger_";
    private static final double ARENA_RADIUS = 112.0D;
    private static final double SPAWN_MIN_RADIUS = 46.0D;
    private static final double SPAWN_MAX_RADIUS = 58.0D;
    private static final int TARGET_REFRESH_TICKS = 10;
    private static final int SUMMON_TRAIL_TICKS = 18;
    private static final float NEAR_PLAYER_FROSTBITTEN_CHANCE = 0.22F;
    private static final DustParticleOptions HEALING_TETHER_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.58F, 0.08F), 0.85F);
    private static final ResourceLocation SUPPORT_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "heart_successor_support_speed");
    private static final ResourceLocation SUPPORT_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "heart_successor_support_damage");
    private static final List<SummonTrail> SUMMON_TRAILS = new ArrayList<>();

    private static long wavesSpawned;
    private static long scavengersSpawned;
    private static long successorsSpawned;
    private static String lastFailure = "none";

    private HeartScavengerWaveManager() {
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        tickSummonTrails(level);
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null) {
            return;
        }
        BlockPos anchor = hearth.heartAnchor().orElse(hearth.center());
        if (!level.hasChunkAt(anchor)) {
            return;
        }
        int destroyed = HeartLattice.destroyedCount(hearth.heartDestroyedNodeMask());
        if (!hearth.heartLive() || destroyed >= HeartLattice.NODE_COUNT) {
            reconcileSuccessor(level, data, hearth, anchor, false);
            return;
        }

        HeartScavengerPolicy.Profile profile = HeartScavengerPolicy.profile(
                destroyed, hearth.heartFieldStrength());
        if (profile.active() && hasParticipatingPlayer(level, anchor)) {
            announceSwarm(level, data, hearth, anchor);
            reconcileWaves(level, data, hearth, anchor, profile);
        }
        HeartSuccessorEntity successor = reconcileSuccessor(
                level, data, hearth, anchor,
                HeartSuccessorPolicy.shouldExist(hearth.heartDestroyedNodeMask()));
        if (level.getGameTime() % TARGET_REFRESH_TICKS == 0L) {
            directEncounter(level, hearth, anchor, successor);
        }
        if (successor != null) {
            tickSuccessor(level, data, hearth, anchor, successor);
        }
    }

    public static boolean isHeartScavenger(@Nullable Entity entity, UUID hearthId) {
        return entity != null && hearthId != null
                && entity.getTags().contains(scavengerTag(hearthId));
    }

    public static boolean isSuccessorSupported(LivingEntity entity) {
        AttributeInstance movement = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        return movement != null && movement.hasModifier(SUPPORT_SPEED_ID);
    }

    public static String statusLine() {
        return "waves=" + wavesSpawned + " scavengers=" + scavengersSpawned
                + " successors=" + successorsSpawned + " failure=" + lastFailure;
    }

    public static void endEncounter(
            ServerLevel level, UUID hearthId, BlockPos anchor) {
        if (!level.hasChunkAt(anchor)) {
            return;
        }
        activeScavengers(level, hearthId, anchor).forEach(Entity::discard);
        level.getEntitiesOfClass(
                        HeartSuccessorEntity.class,
                        new AABB(anchor).inflate(72.0D, 80.0D, 72.0D),
                        entity -> entity.hearthId().map(hearthId::equals).orElse(false))
                .forEach(successor -> {
                    clearSupportBuffs(level, successor);
                    successor.discard();
                });
        SUMMON_TRAILS.removeIf(trail -> hearthId.equals(trail.hearthId()));
    }

    public static String describe(ServerLevel level) {
        ReturnedHearthSavedData.HearthRecord hearth = ReturnedHearthSavedData
                .get(level.getServer())
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null) {
            return "no-major-hearth";
        }
        BlockPos anchor = hearth.heartAnchor().orElse(hearth.center());
        int active = level.hasChunkAt(anchor)
                ? activeScavengers(level, hearth.id(), anchor).size() : -1;
        return "active=" + (active < 0 ? "unloaded" : active)
                + " announced=" + hearth.heartSwarmAnnounced()
                + " nextWave=" + hearth.heartScavengerNextWaveGameTime()
                + " successor=" + hearth.heartSuccessorEntityId()
                .map(HeartScavengerWaveManager::shortId).orElse("none")
                + " successorGen=" + hearth.heartSuccessorGeneration()
                + " successorAt=" + hearth.heartSuccessorRespawnGameTime();
    }

    public static void reset() {
        wavesSpawned = 0L;
        scavengersSpawned = 0L;
        successorsSpawned = 0L;
        SUMMON_TRAILS.clear();
        lastFailure = "none";
    }

    private static void reconcileWaves(
            ServerLevel level, ReturnedHearthSavedData data,
            ReturnedHearthSavedData.HearthRecord hearth, BlockPos anchor,
            HeartScavengerPolicy.Profile profile) {
        long now = level.getGameTime();
        if (hearth.heartScavengerNextWaveGameTime() < 0L) {
            data.scheduleHeartScavengerWave(hearth.id(), now);
        }
        if (now < hearth.heartScavengerNextWaveGameTime()) {
            return;
        }
        List<Mob> active = activeScavengers(level, hearth.id(), anchor);
        int available = Math.max(0, profile.concurrentCap() - active.size());
        int requested = Math.min(profile.waveSize(), available);
        int spawned = 0;
        for (int index = 0; index < requested; index++) {
            if (spawnScavenger(level, hearth, anchor, profile)) {
                spawned++;
            }
        }
        int jitter = level.random.nextInt(Math.max(1, profile.intervalTicks() / 4));
        data.scheduleHeartScavengerWave(
                hearth.id(), now + profile.intervalTicks() + jitter);
        if (spawned > 0) {
            wavesSpawned++;
            scavengersSpawned += spawned;
            FrozenDawn.LOGGER.info(
                    "Heart scavenger wave at Hearth {}: nodes={} spawned={} active={}/{} swarm={}",
                    shortId(hearth.id()),
                    HeartLattice.destroyedCount(hearth.heartDestroyedNodeMask()),
                    spawned, active.size() + spawned, profile.concurrentCap(),
                    profile.swarm());
        }
    }

    private static boolean spawnScavenger(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor, HeartScavengerPolicy.Profile profile) {
        HeartScavengerPolicy.SpawnKind kind = HeartScavengerPolicy.selectKind(
                profile, level.random.nextFloat());
        BlockPos spawn = kind == HeartScavengerPolicy.SpawnKind.FROSTBITTEN
                && level.random.nextFloat() < NEAR_PLAYER_FROSTBITTEN_CHANCE
                ? findNearPlayerSpawn(level, anchor, level.random)
                : null;
        if (spawn == null) {
            spawn = findLoadedSpawn(level, anchor, level.random);
        }
        if (spawn == null) {
            lastFailure = "no-loaded-summon-space";
            return false;
        }
        Mob mob = switch (kind) {
            case FROSTBITTEN -> ModEntities.FROSTBITTEN.get().create(
                    level, null, spawn, MobSpawnType.EVENT, true, false);
            case HOLLOW -> ModEntities.HOLLOW.get().create(
                    level, null, spawn, MobSpawnType.EVENT, true, false);
            case RETURNED -> ModEntities.RETURNED.get().create(
                    level, null, spawn, MobSpawnType.EVENT, true, false);
            case MIMIC -> ModEntities.MIMIC.get().create(
                    level, null, spawn, MobSpawnType.EVENT, true, false);
            case ARCHITECT -> ModEntities.ARCHITECT.get().create(
                    level, null, spawn, MobSpawnType.EVENT, true, false);
        };
        if (mob == null) {
            lastFailure = "entity-create";
            return false;
        }
        mob.addTag(scavengerTag(hearth.id()));
        mob.setPersistenceRequired();
        if (mob instanceof FrostbittenEntity frostbitten) {
            frostbitten.setEmerging(true);
        }
        if (!level.addFreshEntity(mob)) {
            mob.discard();
            lastFailure = "entity-add";
            return false;
        }
        queueSummonTrail(level, hearth.id(), anchor, mob);
        level.sendParticles(ParticleTypes.SOUL,
                mob.getX(), mob.getY() + 0.8D, mob.getZ(),
                profile.swarm() ? 16 : 8,
                0.45D, 0.7D, 0.45D, 0.025D);
        lastFailure = "none";
        return true;
    }

    private static void announceSwarm(
            ServerLevel level, ReturnedHearthSavedData data,
            ReturnedHearthSavedData.HearthRecord hearth, BlockPos anchor) {
        if (hearth.heartSwarmAnnounced()
                || !data.markHeartSwarmAnnounced(hearth.id())) {
            return;
        }
        data.scheduleHeartScavengerWave(hearth.id(), level.getGameTime());
        level.playSound(null, anchor,
                ModSounds.THAE_IVEN_HEART_SWARM_WAIL.get(),
                SoundSource.HOSTILE, 7.0F, 0.58F);
        level.players().stream()
                .filter(player -> participatingPlayer(player, anchor))
                .forEach(player -> sendSwarmBeacon(level, player, anchor));
        FrozenDawn.LOGGER.info(
                "Heart {} became LIVE and called the exposed archive swarm",
                shortId(hearth.id()));
    }

    private static void sendSwarmBeacon(
            ServerLevel level, ServerPlayer player, BlockPos anchor) {
        double x = anchor.getX() + 0.5D;
        double y = anchor.getY() + 30.0D;
        double z = anchor.getZ() + 0.5D;
        level.sendParticles(player, ParticleTypes.FLASH, true,
                x, y, z, 2, 0.5D, 0.5D, 0.5D, 0.0D);
        level.sendParticles(player, ParticleTypes.SCULK_SOUL, true,
                x, y, z, 96, 6.5D, 5.0D, 6.5D, 0.16D);
        for (int height = 0; height <= 42; height += 2) {
            level.sendParticles(player, ParticleTypes.END_ROD, true,
                    x, y + height, z,
                    2, 0.18D, 0.30D, 0.18D, 0.015D);
        }
        for (int ray = 0; ray < 24; ray++) {
            double angle = ray * Math.PI * 2.0D / 24.0D;
            double rise = 0.10D + (ray % 4) * 0.035D;
            level.sendParticles(player, ParticleTypes.SCULK_SOUL, true,
                    x, y, z, 0,
                    Math.cos(angle) * 0.42D, rise,
                    Math.sin(angle) * 0.42D, 1.0D);
        }
    }

    @Nullable
    private static BlockPos findLoadedSpawn(
            ServerLevel level, BlockPos anchor, RandomSource random) {
        for (int attempt = 0; attempt < 28; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = SPAWN_MIN_RADIUS
                    + random.nextDouble() * (SPAWN_MAX_RADIUS - SPAWN_MIN_RADIUS);
            int x = MthFloor(anchor.getX() + Math.cos(angle) * radius);
            int z = MthFloor(anchor.getZ() + Math.sin(angle) * radius);
            ChunkPos chunk = new ChunkPos(BlockPos.containing(x, 0, z));
            if (!level.hasChunk(chunk.x, chunk.z)) {
                continue;
            }
            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, anchor.getY(), z));
            BlockState floor = level.getBlockState(surface.below());
            if (validSpawnPosition(level, surface, floor)) {
                return surface;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos findNearPlayerSpawn(
            ServerLevel level, BlockPos anchor, RandomSource random) {
        List<ServerPlayer> players = level.players().stream()
                .filter(player -> participatingPlayer(player, anchor))
                .toList();
        if (players.isEmpty()) {
            return null;
        }
        ServerPlayer player = players.get(random.nextInt(players.size()));
        for (int attempt = 0; attempt < 18; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 4.5D + random.nextDouble() * 4.5D;
            int x = MthFloor(player.getX() + Math.cos(angle) * radius);
            int z = MthFloor(player.getZ() + Math.sin(angle) * radius);
            ChunkPos chunk = new ChunkPos(BlockPos.containing(x, 0, z));
            if (!level.hasChunk(chunk.x, chunk.z)) {
                continue;
            }
            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, player.getBlockY(), z));
            if (validSpawnPosition(level, surface,
                    level.getBlockState(surface.below()))) {
                return surface;
            }
        }
        return null;
    }

    private static boolean validSpawnPosition(
            ServerLevel level, BlockPos surface, BlockState floor) {
        return floor.isSolidRender(level, surface.below())
                && level.getBlockState(surface).isAir()
                && level.getBlockState(surface.above()).isAir()
                && level.getEntities(null, new AABB(surface).inflate(1.0D)).isEmpty();
    }

    private static void queueSummonTrail(
            ServerLevel level, UUID hearthId, BlockPos anchor, Mob mob) {
        Vec3 start = new Vec3(
                anchor.getX() + 0.5D,
                anchor.getY() + 30.0D,
                anchor.getZ() + 0.5D);
        Vec3 end = mob.position().add(0.0D, mob.getBbHeight() * 0.55D, 0.0D);
        SUMMON_TRAILS.add(new SummonTrail(
                hearthId, start, end, level.getGameTime(), mob.getId()));
    }

    private static void tickSummonTrails(ServerLevel level) {
        Iterator<SummonTrail> trails = SUMMON_TRAILS.iterator();
        while (trails.hasNext()) {
            SummonTrail trail = trails.next();
            long elapsed = level.getGameTime() - trail.startGameTime();
            if (elapsed < 0L || elapsed > SUMMON_TRAIL_TICKS) {
                trails.remove();
                continue;
            }
            float progress = net.minecraft.util.Mth.clamp(
                    elapsed / (float) SUMMON_TRAIL_TICKS, 0.0F, 1.0F);
            float eased = progress * progress * (3.0F - 2.0F * progress);
            for (int tail = 0; tail < 4; tail++) {
                float tailProgress = Math.max(0.0F, eased - tail * 0.045F);
                Vec3 point = trail.start().lerp(trail.end(), tailProgress);
                level.sendParticles(tail == 0
                                ? ParticleTypes.END_ROD
                                : tail == 3 ? ParticleTypes.SNOWFLAKE
                                : ParticleTypes.SCULK_SOUL,
                        point.x, point.y, point.z,
                        1, 0.025D, 0.025D, 0.025D, 0.0D);
            }
            if (elapsed == SUMMON_TRAIL_TICKS) {
                level.sendParticles(ParticleTypes.SCULK_SOUL,
                        trail.end().x, trail.end().y, trail.end().z,
                        16, 0.42D, 0.65D, 0.42D, 0.08D);
                trails.remove();
            }
        }
    }

    private static void directEncounter(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor, @Nullable HeartSuccessorEntity successor) {
        List<LivingEntity> targets = encounterTargets(level, hearth, anchor);
        List<Mob> scavengers = activeScavengers(level, hearth.id(), anchor);
        boolean conducting = successor != null
                && successor.mode() == HeartSuccessorPolicy.Mode.CONDUCTING;
        LivingEntity focus = conducting ? focusTarget(targets, anchor) : null;
        if (successor != null) {
            List<Mob> links = conducting
                    ? supportCandidates(level, hearth, anchor).stream()
                    .filter(Mob.class::isInstance)
                    .map(Mob.class::cast)
                    .filter(Mob::isAlive)
                    .sorted(Comparator.comparingDouble(successor::distanceToSqr))
                    .limit(HeartSuccessorPolicy.MAX_SUPPORT_LINKS)
                    .toList()
                    : List.of();
            replaceSupportLinks(level, successor, links, conducting);
        }
        for (Mob scavenger : scavengers) {
            LivingEntity target = focus != null ? focus : nearestTarget(scavenger, targets);
            if (target != null) {
                if (scavenger instanceof HollowEntity hollow) {
                    hollow.setHeartScavengerTarget(target);
                } else if (scavenger instanceof MimicEntity mimic) {
                    mimic.setHeartScavengerTarget(target);
                } else {
                    scavenger.setTarget(target);
                }
            }
        }

        // The congregation can defend itself without reclassifying the player.
        for (LivingEntity target : targets) {
            if (!(target instanceof Mob resident) || !resident.isAlive()) {
                continue;
            }
            Mob threat = scavengers.stream()
                    .filter(Mob::isAlive)
                    .filter(scavenger -> scavenger.distanceToSqr(resident) <= 30.0D * 30.0D)
                    .min(Comparator.comparingDouble(resident::distanceToSqr))
                    .orElse(null);
            if (threat != null && (resident.getTarget() == null
                    || isHeartScavenger(resident.getTarget(), hearth.id()))) {
                if (resident instanceof MimicEntity mimic) {
                    mimic.setHeartScavengerTarget(threat);
                } else {
                    resident.setTarget(threat);
                }
            }
        }
    }

    private static void tickSuccessor(
            ServerLevel level, ReturnedHearthSavedData data,
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor, HeartSuccessorEntity successor) {
        long now = level.getGameTime();
        if (successor.isDying()) {
            clearSupportBuffs(level, successor);
            if (successor.deathTicks() >= HeartSuccessorPolicy.DEATH_TICKS) {
                int nextGeneration = successor.generation() + 1;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        successor.getX(), successor.getY() + 1.2D, successor.getZ(),
                        52, 1.1D, 1.7D, 1.1D, 0.13D);
                data.scheduleHeartSuccessorRespawn(
                        hearth.id(), nextGeneration,
                        now + HeartSuccessorPolicy.RESPAWN_TICKS);
                successor.discard();
            }
            return;
        }
        steerSuccessor(level, hearth, anchor, successor);
        if (now - successor.assemblyStartGameTime()
                < HeartSuccessorPolicy.ASSEMBLY_TICKS) {
            clearSupportBuffs(level, successor);
            successor.setMode(HeartSuccessorPolicy.Mode.ASSEMBLING);
            successor.setHealTargetId(-1);
            successor.clearLinkTargets();
            return;
        }
        if (successor.staggerTicks() > 0) {
            clearSupportBuffs(level, successor);
            successor.tickStagger();
            successor.setMode(HeartSuccessorPolicy.Mode.STAGGERED);
            successor.setHealTargetId(-1);
            successor.clearLinkTargets();
            return;
        }
        long activeTicks = now - successor.assemblyStartGameTime()
                - HeartSuccessorPolicy.ASSEMBLY_TICKS;
        LivingEntity currentHealTarget = level.getEntity(successor.healTargetId())
                instanceof LivingEntity living && validHealTarget(successor, living)
                ? living : null;
        LivingEntity emergencyTarget = emergencyHealTarget(
                level, hearth, anchor, successor);
        boolean continueHealing = currentHealTarget != null
                && HeartSuccessorPolicy.shouldContinueHealing(
                currentHealTarget.getHealth(), currentHealTarget.getMaxHealth());
        HeartSuccessorPolicy.Mode desired = emergencyTarget != null || continueHealing
                ? HeartSuccessorPolicy.Mode.HEALING
                : HeartSuccessorPolicy.mode(activeTicks);
        if (emergencyTarget != null && emergencyTarget != currentHealTarget) {
            successor.setHealTargetId(emergencyTarget.getId());
        } else if (!continueHealing && emergencyTarget == null
                && desired != HeartSuccessorPolicy.Mode.HEALING) {
            successor.setHealTargetId(-1);
        }
        if (successor.mode() != desired) {
            clearSupportBuffs(level, successor);
            successor.setMode(desired);
            if (desired != HeartSuccessorPolicy.Mode.HEALING) {
                successor.setHealTargetId(-1);
                successor.clearLinkTargets();
            }
            level.playSound(null, successor.blockPosition(),
                    desired == HeartSuccessorPolicy.Mode.CONDUCTING
                            ? ModSounds.HEART_SUCCESSOR_CONDUCT.get()
                            : ModSounds.HEART_SUCCESSOR_HEAL.get(),
                    SoundSource.HOSTILE, desired == HeartSuccessorPolicy.Mode.CONDUCTING
                            ? 1.55F : 1.15F,
                    desired == HeartSuccessorPolicy.Mode.CONDUCTING ? 0.62F : 0.78F);
        }
        if (desired == HeartSuccessorPolicy.Mode.HEALING) {
            healThroughSuccessor(level, hearth, anchor, successor);
        } else {
            successor.setHealTargetId(-1);
        }
        if (level.getGameTime() % 3L == 0L) {
            emitSuccessorTethers(level, successor);
        }
    }

    private static void healThroughSuccessor(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor, HeartSuccessorEntity successor) {
        LivingEntity target = level.getEntity(successor.healTargetId())
                instanceof LivingEntity living && validHealTarget(successor, living)
                ? living : null;
        if (target == null) {
            target = supportCandidates(level, hearth, anchor).stream()
                    .filter(candidate -> validHealTarget(successor, candidate))
                    .min(Comparator.comparingDouble(
                            candidate -> candidate.getHealth() / candidate.getMaxHealth()))
                    .orElse(null);
            successor.setHealTargetId(target == null ? -1 : target.getId());
        }
        if (target == null) {
            clearSupportBuffs(level, successor);
            successor.clearLinkTargets();
            return;
        }
        clearSupportBuffs(level, successor);
        successor.setLinkTargetIds(List.of(target));
        successor.getLookControl().setLookAt(target, 45.0F, 45.0F);
        if (level.getGameTime() % 20L == 0L) {
            target.heal(HeartSuccessorPolicy.healPerSecond(
                    successor.generation(), successor.fieldStrength()));
            level.sendParticles(ParticleTypes.SOUL,
                    target.getX(), target.getY() + target.getBbHeight() * 0.55D,
                    target.getZ(), 5, 0.25D, 0.4D, 0.25D, 0.015D);
        }
    }

    private static boolean validHealTarget(
            HeartSuccessorEntity successor, LivingEntity candidate) {
        return candidate instanceof Mob && candidate.isAlive() && candidate != successor
                && candidate.getHealth() + 0.05F < candidate.getMaxHealth()
                && candidate.distanceToSqr(successor) <= 64.0D * 64.0D
                && successor.hasLineOfSight(candidate);
    }

    @Nullable
    private static LivingEntity emergencyHealTarget(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor, HeartSuccessorEntity successor) {
        return supportCandidates(level, hearth, anchor).stream()
                .filter(candidate -> validHealTarget(successor, candidate))
                .filter(candidate -> HeartSuccessorPolicy.needsEmergencyHealing(
                        candidate.getHealth(), candidate.getMaxHealth()))
                .min(Comparator.comparingDouble(
                        candidate -> candidate.getHealth() / candidate.getMaxHealth()))
                .orElse(null);
    }

    @Nullable
    private static HeartSuccessorEntity reconcileSuccessor(
            ServerLevel level, ReturnedHearthSavedData data,
            ReturnedHearthSavedData.HearthRecord hearth, BlockPos anchor,
            boolean shouldExist) {
        HeartSuccessorEntity bound = hearth.heartSuccessorEntityId()
                .map(level::getEntity)
                .filter(HeartSuccessorEntity.class::isInstance)
                .map(HeartSuccessorEntity.class::cast)
                .filter(Entity::isAlive)
                .orElse(null);
        List<HeartSuccessorEntity> candidates = level.getEntitiesOfClass(
                HeartSuccessorEntity.class,
                new AABB(anchor).inflate(72.0D, 80.0D, 72.0D),
                entity -> entity.hearthId().map(hearth.id()::equals).orElse(false));
        if (bound == null && !candidates.isEmpty()) {
            bound = candidates.getFirst();
            data.bindHeartSuccessor(hearth.id(), bound.getUUID(),
                    bound.generation(), bound.assemblyStartGameTime());
        }
        for (HeartSuccessorEntity candidate : candidates) {
            if (candidate != bound) {
                candidate.discard();
            }
        }
        if (!shouldExist) {
            if (bound != null) {
                snapSuccessor(level, bound);
            }
            data.clearHeartSuccessor(hearth.id());
            return null;
        }

        int boundNode = HeartSuccessorPolicy.boundNode(hearth.heartDestroyedNodeMask());
        int generation = Math.max(
                HeartSuccessorPolicy.generationForNode(boundNode),
                hearth.heartSuccessorGeneration());
        if (bound != null && bound.boundNode() != boundNode) {
            snapSuccessor(level, bound);
            data.scheduleHeartSuccessorRespawn(
                    hearth.id(), generation,
                    level.getGameTime() + HeartSuccessorPolicy.RESPAWN_TICKS);
            bound = null;
        }
        long respawnAt = hearth.heartSuccessorRespawnGameTime();
        if (bound == null && respawnAt >= 0L && level.getGameTime() < respawnAt) {
            return null;
        }
        if (bound == null) {
            bound = createSuccessor(level, data, hearth, anchor, boundNode, generation);
        }
        if (bound != null && !bound.isDying()) {
            orientTowardNearestPlayer(level, anchor, bound);
        }
        return bound;
    }

    @Nullable
    private static HeartSuccessorEntity createSuccessor(
            ServerLevel level, ReturnedHearthSavedData data,
            ReturnedHearthSavedData.HearthRecord hearth, BlockPos anchor,
            int boundNode, int generation) {
        HeartSuccessorEntity successor = ModEntities.HEART_SUCCESSOR.get().create(level);
        if (successor == null) {
            lastFailure = "successor-create";
            return null;
        }
        long now = level.getGameTime();
        successor.configure(hearth.id(), boundNode, generation, now,
                anchor, hearth.heartLayoutSeed(), hearth.heartFieldStrength());
        Vec3 position = successorSpawnPosition(anchor, boundNode, generation);
        successor.setPos(position.x, position.y, position.z);
        if (!level.addFreshEntity(successor)) {
            successor.discard();
            lastFailure = "successor-add";
            return null;
        }
        data.bindHeartSuccessor(hearth.id(), successor.getUUID(), generation, now);
        level.playSound(null, successor.blockPosition(),
                ModSounds.HEART_SUCCESSOR_ASSEMBLE.get(),
                SoundSource.HOSTILE, 1.9F, 0.66F);
        successorsSpawned++;
        lastFailure = "none";
        return successor;
    }

    private static void snapSuccessor(
            ServerLevel level, HeartSuccessorEntity successor) {
        clearSupportBuffs(level, successor);
        level.playSound(null, successor.blockPosition(),
                ModSounds.HEART_SUCCESSOR_SNAP.get(),
                SoundSource.HOSTILE, 1.8F, 0.82F);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                successor.getX(), successor.getY() + 1.2D, successor.getZ(),
                42, 0.9D, 1.5D, 0.9D, 0.11D);
        successor.discard();
    }

    private static Vec3 successorSpawnPosition(
            BlockPos anchor, int node, int generation) {
        double angle = node * 2.17D + generation * 0.71D + 0.65D;
        double radius = 6.5D;
        return new Vec3(
                anchor.getX() + 0.5D + Math.cos(angle) * radius,
                anchor.getY() + 1.2D,
                anchor.getZ() + 0.5D + Math.sin(angle) * radius);
    }

    private static void steerSuccessor(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor, HeartSuccessorEntity successor) {
        ServerPlayer player = level.players().stream()
                .filter(candidate -> participatingPlayer(candidate, anchor))
                .min(Comparator.comparingDouble(successor::distanceToSqr))
                .orElse(null);
        LivingEntity support = successor.linkTargetIds().stream()
                .map(level::getEntity)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(LivingEntity::isAlive)
                .min(Comparator.comparingDouble(successor::distanceToSqr))
                .orElse(null);
        LivingEntity focus = support != null ? support : player;
        if (focus == null) {
            successor.steerToward(Vec3.atBottomCenterOf(anchor.above(2)));
            return;
        }

        Vec3 away = successor.position().subtract(focus.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (away.horizontalDistanceSqr() < 0.01D) {
            away = Vec3.atCenterOf(anchor).subtract(focus.position())
                    .multiply(1.0D, 0.0D, 1.0D);
        }
        if (away.horizontalDistanceSqr() < 0.01D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        away = away.normalize();
        double standoff = support != null ? 4.5D : 8.0D;
        double horizontalDistance = successor.position().subtract(focus.position())
                .multiply(1.0D, 0.0D, 1.0D).horizontalDistance();
        Vec3 desired = horizontalDistance > standoff + 1.5D
                || horizontalDistance < standoff - 1.0D
                ? focus.position().add(away.scale(standoff))
                : new Vec3(successor.getX(), focus.getY(), successor.getZ());
        desired = new Vec3(
                desired.x,
                Math.max(anchor.getY() + 1.25D,
                        focus.getY() + (support != null ? 1.9D : 2.5D)
                                + Math.sin(level.getGameTime() * 0.055D) * 0.25D),
                desired.z);

        Vec3 center = Vec3.atCenterOf(anchor);
        Vec3 fromCenter = desired.subtract(center).multiply(1.0D, 0.0D, 1.0D);
        if (fromCenter.horizontalDistanceSqr() > 28.0D * 28.0D) {
            Vec3 clamped = fromCenter.normalize().scale(28.0D);
            desired = new Vec3(center.x + clamped.x, desired.y, center.z + clamped.z);
        }
        successor.turnToward(focus.getEyePosition());
        successor.steerToward(findOpenFlightPoint(level, desired));
    }

    private static Vec3 findOpenFlightPoint(ServerLevel level, Vec3 desired) {
        BlockPos base = BlockPos.containing(desired);
        for (int lift = 0; lift <= 6; lift++) {
            BlockPos feet = base.above(lift);
            boolean open = true;
            for (int height = 0; height < 3; height++) {
                BlockPos check = feet.above(height);
                if (!level.getBlockState(check).getCollisionShape(level, check).isEmpty()) {
                    open = false;
                    break;
                }
            }
            if (open) {
                return new Vec3(desired.x, feet.getY() + 0.15D, desired.z);
            }
        }
        return desired.add(0.0D, 4.0D, 0.0D);
    }

    private static void emitSuccessorTethers(
            ServerLevel level, HeartSuccessorEntity successor) {
        Vec3 from = successor.position().add(
                0.0D, successor.getBbHeight() * 0.68D, 0.0D);
        Vec3 node = HeartLattice.nodePosition(
                successor.heartAnchor(), successor.layoutSeed(),
                0.0F, successor.boundNode());
        emitParticleTether(level, node, from, false);
        for (int entityId : successor.linkTargetIds()) {
            if (!(level.getEntity(entityId) instanceof LivingEntity target)
                    || !target.isAlive()) {
                continue;
            }
            Vec3 to = target.position().add(
                    0.0D, target.getBbHeight() * 0.58D, 0.0D);
            boolean healing = successor.mode() == HeartSuccessorPolicy.Mode.HEALING
                    && entityId == successor.healTargetId();
            emitParticleTether(level, from, to, healing);
        }
    }

    private static void emitParticleTether(
            ServerLevel level, Vec3 from, Vec3 to, boolean healing) {
        Vec3 delta = to.subtract(from);
        int steps = healing ? 16 : 12;
        for (int step = 1; step <= steps; step++) {
            Vec3 point = from.add(delta.scale(step / (double) steps));
            if (healing) {
                level.sendParticles(HEALING_TETHER_DUST,
                        point.x, point.y, point.z,
                        1, 0.018D, 0.018D, 0.018D, 0.0D);
            } else {
                level.sendParticles(step % 3 == 0
                                ? ParticleTypes.SNOWFLAKE : ParticleTypes.SCULK_SOUL,
                        point.x, point.y, point.z,
                        1, 0.012D, 0.012D, 0.012D, 0.0D);
            }
        }
        if (healing) {
            level.sendParticles(ParticleTypes.WAX_ON,
                    to.x, to.y, to.z,
                    2, 0.12D, 0.18D, 0.12D, 0.015D);
        }
    }

    private static List<LivingEntity> supportCandidates(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor) {
        List<LivingEntity> candidates = new ArrayList<>();
        activeScavengers(level, hearth.id(), anchor).stream()
                .filter(LivingEntity::isAlive)
                .filter(HeartScavengerWaveManager::eligibleSupportTarget)
                .forEach(candidates::add);
        for (UUID residentId : hearth.combatRoster().keySet()) {
            if (level.getEntity(residentId) instanceof Mob resident
                    && resident.isAlive()
                    && eligibleSupportTarget(resident)) {
                candidates.add(resident);
            }
        }
        return candidates;
    }

    private static boolean eligibleSupportTarget(LivingEntity target) {
        return !(target instanceof FrostmiteEntity)
                && target.getType() != ModEntities.SHADOW_FIGURE.get();
    }

    private static void replaceSupportLinks(
            ServerLevel level, HeartSuccessorEntity successor,
            List<? extends Mob> links, boolean buffing) {
        clearSupportBuffs(level, successor);
        successor.setLinkTargetIds(links);
        if (buffing) {
            links.forEach(HeartScavengerWaveManager::applySupportBuff);
        }
    }

    private static void applySupportBuff(LivingEntity target) {
        AttributeInstance movement = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            movement.addOrUpdateTransientModifier(new AttributeModifier(
                    SUPPORT_SPEED_ID,
                    HeartSuccessorPolicy.SUPPORT_SPEED_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance damage = target.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.addOrUpdateTransientModifier(new AttributeModifier(
                    SUPPORT_DAMAGE_ID,
                    HeartSuccessorPolicy.SUPPORT_DAMAGE_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        target.addEffect(new MobEffectInstance(
                MobEffects.GLOWING,
                TARGET_REFRESH_TICKS + 4,
                0,
                true,
                false,
                false));
    }

    private static void clearSupportBuffs(
            ServerLevel level, HeartSuccessorEntity successor) {
        for (int entityId : successor.linkTargetIds()) {
            if (level.getEntity(entityId) instanceof LivingEntity target) {
                clearSupportBuff(target);
            }
        }
    }

    private static void clearSupportBuff(LivingEntity target) {
        AttributeInstance movement = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            movement.removeModifier(SUPPORT_SPEED_ID);
        }
        AttributeInstance damage = target.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.removeModifier(SUPPORT_DAMAGE_ID);
        }
    }

    private static void orientTowardNearestPlayer(
            ServerLevel level, BlockPos anchor, HeartSuccessorEntity successor) {
        ServerPlayer player = level.players().stream()
                .filter(candidate -> participatingPlayer(candidate, anchor))
                .min(Comparator.comparingDouble(successor::distanceToSqr))
                .orElse(null);
        if (player == null) {
            return;
        }
        successor.turnToward(player.getEyePosition());
    }

    private static List<LivingEntity> encounterTargets(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor) {
        List<LivingEntity> targets = new ArrayList<>();
        level.players().stream()
                .filter(player -> participatingPlayer(player, anchor))
                .forEach(targets::add);
        for (UUID residentId : hearth.combatRoster().keySet()) {
            Entity entity = level.getEntity(residentId);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                targets.add(living);
            }
        }
        return targets;
    }

    private static boolean hasParticipatingPlayer(ServerLevel level, BlockPos anchor) {
        return level.players().stream().anyMatch(player -> participatingPlayer(player, anchor));
    }

    private static boolean participatingPlayer(ServerPlayer player, BlockPos anchor) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && player.distanceToSqr(Vec3.atCenterOf(anchor))
                <= ARENA_RADIUS * ARENA_RADIUS;
    }

    private static List<Mob> activeScavengers(
            ServerLevel level, UUID hearthId, BlockPos anchor) {
        return level.getEntitiesOfClass(Mob.class,
                new AABB(anchor).inflate(ARENA_RADIUS, 64.0D, ARENA_RADIUS),
                mob -> mob.isAlive() && isHeartScavenger(mob, hearthId));
    }

    @Nullable
    private static LivingEntity nearestTarget(Mob mob, List<LivingEntity> targets) {
        return targets.stream()
                .filter(LivingEntity::isAlive)
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
    }

    @Nullable
    private static LivingEntity focusTarget(
            List<LivingEntity> targets, BlockPos anchor) {
        return targets.stream()
                .filter(LivingEntity::isAlive)
                .min(Comparator.comparingDouble(
                        target -> target.distanceToSqr(Vec3.atCenterOf(anchor))))
                .orElse(null);
    }

    private static String scavengerTag(UUID hearthId) {
        return SCAVENGER_TAG_PREFIX + hearthId.toString().replace("-", "");
    }

    private static int MthFloor(double value) {
        return (int) Math.floor(value);
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private record SummonTrail(
            UUID hearthId, Vec3 start, Vec3 end,
            long startGameTime, int entityId) {
    }
}
