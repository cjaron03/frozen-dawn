package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.bloom.BloomGrowthManager;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.config.PostMaeveEvolutionDifficulty;
import com.frozendawn.aggregate.AggregateGrowthManager;
import com.frozendawn.aggregate.StillpointPolicy;
import com.frozendawn.entity.FrostmiteEntity;
import com.frozendawn.entity.FrostwritheEntity;
import com.frozendawn.entity.FrostwrithePolicy;
import com.frozendawn.entity.FrostwritheState;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public final class FrostmiteSpawner {

    private FrostmiteSpawner() {}

    public static void tick(ServerLevel level, int currentPhase, float progress) {
        boolean postMaeve = PostMaeveWorldState.isUndoneSpawningReleased(
                level.getServer());
        if (!postMaeve && currentPhase < 5) return;
        if (!FrozenDawnConfig.ENABLE_FROSTMITE.get()) return;

        long gameTick = level.getGameTime();
        if (gameTick % 160 != 0) return;

        float mobMult = (float) FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get().doubleValue();
        float spawnChance = currentPhase >= 6 ? Math.min(0.36f, 0.18f * mobMult) : Math.min(0.20f, 0.10f * mobMult);
        int maxNearby = currentPhase >= 6 ? Mth.clamp(Mth.ceil(8 * mobMult), 6, 16) : Mth.clamp(Mth.ceil(5 * mobMult), 4, 10);
        int maxGroup = currentPhase >= 6 ? Mth.clamp(Mth.ceil(2.5f * mobMult), 3, 5) : Mth.clamp(Mth.ceil(1.5f * mobMult), 2, 3);
        Set<Long> checkedEvolutionCells = new HashSet<>();

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            boolean forcedEvolution = postMaeve
                    && PostMaeveEncounterDirector.isDebugReadyPlayer(
                    level, player, PostMaeveEncounterType.FROSTWRITHE);
            if (player.isCreative() && !forcedEvolution) continue;
            if (!forcedEvolution && level.random.nextFloat() > spawnChance) continue;

            BlockPos center = findSpawnPos(level, player, level.random);
            if (center == null && forcedEvolution) {
                center = findDebugEncounterPos(level, player, level.random);
            }
            if (center == null) {
                if (forcedEvolution) {
                    PostMaeveEncounterDirector.blockedPlayer(level, player,
                            PostMaeveEncounterType.FROSTWRITHE,
                            "no loaded Frostmite encounter surface");
                }
                continue;
            }

            long evolutionCell = evolutionCell(center);
            if (postMaeve && checkedEvolutionCells.add(evolutionCell)
                    && trySpawnFrostwrithe(
                    level, center, player, level.random, false)) {
                continue;
            }

            int nearby = level.getEntitiesOfClass(FrostmiteEntity.class,
                    player.getBoundingBox().inflate(32.0)).size();
            if (nearby >= maxNearby) continue;

            int groupSize = Math.min(maxGroup, maxNearby - nearby);
            groupSize = 1 + level.random.nextInt(groupSize);
            spawnCluster(level, center, groupSize, MobSpawnType.NATURAL);
        }
    }

    public static void trySpawnInfestedBreak(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (!FrozenDawnConfig.ENABLE_FROSTMITE.get()) return;
        if (player == null || player.isCreative() || player.isSpectator()) return;

        int phase = com.frozendawn.phase.FrozenDawnPhaseTracker.getPhase();
        boolean postMaeve = PostMaeveWorldState.isUndoneSpawningReleased(
                level.getServer());
        if (!postMaeve && phase < 4) return;
        if (!isInfestedBreakBlock(state)) return;

        float chance = phase >= 6 ? 0.24f : phase == 5 ? 0.16f : 0.12f;
        if (level.random.nextFloat() > chance) return;

        if (postMaeve && trySpawnFrostwrithe(
                level, pos.above(), player, level.random, true)) {
            return;
        }
        int count = phase >= 6 ? 1 + level.random.nextInt(3)
                : 1 + level.random.nextInt(2);
        spawnCluster(level, pos.above(), count, MobSpawnType.TRIGGERED);
    }

    private static boolean trySpawnFrostwrithe(
            ServerLevel level, BlockPos encounter, ServerPlayer player,
            RandomSource random, boolean infestedBreak) {
        if (!FrozenDawnConfig.ENABLE_FROSTWRITHE.get()) return false;
        float chance = FrostwrithePolicy.evolutionChance(
                FrostwritheManager.ticksSinceErasure(level),
                BloomGrowthManager.pressureMultiplier(level, encounter),
                FrozenDawnConfig.FROSTWRITHE_EVOLUTION_SHARE_MULTIPLIER.get()
                        * PostMaeveEvolutionDifficulty.evolutionMultiplier()
                        * StillpointPolicy.evolvedWeightMultiplier(level, encounter)
                        * AggregateGrowthManager.evolvedWeightMultiplier(level, encounter),
                infestedBreak);
        if (chance <= 0.0F) return false;

        int cap = FrozenDawnConfig.FROSTWRITHE_NEARBY_CAP.get();
        int nearby = level.getEntitiesOfClass(FrostwritheEntity.class,
                new AABB(encounter).inflate(64.0D)).size();
        boolean unresolvedColony = !level.getEntitiesOfClass(
                FrostmiteEntity.class, new AABB(encounter).inflate(64.0D),
                FrostmiteEntity::hasColony).isEmpty();
        if (nearby >= cap) {
            PostMaeveEncounterDirector.blockedPlayer(level, player,
                    PostMaeveEncounterType.FROSTWRITHE,
                    "nearby Frostwrithe cap reached");
            return false;
        }
        if (unresolvedColony) {
            PostMaeveEncounterDirector.blockedPlayer(level, player,
                    PostMaeveEncounterType.FROSTWRITHE,
                    "unresolved Frostwrithe colony nearby");
            return false;
        }

        BlockPos formation = findFormationPos(level, encounter, random);
        if (formation == null
                || MiteAwayRegistry.isProtected(level, formation.getCenter())) {
            PostMaeveEncounterDirector.blockedPlayer(level, player,
                    PostMaeveEncounterType.FROSTWRITHE,
                    formation == null ? "no clear colony formation surface"
                            : "MiteAway coverage");
            return false;
        }
        if (!PostMaeveEncounterDirector.rollPlayer(level, player,
                PostMaeveEncounterType.FROSTWRITHE, chance)) {
            return false;
        }
        FrostwritheEntity entity = FrostwritheManager.createAt(
                level, formation, infestedBreak ? MobSpawnType.TRIGGERED
                        : MobSpawnType.NATURAL, UUID.randomUUID(), 100,
                FrostwritheState.ASSEMBLING);
        if (entity == null) {
            PostMaeveEncounterDirector.blockedPlayer(level, player,
                    PostMaeveEncounterType.FROSTWRITHE,
                    "entity creation or insertion failed");
            return false;
        }
        entity.setTarget(player);
        PostMaeveEncounterDirector.successPlayer(level, player,
                PostMaeveEncounterType.FROSTWRITHE);
        FrozenDawn.LOGGER.info(
                "[Frostwrithe] Evolved Frostmite encounter near {} at age {} ticks{}",
                player.getName().getString(),
                FrostwritheManager.ticksSinceErasure(level),
                infestedBreak ? " from infested terrain" : "");
        return true;
    }

    private static BlockPos findFormationPos(ServerLevel level, BlockPos origin,
                                             RandomSource random) {
        for (int attempt = 0; attempt < 40; attempt++) {
            BlockPos candidate;
            if (attempt == 0) {
                candidate = origin;
            } else {
                int radius = 3 + attempt / 8;
                int x = origin.getX()
                        + random.nextIntBetweenInclusive(-radius, radius);
                int z = origin.getZ()
                        + random.nextIntBetweenInclusive(-radius, radius);
                candidate = level.getHeightmapPos(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(x, origin.getY(), z));
            }
            if (!isValidSpawnPos(level, candidate)) continue;
            boolean clear = true;
            for (int x = -1; x <= 1 && clear; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos space = candidate.offset(x, 0, z);
                    if (!isOpenSpace(level, space)
                            || !isOpenSpace(level, space.above())) {
                        clear = false;
                        break;
                    }
                }
            }
            if (clear) return candidate;
        }
        return null;
    }

    private static long evolutionCell(BlockPos pos) {
        int cellX = Math.floorDiv(pos.getX(), 128);
        int cellZ = Math.floorDiv(pos.getZ(), 128);
        return ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
    }

    private static boolean isInfestedBreakBlock(BlockState state) {
        return state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE)
                || state.is(ModBlocks.FROZEN_OBSIDIAN.get())
                || state.is(ModBlocks.FROZEN_COAL_ORE.get());
    }

    private static int spawnCluster(ServerLevel level, BlockPos origin, int count, MobSpawnType spawnType) {
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = findNearbySpawnPos(level, origin, level.random);
            if (spawnPos == null) spawnPos = origin;
            FrostmiteEntity mite = ModEntities.FROSTMITE.get().create(level, null, spawnPos, spawnType, true, false);
            if (mite != null) {
                mite.setDeltaMovement(
                        (level.random.nextDouble() - 0.5) * 0.15,
                        0.08 + level.random.nextDouble() * 0.08,
                        (level.random.nextDouble() - 0.5) * 0.15
                );
                level.addFreshEntity(mite);
                spawned++;
            }
        }
        return spawned;
    }

    private static BlockPos findSpawnPos(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double dist = 10 + random.nextInt(5);
            int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            int baseY = player.blockPosition().getY() + random.nextIntBetweenInclusive(-4, 4);

            for (int dy = 4; dy >= -4; dy--) {
                BlockPos candidate = new BlockPos(x, baseY + dy, z);
                if (isValidSpawnPos(level, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static BlockPos findDebugEncounterPos(ServerLevel level,
                                                  ServerPlayer player,
                                                  RandomSource random) {
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = 8.0D + random.nextDouble() * 20.0D;
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, player.getBlockY(), z));
            if (isValidSpawnPos(level, surface)) return surface;
        }
        return null;
    }

    private static BlockPos findNearbySpawnPos(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int attempt = 0; attempt < 6; attempt++) {
            BlockPos candidate = origin.offset(
                    random.nextIntBetweenInclusive(-2, 2),
                    random.nextIntBetweenInclusive(-1, 1),
                    random.nextIntBetweenInclusive(-2, 2)
            );
            if (isValidSpawnPos(level, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isValidSpawnPos(ServerLevel level, BlockPos candidate) {
        if (!level.isInWorldBounds(candidate)) return false;
        if (!isOpenSpace(level, candidate) || !isOpenSpace(level, candidate.above())) {
            return false;
        }
        BlockPos below = candidate.below();
        return level.getBlockState(below).isSolidRender(level, below);
    }

    private static boolean isOpenSpace(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty()
                && state.getCollisionShape(level, pos).isEmpty();
    }

    public static void reset() {
        FrostmiteEntity.resetAttachmentTracking();
        FrostwritheManager.reset();
    }
}
