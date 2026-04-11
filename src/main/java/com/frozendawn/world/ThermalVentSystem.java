package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModDamageTypes;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class ThermalVentSystem {

    private static final int REGION_SIZE = 12;
    private static final int REGION_SCAN_RADIUS = 2;
    private static final int LOCATE_SCAN_RADIUS = 8;
    private static final int BASE_CHANCE = 28;
    private static final long WARM_DURATION_TICKS = 12L * 60L * 20L;
    private static final int WARM_RADIUS = 6;
    private static final int ACTIVE_RADIUS = 8;
    private static final float WARM_FLOOR = 18.0f;
    private static final float ACTIVE_FLOOR = 24.0f;
    private static final int RIM_RADIUS = 2;
    private static final float WARM_RIM_HEAT = 28.0f;
    private static final float ACTIVE_RIM_HEAT = 48.0f;
    private static final float RUPTURE_RIM_HEAT = 56.0f;
    private static final int ACTIVE_ERUPTION_RADIUS = 4;
    private static final int RUPTURE_ERUPTION_RADIUS = 5;
    private static final float ACTIVE_ERUPTION_HEAT = 82.0f;
    private static final float RUPTURE_ERUPTION_HEAT = 110.0f;
    private static final long ACTIVE_MIN_INTERVAL = 20L * 20L;
    private static final long ACTIVE_MAX_INTERVAL = 45L * 20L;
    private static final long ACTIVE_BURST_DURATION = 16L;
    private static final long RUPTURE_MIN_INTERVAL = 30L * 20L;
    private static final long RUPTURE_MAX_INTERVAL = 75L * 20L;
    private static final long RUPTURE_WARNING_DURATION = 5L * 20L;
    private static final long RUPTURE_BURST_DURATION = 24L;
    private static final Set<ResourceKey<Biome>> BONUS_BIOMES = Set.of(
            Biomes.SNOWY_PLAINS,
            Biomes.ICE_SPIKES,
            Biomes.SNOWY_TAIGA,
            Biomes.TAIGA,
            Biomes.OLD_GROWTH_PINE_TAIGA,
            Biomes.OLD_GROWTH_SPRUCE_TAIGA,
            Biomes.MEADOW,
            Biomes.GROVE
    );
    private static final Set<ResourceKey<Biome>> HOT_BIOMES = Set.of(
            Biomes.DESERT,
            Biomes.BADLANDS,
            Biomes.ERODED_BADLANDS,
            Biomes.WOODED_BADLANDS,
            Biomes.SAVANNA,
            Biomes.SAVANNA_PLATEAU,
            Biomes.JUNGLE,
            Biomes.SPARSE_JUNGLE,
            Biomes.BAMBOO_JUNGLE
    );

    private ThermalVentSystem() {
    }

    public static void tick(ServerLevel level, int phase, float progress, long worldTime) {
        ThermalVentRegistry.beginTick(level);
        if (level.players().isEmpty()) {
            return;
        }

        ThermalVentSavedData ventData = ThermalVentSavedData.get(level.getServer());
        Set<Long> visitedRegions = new HashSet<>();

        for (ServerPlayer player : level.players()) {
            int playerRegionX = Math.floorDiv(player.blockPosition().getX() >> 4, REGION_SIZE);
            int playerRegionZ = Math.floorDiv(player.blockPosition().getZ() >> 4, REGION_SIZE);

            for (int rx = -REGION_SCAN_RADIUS; rx <= REGION_SCAN_RADIUS; rx++) {
                for (int rz = -REGION_SCAN_RADIUS; rz <= REGION_SCAN_RADIUS; rz++) {
                    int regionX = playerRegionX + rx;
                    int regionZ = playerRegionZ + rz;
                    long regionKey = packRegion(regionX, regionZ);
                    if (!visitedRegions.add(regionKey)) {
                        continue;
                    }

                    ThermalVentSavedData.VentRecord record = ventData.getOrCreate(level, regionX, regionZ);
                    if (record == null || !isPlacementAreaLoaded(level, record.x(), record.z())) {
                        continue;
                    }

                    if (!record.surfaced()) {
                        if (!surfaceVent(level, record)) {
                            continue;
                        }
                        ventData.markDirty();
                    }

                    ThermalVentSnapshot snapshot = updateRecord(level, ventData, record, phase, progress, worldTime);
                    applyPoolHeatStage(level, record, snapshot);
                    applyVisualState(level, record, snapshot, phase, progress, worldTime);
                    ThermalVentRegistry.register(level, snapshot);
                }
            }
        }
    }

    public static void reset() {
        ThermalVentRegistry.reset();
    }

    @Nullable
    static ThermalVentSavedData.VentRecord createVentRecord(ServerLevel level, int regionX, int regionZ) {
        long seed = level.getSeed();
        long baseHash = regionHash(seed, regionX, regionZ);

        for (int attempt = 0; attempt < 4; attempt++) {
            long hash = mixHash(baseHash + (attempt * 0x9E3779B97F4A7C15L));
            int localChunkX = 1 + Math.floorMod((int) (hash >>> 8), REGION_SIZE - 2);
            int localChunkZ = 1 + Math.floorMod((int) (hash >>> 20), REGION_SIZE - 2);
            int x = ((regionX * REGION_SIZE + localChunkX) << 4) + 8;
            int z = ((regionZ * REGION_SIZE + localChunkZ) << 4) + 8;

            Holder<Biome> biome = LandmarkBiomeRules.getLandmarkNoiseBiome(level, x, z);
            if (!LandmarkBiomeRules.isToleratedLandmarkFootprintBiome(biome)) {
                continue;
            }

            int chance = clampChance(BASE_CHANCE + biomeWeight(biome));
            if (Math.floorMod((int) (hash >>> 36), 100) >= chance) {
                continue;
            }

            ThermalVentArchetype archetype = chooseArchetype(hash >>> 48);
            return new ThermalVentSavedData.VentRecord(regionX, regionZ, x, z, archetype);
        }

        return null;
    }

    @Nullable
    public static ThermalVentSavedData.VentRecord findNearestVent(ServerLevel level, BlockPos origin) {
        ThermalVentSavedData ventData = ThermalVentSavedData.get(level.getServer());
        int originRegionX = Math.floorDiv(origin.getX() >> 4, REGION_SIZE);
        int originRegionZ = Math.floorDiv(origin.getZ() >> 4, REGION_SIZE);
        ThermalVentSavedData.VentRecord nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int rx = -LOCATE_SCAN_RADIUS; rx <= LOCATE_SCAN_RADIUS; rx++) {
            for (int rz = -LOCATE_SCAN_RADIUS; rz <= LOCATE_SCAN_RADIUS; rz++) {
                ThermalVentSavedData.VentRecord record = ventData.getOrCreate(level, originRegionX + rx, originRegionZ + rz);
                if (record == null) {
                    continue;
                }
                double distance = origin.distSqr(new BlockPos(record.x(), origin.getY(), record.z()));
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = record;
                }
            }
        }

        return nearest;
    }

    private static ThermalVentSnapshot updateRecord(ServerLevel level, ThermalVentSavedData ventData,
                                                    ThermalVentSavedData.VentRecord record,
                                                    int phase, float progress, long worldTime) {
        ThermalVentState state = ThermalVentState.DORMANT;
        int warmthRadius = 0;
        float warmthFloor = Float.NEGATIVE_INFINITY;
        int rimRadius = 0;
        float rimOverheat = 0.0f;
        int eruptionRadius = 0;
        float eruptionHeat = 0.0f;
        boolean dirty = false;

        switch (record.archetype()) {
            case WARM -> {
                if (!record.spent() && record.activatedAt() < 0L && phase >= 6) {
                    record.setActivatedAt(worldTime);
                    dirty = true;
                }
                if (record.activatedAt() >= 0L && !record.spent()) {
                    if (worldTime - record.activatedAt() >= WARM_DURATION_TICKS) {
                        record.setSpent(true);
                        dirty = true;
                        state = ThermalVentState.SPENT;
                    } else {
                        state = ThermalVentState.ACTIVE;
                        warmthRadius = WARM_RADIUS;
                        warmthFloor = WARM_FLOOR;
                        rimRadius = 1;
                        rimOverheat = WARM_RIM_HEAT;
                    }
                } else if (record.spent()) {
                    state = ThermalVentState.SPENT;
                }
            }
            case ACTIVE -> {
                if (PhaseManager.isPhase6MidOrLater(phase, progress)) {
                    if (record.eruptionEndTick() > worldTime) {
                        state = ThermalVentState.ERUPTING;
                    } else {
                        if (record.nextEventTick() < 0L) {
                            record.setNextEventTick(worldTime + randomInterval(level, record, worldTime, ACTIVE_MIN_INTERVAL, ACTIVE_MAX_INTERVAL, 11L));
                            dirty = true;
                        }
                        if (worldTime >= record.nextEventTick()) {
                            startActiveBurst(level, record, worldTime);
                            dirty = true;
                            state = ThermalVentState.ERUPTING;
                        } else {
                            state = ThermalVentState.ACTIVE;
                        }
                    }
                    warmthRadius = ACTIVE_RADIUS;
                    warmthFloor = ACTIVE_FLOOR;
                    rimRadius = RIM_RADIUS;
                    rimOverheat = ACTIVE_RIM_HEAT;
                    eruptionRadius = ACTIVE_ERUPTION_RADIUS;
                    eruptionHeat = ACTIVE_ERUPTION_HEAT;
                }
            }
            case RUPTURE -> {
                if (PhaseManager.isVacuumActive(phase, progress)) {
                    if (record.eruptionEndTick() > worldTime) {
                        state = ThermalVentState.ERUPTING;
                    } else {
                        if (record.nextEventTick() < 0L) {
                            record.setNextEventTick(worldTime + RUPTURE_WARNING_DURATION
                                    + randomInterval(level, record, worldTime, RUPTURE_MIN_INTERVAL, RUPTURE_MAX_INTERVAL, 23L));
                            dirty = true;
                        }

                        long warningTick = record.nextEventTick() - RUPTURE_WARNING_DURATION;
                        if (worldTime == warningTick) {
                            playRuptureWarning(level, record);
                        }

                        if (worldTime >= record.nextEventTick()) {
                            startRuptureBurst(level, record, worldTime);
                            dirty = true;
                            state = ThermalVentState.ERUPTING;
                        } else if (worldTime >= warningTick) {
                            state = ThermalVentState.WARNING;
                        } else {
                            state = ThermalVentState.ACTIVE;
                        }
                    }
                    warmthRadius = ACTIVE_RADIUS;
                    warmthFloor = ACTIVE_FLOOR;
                    rimRadius = RIM_RADIUS;
                    rimOverheat = RUPTURE_RIM_HEAT;
                    eruptionRadius = RUPTURE_ERUPTION_RADIUS;
                    eruptionHeat = RUPTURE_ERUPTION_HEAT;
                }
            }
        }

        if (dirty) {
            ventData.markDirty();
        }

        return new ThermalVentSnapshot(
                new BlockPos(record.x(), record.y(), record.z()),
                new BlockPos(record.x(), record.y(), record.z()),
                record.archetype(),
                state,
                warmthRadius,
                warmthFloor,
                rimRadius,
                rimOverheat,
                eruptionRadius,
                eruptionHeat
        );
    }

    private static void startActiveBurst(ServerLevel level, ThermalVentSavedData.VentRecord record, long worldTime) {
        record.setEruptionEndTick(worldTime + ACTIVE_BURST_DURATION);
        record.setNextEventTick(record.eruptionEndTick()
                + randomInterval(level, record, worldTime, ACTIVE_MIN_INTERVAL, ACTIVE_MAX_INTERVAL, 37L));

        BlockPos pos = record.anchorPos();
        level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.7f, 0.8f);
        applyBurstDamage(level, pos, ACTIVE_ERUPTION_RADIUS, 6.0f, 0.2f, 0.4f);
    }

    private static void startRuptureBurst(ServerLevel level, ThermalVentSavedData.VentRecord record, long worldTime) {
        record.setEruptionEndTick(worldTime + RUPTURE_BURST_DURATION);
        record.setNextEventTick(record.eruptionEndTick()
                + randomInterval(level, record, worldTime, RUPTURE_MIN_INTERVAL, RUPTURE_MAX_INTERVAL, 61L));

        BlockPos pos = record.anchorPos();
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.55f, 0.5f);
        applyBurstDamage(level, pos, RUPTURE_ERUPTION_RADIUS, 10.0f, 0.35f, 0.7f);
        applyRuptureScar(level, pos);
    }

    private static void playRuptureWarning(ServerLevel level, ThermalVentSavedData.VentRecord record) {
        BlockPos pos = record.anchorPos();
        level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.55f, 0.7f);
    }

    private static void applyBurstDamage(ServerLevel level, BlockPos center, int radius, float maxDamage,
                                         float horizontalPush, float verticalPush) {
        AABB area = new AABB(center).inflate(radius + 0.5D, 2.0D, radius + 0.5D);
        DamageSource damageSource = new DamageSource(
                level.registryAccess()
                        .lookupOrThrow(Registries.DAMAGE_TYPE)
                        .getOrThrow(ModDamageTypes.HYPERTHERMIA)
        );

        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area)) {
            double dx = living.getX() - (center.getX() + 0.5D);
            double dz = living.getZ() - (center.getZ() + 0.5D);
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDistance > radius + 0.5D) {
                continue;
            }

            float falloff = (float) Math.max(0.15D, 1.0D - (horizontalDistance / (radius + 0.5D)));
            living.hurt(damageSource, maxDamage * falloff);

            double normX = horizontalDistance > 0.001D ? dx / horizontalDistance : 0.0D;
            double normZ = horizontalDistance > 0.001D ? dz / horizontalDistance : 0.0D;
            living.push(normX * horizontalPush * falloff, verticalPush * falloff, normZ * horizontalPush * falloff);
            living.hurtMarked = true;
        }
    }

    private static void applyVisualState(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                         ThermalVentSnapshot snapshot, int phase, float progress, long worldTime) {
        clearColdDeposition(level, record.anchorPos(), phase, snapshot);

        int steamCount = 0;
        double ySpeed = 0.03D;
        if (snapshot.isErupting()) {
            steamCount = snapshot.archetype() == ThermalVentArchetype.RUPTURE ? 34 : 20;
            ySpeed = snapshot.archetype() == ThermalVentArchetype.RUPTURE ? 0.26D : 0.18D;
            spawnSteam(level, record.anchorPos(), steamCount, 0.34D, ySpeed, true);
            return;
        }

        if (snapshot.isWarning()) {
            if (worldTime % 4L == 0L) {
                spawnSteam(level, record.anchorPos(), 16, 0.25D, 0.10D, false);
            }
            return;
        }

        if (snapshot.state() == ThermalVentState.ACTIVE) {
            if (worldTime % 8L == 0L) {
                steamCount = snapshot.archetype() == ThermalVentArchetype.WARM ? 6 : 10;
                ySpeed = snapshot.archetype() == ThermalVentArchetype.WARM ? 0.05D : 0.08D;
                spawnSteam(level, record.anchorPos(), steamCount, 0.20D, ySpeed, false);
            }
            return;
        }

        if (phase == 5 && worldTime % 60L == 0L) {
            spawnSteam(level, record.anchorPos(), 3, 0.16D, 0.03D, false);
        } else if (phase >= 4 && worldTime % 100L == 0L) {
            spawnSteam(level, record.anchorPos(), 2, 0.12D, 0.02D, false);
        }
    }

    private static void spawnSteam(ServerLevel level, BlockPos center, int count, double horizontalSpread,
                                   double ySpeed, boolean geyser) {
        double x = center.getX() + 0.5D;
        double y = center.getY() + 0.2D;
        double z = center.getZ() + 0.5D;
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, x, y, z, count,
                horizontalSpread, 0.08D, horizontalSpread, ySpeed);
        if (geyser) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, x, y + 0.1D, z,
                    Math.max(8, count / 2), 0.18D, 0.05D, 0.18D, ySpeed * 1.6D);
        }
    }

    private static void clearColdDeposition(ServerLevel level, BlockPos center, int phase, ThermalVentSnapshot snapshot) {
        int radius = switch (snapshot.state()) {
            case ERUPTING -> 4;
            case WARNING, ACTIVE -> snapshot.archetype() == ThermalVentArchetype.WARM ? 2 : 3;
            default -> phase >= 5 ? 1 : 0;
        };
        if (radius <= 0) {
            return;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                for (int dy = 0; dy <= 3; dy++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
                            || state.is(Blocks.POWDER_SNOW) || state.is(ModBlocks.FROZEN_ATMOSPHERE.get())) {
                        level.destroyBlock(cursor, false);
                    }
                }
            }
        }
    }

    private static void applyRuptureScar(ServerLevel level, BlockPos center) {
        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq < 4 || distSq > 10 || random.nextFloat() > 0.5f) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                cursor.set(x, y, z);
                if (!level.getBlockState(cursor).is(ModBlocks.THERMAL_VENT_POOL.get())) {
                    level.setBlock(cursor, ModBlocks.SCORCHED_GROUND.get().defaultBlockState(), 3);
                }
            }
        }
    }

    private static void applyPoolHeatStage(ServerLevel level, ThermalVentSavedData.VentRecord record, ThermalVentSnapshot snapshot) {
        int stage = switch (snapshot.archetype()) {
            case WARM -> snapshot.state() == ThermalVentState.ACTIVE ? 1 : 0;
            case ACTIVE -> snapshot.state() == ThermalVentState.DORMANT ? 0 : 2;
            case RUPTURE -> snapshot.state() == ThermalVentState.DORMANT ? 0 : 3;
        };

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx * dx + dz * dz > 2) {
                    continue;
                }
                cursor.set(record.x() + dx, record.y(), record.z() + dz);
                BlockState state = level.getBlockState(cursor);
                if (state.is(ModBlocks.THERMAL_VENT_POOL.get())
                        && state.getValue(com.frozendawn.block.ThermalVentPoolBlock.HEAT_STAGE) != stage) {
                    level.setBlock(cursor, state.setValue(com.frozendawn.block.ThermalVentPoolBlock.HEAT_STAGE, stage), 3);
                }
            }
        }
    }

    private static boolean surfaceVent(ServerLevel level, ThermalVentSavedData.VentRecord record) {
        int centerGroundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, record.x(), record.z()) - 1;
        if (centerGroundY <= level.getMinBuildHeight() + 1) {
            return false;
        }

        int basinFloorY = centerGroundY - 1;
        int rimY = centerGroundY;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > 3.25D) {
                    continue;
                }

                int x = record.x() + dx;
                int z = record.z() + dz;
                int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (topY <= level.getMinBuildHeight()) {
                    continue;
                }

                int targetY = distance <= 2.25D ? basinFloorY : rimY;
                BlockState targetState = distance <= 1.45D
                        ? ModBlocks.THERMAL_VENT_POOL.get().defaultBlockState()
                        : ModBlocks.SULFUR_CRUST.get().defaultBlockState();

                if (topY < targetY) {
                    for (int fillY = topY + 1; fillY < targetY; fillY++) {
                        cursor.set(x, fillY, z);
                        level.setBlock(cursor, ModBlocks.SULFUR_CRUST.get().defaultBlockState(), 3);
                    }
                }

                int clearStart = Math.min(topY, targetY) + 1;
                int clearEnd = Math.max(topY, targetY) + 2;
                for (int clearY = clearStart; clearY <= clearEnd; clearY++) {
                    cursor.set(x, clearY, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || state.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                }

                cursor.set(x, targetY, z);
                level.setBlock(cursor, targetState, 3);
            }
        }

        record.setY(basinFloorY);
        record.setSurfaced(true);
        FrozenDawn.LOGGER.debug("Thermal vent surfaced at ({}, {}, {}) as {}",
                record.x(), record.y(), record.z(), record.archetype().getSerializedName());
        return true;
    }

    private static boolean isPlacementAreaLoaded(ServerLevel level, int centerX, int centerZ) {
        int radius = 8;
        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minZ = centerZ - radius;
        int maxZ = centerZ + radius;
        for (int x = minX; x <= maxX; x += 16) {
            for (int z = minZ; z <= maxZ; z += 16) {
                if (!level.isLoaded(new BlockPos(x, level.getMinBuildHeight(), z))) {
                    return false;
                }
            }
        }
        return level.isLoaded(new BlockPos(maxX, level.getMinBuildHeight(), maxZ));
    }

    private static ThermalVentArchetype chooseArchetype(long value) {
        int roll = Math.floorMod((int) value, 100);
        if (roll < 60) {
            return ThermalVentArchetype.WARM;
        }
        if (roll < 90) {
            return ThermalVentArchetype.ACTIVE;
        }
        return ThermalVentArchetype.RUPTURE;
    }

    private static int biomeWeight(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> {
            if (BONUS_BIOMES.contains(key)) {
                return 18;
            }
            if (HOT_BIOMES.contains(key)) {
                return -12;
            }
            return 0;
        }).orElse(0);
    }

    private static int clampChance(int chance) {
        return Math.max(6, Math.min(72, chance));
    }

    private static long packRegion(int regionX, int regionZ) {
        return ((long) regionX << 32) ^ (regionZ & 0xffffffffL);
    }

    private static long regionHash(long seed, int regionX, int regionZ) {
        long hash = seed ^ 0x56454E5453484946L; // "VENTSHIF"
        hash = hash * 6364136223846793005L + regionX * 1442695040888963407L;
        hash = hash * 6364136223846793005L + regionZ * 22695477L;
        return mixHash(hash);
    }

    private static long mixHash(long value) {
        value ^= (value >>> 33);
        value *= 0xff51afd7ed558ccdL;
        value ^= (value >>> 33);
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= (value >>> 33);
        return value;
    }

    private static long randomInterval(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                       long worldTime, long min, long max, long salt) {
        if (max <= min) {
            return min;
        }
        long seed = level.getSeed() ^ (((long) record.x()) << 32) ^ record.z() ^ worldTime ^ salt;
        RandomSource random = RandomSource.create(seed);
        return min + random.nextInt((int) (max - min + 1L));
    }
}
