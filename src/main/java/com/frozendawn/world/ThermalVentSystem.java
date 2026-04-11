package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.ThermalVentPoolBlock;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModDamageTypes;
import com.frozendawn.init.ModFluids;
import com.frozendawn.network.ThermalVentEruptionPayload;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
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
    private static final int RUPTURE_ERUPTION_RADIUS = 7;
    private static final float ACTIVE_ERUPTION_HEAT = 82.0f;
    private static final float RUPTURE_ERUPTION_HEAT = 135.0f;
    private static final long ACTIVE_MIN_INTERVAL = 20L * 20L;
    private static final long ACTIVE_MAX_INTERVAL = 45L * 20L;
    private static final long ACTIVE_BURST_DURATION = 16L;
    private static final long RUPTURE_MIN_INTERVAL = 30L * 20L;
    private static final long RUPTURE_MAX_INTERVAL = 75L * 20L;
    private static final long RUPTURE_WARNING_DURATION = 5L * 20L;
    private static final long RUPTURE_BURST_DURATION = 24L;
    private static final int MAX_CONE_STAGE = 12;
    private static final int RUPTURE_LAVA_START_STAGE = 3;
    private static final int RUPTURE_BOMBARDMENT_STAGE = 5;
    private static final int MATURE_RUPTURE_STAGE = 4;
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
        return findNearestVent(level, origin, null, LOCATE_SCAN_RADIUS);
    }

    @Nullable
    public static ThermalVentSavedData.VentRecord findNearestVent(ServerLevel level, BlockPos origin,
                                                                  @Nullable ThermalVentArchetype archetypeFilter,
                                                                  int scanRadius) {
        ThermalVentSavedData ventData = ThermalVentSavedData.get(level.getServer());
        int originRegionX = Math.floorDiv(origin.getX() >> 4, REGION_SIZE);
        int originRegionZ = Math.floorDiv(origin.getZ() >> 4, REGION_SIZE);
        ThermalVentSavedData.VentRecord nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int rx = -scanRadius; rx <= scanRadius; rx++) {
            for (int rz = -scanRadius; rz <= scanRadius; rz++) {
                ThermalVentSavedData.VentRecord record = ventData.getOrCreate(level, originRegionX + rx, originRegionZ + rz);
                if (record == null || archetypeFilter != null && record.archetype() != archetypeFilter) {
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
                            startActiveBurst(level, record, worldTime, phase, progress);
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
                                    + ruptureInterval(level, record, worldTime, 23L));
                            dirty = true;
                        }

                        long warningTick = record.nextEventTick() - RUPTURE_WARNING_DURATION;
                        if (worldTime == warningTick) {
                            playRuptureWarning(level, record, phase, progress);
                        }

                        if (worldTime >= record.nextEventTick()) {
                            startRuptureBurst(level, record, worldTime, phase, progress);
                            dirty = true;
                            state = ThermalVentState.ERUPTING;
                        } else if (worldTime >= warningTick) {
                            state = ThermalVentState.WARNING;
                        } else {
                            state = ThermalVentState.ACTIVE;
                        }
                    }
                    warmthRadius = ACTIVE_RADIUS + Math.max(0, record.coneStage() / 2);
                    warmthFloor = ACTIVE_FLOOR + Math.min(16.0f, record.coneStage() * 1.6f);
                    rimRadius = RIM_RADIUS + 1 + Math.max(1, record.coneStage() / 3);
                    rimOverheat = RUPTURE_RIM_HEAT + record.coneStage() * 7.5f;
                    eruptionRadius = RUPTURE_ERUPTION_RADIUS + 1 + Math.max(0, record.coneStage() / 2);
                    eruptionHeat = RUPTURE_ERUPTION_HEAT + record.coneStage() * 14.0f;
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
                record.coneStage(),
                warmthRadius,
                warmthFloor,
                rimRadius,
                rimOverheat,
                eruptionRadius,
                eruptionHeat
        );
    }

    private static void startActiveBurst(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                         long worldTime, int phase, float progress) {
        record.setEruptionEndTick(worldTime + ACTIVE_BURST_DURATION);
        record.setNextEventTick(record.eruptionEndTick()
                + randomInterval(level, record, worldTime, ACTIVE_MIN_INTERVAL, ACTIVE_MAX_INTERVAL, 37L));

        BlockPos pos = record.anchorPos();
        if (!PhaseManager.isVacuumActive(phase, progress)) {
            level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.1f, 0.62f);
        }
        sendEruptionImpulse(level, pos, 0.85f, 22, 20.0D);
        spawnActiveBurstParticles(level, pos);
        meltColdTerrain(level, pos, 4, true);
        applyBurstDamage(level, pos, ACTIVE_ERUPTION_RADIUS, 6.0f, 0.2f, 0.4f);
    }

    private static void startRuptureBurst(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                          long worldTime, int phase, float progress) {
        int nextConeStage = Math.min(MAX_CONE_STAGE, record.coneStage() + 1);
        record.setConeStage(nextConeStage);
        record.setEruptionEndTick(worldTime + RUPTURE_BURST_DURATION);
        record.setNextEventTick(record.eruptionEndTick() + ruptureInterval(level, record, worldTime, 61L));

        BlockPos pos = record.anchorPos();
        if (!PhaseManager.isVacuumActive(phase, progress)) {
            level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.5f, 0.46f);
        }
        growRuptureCone(level, record);
        maintainRuptureLava(level, record);
        applyVolcanicField(level, record, true);
        sendEruptionImpulse(level, pos, 1.55f + nextConeStage * 0.16f, 42 + nextConeStage * 5, 32.0D + nextConeStage * 2.8D);
        spawnRuptureBurstParticles(level, record);
        meltColdTerrain(level, pos, 6 + nextConeStage, true);
        applyBurstDamage(level, pos, RUPTURE_ERUPTION_RADIUS + nextConeStage / 2,
                14.0f + nextConeStage * 1.8f, 0.50f + nextConeStage * 0.05f, 0.90f + nextConeStage * 0.07f);
        applyRuptureScar(level, record);
        if (nextConeStage >= RUPTURE_BOMBARDMENT_STAGE) {
            spawnVolcanicBombardment(level, record, phase, progress);
        }
    }

    private static void playRuptureWarning(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                           int phase, float progress) {
        if (PhaseManager.isVacuumActive(phase, progress)) {
            return;
        }
        level.playSound(null, record.anchorPos(), SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.7f, 0.6f);
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
        meltColdTerrain(level, record.anchorPos(), switch (snapshot.state()) {
            case ERUPTING -> snapshot.archetype() == ThermalVentArchetype.RUPTURE ? 6 : 4;
            case WARNING, ACTIVE -> snapshot.archetype() == ThermalVentArchetype.WARM ? 2 : 3;
            default -> phase >= 5 ? 1 : 0;
        }, snapshot.state() == ThermalVentState.ERUPTING || snapshot.archetype() != ThermalVentArchetype.WARM);

        if (snapshot.archetype() == ThermalVentArchetype.RUPTURE && snapshot.state().contributesWarmth()) {
            maintainRuptureLava(level, record);
            if (snapshot.isErupting() || snapshot.isWarning() || worldTime % 40L == 0L) {
                applyVolcanicField(level, record, snapshot.isErupting());
            }
        }

        if (snapshot.isErupting()) {
            if (snapshot.archetype() == ThermalVentArchetype.RUPTURE) {
                spawnSteamColumn(level, record.anchorPos(), 16, 32, 0.48D, 0.20D, true, true);
                if (worldTime % 2L == 0L) {
                    spawnRupturePlume(level, record, true);
                }
                spawnMatureRuptureAmbient(level, record, worldTime, true);
            } else {
                spawnSteamColumn(level, record.anchorPos(), 10, 22, 0.32D, 0.12D, true, false);
            }
            return;
        }

        if (snapshot.isWarning()) {
            if (worldTime % 3L == 0L) {
                spawnSteamColumn(level, record.anchorPos(), 10, 18, 0.28D, 0.10D, false, true);
                spawnGroundStress(level, record.anchorPos());
                spawnRupturePlume(level, record, false);
            }
            spawnMatureRuptureAmbient(level, record, worldTime, false);
            return;
        }

        if (snapshot.state() == ThermalVentState.ACTIVE) {
            if (snapshot.archetype() == ThermalVentArchetype.WARM) {
                if (worldTime % 14L == 0L) {
                    spawnSteamColumn(level, record.anchorPos(), 4, 6, 0.16D, 0.045D, false, false);
                }
                if (!PhaseManager.isVacuumActive(phase, progress) && worldTime % 120L == 0L) {
                    playAmbientBoil(level, record.anchorPos(), 0.45f, 1.22f);
                }
            } else {
                if (worldTime % 8L == 0L) {
                    spawnSteamColumn(level, record.anchorPos(), 7, 12, 0.24D, 0.070D, false, false);
                }
                if (snapshot.archetype() == ThermalVentArchetype.RUPTURE && record.coneStage() >= 5 && worldTime % 12L == 0L) {
                    spawnRupturePlume(level, record, false);
                }
                spawnMatureRuptureAmbient(level, record, worldTime, false);
                if (!PhaseManager.isVacuumActive(phase, progress) && worldTime % 80L == 0L) {
                    playAmbientBoil(level, record.anchorPos(), 0.65f, 0.95f);
                }
            }
            return;
        }

        if (phase == 5 && worldTime % 40L == 0L) {
            spawnSteamColumn(level, record.anchorPos(), 2, 4, 0.14D, 0.022D, false, false);
        } else if (phase >= 4 && worldTime % 100L == 0L) {
            spawnSteamColumn(level, record.anchorPos(), 1, 1, 0.10D, 0.012D, false, false);
        }
    }

    private static void spawnSteamColumn(ServerLevel level, BlockPos center, int signalCount, int cloudCount,
                                         double horizontalSpread, double ySpeed, boolean geyser, boolean rupture) {
        double x = center.getX() + 0.5D;
        double y = center.getY() + 0.15D;
        double z = center.getZ() + 0.5D;

        if (signalCount > 0) {
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y, z, signalCount,
                    horizontalSpread, 0.10D, horizontalSpread, ySpeed);
        }
        if (cloudCount > 0) {
            level.sendParticles(ParticleTypes.CLOUD, x, y, z, cloudCount,
                    horizontalSpread, 0.08D, horizontalSpread, ySpeed * 0.6D);
        }
        if (geyser) {
            level.sendParticles(ParticleTypes.SPLASH, x, y + 0.1D, z,
                    Math.max(8, cloudCount), 0.18D, 0.05D, 0.18D, ySpeed * 1.8D);
        }
        if (rupture) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.2D, z, 18,
                    0.38D, 0.20D, 0.38D, 0.03D);
            level.sendParticles(ParticleTypes.SPLASH, x, y + 0.2D, z, 14,
                    0.24D, 0.10D, 0.24D, ySpeed * 2.2D);
            level.sendParticles(ParticleTypes.ASH, x, y + 0.35D, z, 18,
                    0.40D, 0.20D, 0.40D, 0.02D);
            level.sendParticles(ParticleTypes.WHITE_ASH, x, y + 0.35D, z, 10,
                    0.34D, 0.14D, 0.34D, 0.015D);
        }
    }

    private static void spawnRupturePlume(ServerLevel level, ThermalVentSavedData.VentRecord record, boolean erupting) {
        BlockPos center = record.anchorPos();
        double mouthX = center.getX() + 0.5D;
        double mouthY = ruptureMouthY(record) + 0.4D;
        double mouthZ = center.getZ() + 0.5D;
        int coneStage = record.coneStage();
        int jetCount = erupting ? 26 + coneStage * 4 : 10 + coneStage * 2;
        int capCount = erupting ? 18 + coneStage * 3 : 7 + coneStage;
        int falloutCount = erupting ? 22 + coneStage * 3 : 8 + coneStage;
        double jetHeight = 10.0D + coneStage * 1.6D;
        double capY = mouthY + jetHeight;
        double jetSpread = 0.22D + coneStage * 0.018D;
        double capSpread = 1.25D + coneStage * 0.22D;
        double falloutRadius = 3.0D + coneStage * 0.55D;

        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mouthX, mouthY, mouthZ,
                erupting ? 18 + coneStage * 2 : 8 + coneStage, 0.34D, 0.22D, 0.34D, erupting ? 0.08D : 0.03D);
        level.sendParticles(ParticleTypes.ASH, mouthX, mouthY, mouthZ, jetCount,
                jetSpread, 0.35D, jetSpread, erupting ? 0.34D : 0.16D);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, mouthX, mouthY, mouthZ, jetCount / 2,
                jetSpread * 0.8D, 0.24D, jetSpread * 0.8D, erupting ? 0.28D : 0.14D);

        level.sendParticles(ParticleTypes.WHITE_ASH, mouthX, capY, mouthZ, capCount,
                capSpread, 0.22D, capSpread, erupting ? 0.045D : 0.02D);
        level.sendParticles(ParticleTypes.CLOUD, mouthX, capY, mouthZ, Math.max(8, capCount / 2),
                capSpread * 0.9D, 0.10D, capSpread * 0.9D, 0.012D);

        level.sendParticles(ParticleTypes.ASH, mouthX, capY - 0.7D, mouthZ, falloutCount,
                falloutRadius, 0.25D, falloutRadius, -0.03D);
        level.sendParticles(ParticleTypes.WHITE_ASH, mouthX, capY - 0.5D, mouthZ, Math.max(6, falloutCount / 3),
                falloutRadius * 0.8D, 0.16D, falloutRadius * 0.8D, -0.018D);
    }

    private static void spawnMatureRuptureAmbient(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                                  long worldTime, boolean erupting) {
        if (record.coneStage() < MATURE_RUPTURE_STAGE) {
            return;
        }
        if (worldTime % 8L == 0L) {
            spawnPersistentAshCanopy(level, record, erupting);
        }
        if (worldTime % 10L == 0L) {
            spawnSatelliteFumaroles(level, record, erupting);
        }
    }

    private static void spawnPersistentAshCanopy(ServerLevel level, ThermalVentSavedData.VentRecord record, boolean erupting) {
        double x = record.x() + 0.5D;
        double z = record.z() + 0.5D;
        double canopyY = ruptureMouthY(record) + 7.0D + record.coneStage() * 0.8D;
        double canopySpread = 1.8D + record.coneStage() * 0.35D;
        int ashCount = erupting ? 12 + record.coneStage() * 2 : 7 + record.coneStage();
        int whiteAshCount = Math.max(5, ashCount / 3);

        level.sendParticles(ParticleTypes.ASH, x, canopyY, z, ashCount,
                canopySpread, 0.18D, canopySpread, 0.003D);
        level.sendParticles(ParticleTypes.WHITE_ASH, x, canopyY + 0.5D, z, whiteAshCount,
                canopySpread * 0.75D, 0.12D, canopySpread * 0.75D, 0.001D);
        level.sendParticles(ParticleTypes.CLOUD, x, canopyY + 0.7D, z, Math.max(4, whiteAshCount / 2),
                canopySpread * 0.6D, 0.05D, canopySpread * 0.6D, 0.0D);

        if (record.coneStage() >= 6) {
            double falloutSpread = 2.6D + record.coneStage() * 0.45D;
            level.sendParticles(ParticleTypes.ASH, x, canopyY - 0.8D, z, Math.max(8, ashCount / 2),
                    falloutSpread, 0.15D, falloutSpread, -0.015D);
        }
    }

    private static void spawnSatelliteFumaroles(ServerLevel level, ThermalVentSavedData.VentRecord record, boolean erupting) {
        int points = record.coneStage() >= 8 ? 6 : 4;
        double radius = 4.5D + record.coneStage() * 0.6D;
        double angleOffset = spillDirection(record).get2DDataValue() * (Math.PI / 6.0D);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int i = 0; i < points; i++) {
            double angle = angleOffset + (Mth.TWO_PI * i / points);
            int x = record.x() + Mth.floor(Math.cos(angle) * radius);
            int z = record.z() + Mth.floor(Math.sin(angle) * radius);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (y <= level.getMinBuildHeight()) {
                continue;
            }

            cursor.set(x, y, z);
            BlockState surface = level.getBlockState(cursor);
            if (surface.is(Blocks.BEDROCK) || surface.is(ModBlocks.THERMAL_VENT_POOL.get()) || surface.is(ModBlocks.VENT_LAVA.get())) {
                continue;
            }
            if (surface.isAir() || surface.is(BlockTags.REPLACEABLE) || isFragileSurface(surface)
                    || surface.is(Blocks.DIRT) || surface.is(Blocks.GRASS_BLOCK)
                    || surface.is(ModBlocks.FROZEN_DIRT.get()) || surface.is(ModBlocks.FROZEN_SAND.get())
                    || surface.is(Blocks.STONE) || surface.is(Blocks.COBBLESTONE) || surface.is(Blocks.SAND)) {
                level.setBlock(cursor, (i & 1) == 0
                        ? ModBlocks.SULFUR_CRUST.get().defaultBlockState()
                        : ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState(), 3);
            }

            double px = x + 0.5D;
            double py = y + 0.25D;
            double pz = z + 0.5D;
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, px, py, pz,
                    erupting ? 4 : 2, 0.12D, 0.06D, 0.12D, erupting ? 0.09D : 0.05D);
            level.sendParticles(ParticleTypes.CLOUD, px, py, pz,
                    erupting ? 4 : 2, 0.10D, 0.04D, 0.10D, 0.03D);
        }
    }

    private static void meltColdTerrain(ServerLevel level, BlockPos center, int radius, boolean aggressive) {
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
                    BlockState thawed = resolveThawedState(state);
                    if (thawed != null) {
                        level.setBlock(cursor, thawed, 3);
                    } else if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
                            || state.is(Blocks.POWDER_SNOW) || state.is(ModBlocks.FROZEN_ATMOSPHERE.get())) {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                    }
                }

                if (!aggressive) {
                    continue;
                }

                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        center.getX() + dx, center.getZ() + dz) - 1;
                cursor.set(center.getX() + dx, surfaceY, center.getZ() + dz);
                BlockState surfaceState = level.getBlockState(cursor);
                if (surfaceState.is(ModBlocks.THERMAL_VENT_POOL.get()) || surfaceState.is(Blocks.BEDROCK)) {
                    continue;
                }
                if (dx * dx + dz * dz <= 3) {
                    level.setBlock(cursor, ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState(), 3);
                } else if (surfaceState.isAir() || surfaceState.is(BlockTags.REPLACEABLE)) {
                    level.setBlock(cursor, ModBlocks.SULFUR_CRUST.get().defaultBlockState(), 3);
                }
            }
        }
    }

    @Nullable
    private static BlockState resolveThawedState(BlockState state) {
        if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) {
            return Blocks.WATER.defaultBlockState();
        }
        if (state.is(ModBlocks.FROZEN_DIRT.get())) {
            return Blocks.DIRT.defaultBlockState();
        }
        if (state.is(ModBlocks.FROZEN_SAND.get())) {
            return Blocks.SAND.defaultBlockState();
        }
        if (state.is(ModBlocks.FROZEN_COBBLESTONE.get())) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        if (state.is(ModBlocks.FROZEN_STONE_BRICKS.get())) {
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
        return null;
    }

    private static void playAmbientBoil(ServerLevel level, BlockPos pos, float volume, float pitch) {
        level.playSound(null, pos, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, volume, pitch);
    }

    private static void spawnGroundStress(ServerLevel level, BlockPos center) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState()),
                center.getX() + 0.5D, center.getY() + 0.05D, center.getZ() + 0.5D,
                10, 0.55D, 0.05D, 0.55D, 0.02D);
    }

    private static void spawnActiveBurstParticles(ServerLevel level, BlockPos center) {
        double x = center.getX() + 0.5D;
        double y = center.getY() + 0.2D;
        double z = center.getZ() + 0.5D;
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y, z, 18, 0.24D, 0.08D, 0.24D, 0.12D);
        level.sendParticles(ParticleTypes.CLOUD, x, y, z, 24, 0.28D, 0.10D, 0.28D, 0.10D);
        level.sendParticles(ParticleTypes.SPLASH, x, y + 0.1D, z, 16, 0.22D, 0.08D, 0.22D, 0.28D);
    }

    private static void spawnRuptureBurstParticles(ServerLevel level, ThermalVentSavedData.VentRecord record) {
        BlockPos center = record.anchorPos();
        double x = center.getX() + 0.5D;
        double y = center.getY() + 0.2D + (record.coneStage() * 0.35D);
        double z = center.getZ() + 0.5D;
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y, z, 28, 0.34D, 0.12D, 0.34D, 0.18D);
        level.sendParticles(ParticleTypes.CLOUD, x, y, z, 32, 0.36D, 0.16D, 0.36D, 0.14D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 28, 0.52D, 0.24D, 0.52D, 0.05D);
        level.sendParticles(ParticleTypes.SPLASH, x, y, z, 22, 0.42D, 0.16D, 0.42D, 0.10D);
        level.sendParticles(ParticleTypes.ASH, x, y, z, 36, 0.62D, 0.30D, 0.62D, 0.03D);
        level.sendParticles(ParticleTypes.WHITE_ASH, x, y, z, 20, 0.44D, 0.22D, 0.44D, 0.02D);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState()),
                x, y, z, 18, 0.45D, 0.18D, 0.45D, 0.03D);
    }

    private static void applyRuptureScar(ServerLevel level, ThermalVentSavedData.VentRecord record) {
        BlockPos center = record.anchorPos();
        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int scarRadius = 6 + record.coneStage();
        for (int dx = -scarRadius; dx <= scarRadius; dx++) {
            for (int dz = -scarRadius; dz <= scarRadius; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq < 4 || distSq > scarRadius * scarRadius || random.nextFloat() > 0.58f) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                cursor.set(x, y, z);
                BlockState state = level.getBlockState(cursor);
                if (state.is(ModBlocks.THERMAL_VENT_POOL.get()) || state.is(Blocks.BEDROCK)) {
                    continue;
                }
                if (isFragileSurface(state) && random.nextFloat() < 0.45F) {
                    level.destroyBlock(cursor, false);
                    continue;
                }
                BlockState scarState = distSq <= 8
                        ? ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState()
                        : distSq <= 20 + record.coneStage() * 2
                        ? ModBlocks.SCORCHED_GROUND.get().defaultBlockState()
                        : distSq <= 32 + record.coneStage() * 3
                        ? ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState()
                        : ModBlocks.SCORCHED_GROUND.get().defaultBlockState();
                level.setBlock(cursor, scarState, 3);
            }
        }
    }

    private static void applyVolcanicField(ServerLevel level, ThermalVentSavedData.VentRecord record, boolean eruptionPulse) {
        int coneStage = record.coneStage();
        if (coneStage <= 0) {
            return;
        }

        int fieldRadius = 9 + coneStage * 2;
        int innerRadius = 4 + coneStage;
        int middleRadius = 7 + coneStage * 2;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -fieldRadius; dx <= fieldRadius; dx++) {
            for (int dz = -fieldRadius; dz <= fieldRadius; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > fieldRadius * fieldRadius) {
                    continue;
                }

                int x = record.x() + dx;
                int z = record.z() + dz;
                clearVolcanicColumn(level, x, z, record.y(), coneStage, eruptionPulse);

                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                cursor.set(x, surfaceY, z);
                BlockState state = level.getBlockState(cursor);
                if (state.is(Blocks.BEDROCK) || state.is(ModBlocks.THERMAL_VENT_POOL.get()) || state.is(ModBlocks.VENT_LAVA.get())) {
                    continue;
                }

                if (state.isAir() || state.is(ModBlocks.THERMAL_VENT_POOL.get()) || state.is(ModBlocks.VENT_LAVA.get())) {
                    continue;
                }

                BlockState replacement;
                if (distSq <= innerRadius * innerRadius) {
                    replacement = coneStage >= 6
                            ? ModBlocks.SCORCHED_GROUND.get().defaultBlockState()
                            : ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState();
                } else if (distSq <= middleRadius * middleRadius) {
                    long mix = mixHash((((long) x) << 32) ^ z ^ (coneStage * 31L));
                    replacement = (mix & 3L) == 0L
                            ? ModBlocks.SULFUR_CRUST.get().defaultBlockState()
                            : (mix & 1L) == 0L
                            ? ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState()
                            : ModBlocks.SCORCHED_GROUND.get().defaultBlockState();
                } else if (state.is(BlockTags.REPLACEABLE)) {
                    continue;
                } else if (isFragileSurface(state)
                        || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                        || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
                        || state.is(ModBlocks.FROZEN_DIRT.get()) || state.is(ModBlocks.FROZEN_SAND.get())
                        || state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.SAND)) {
                    replacement = ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState();
                } else {
                    continue;
                }

                level.setBlock(cursor, replacement, 3);
            }
        }
    }

    private static void clearVolcanicColumn(ServerLevel level, int x, int z, int baseY, int coneStage, boolean eruptionPulse) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int topY = Math.min(level.getMaxBuildHeight() - 1,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 5 + coneStage / 2);

        for (int y = baseY; y <= topY; y++) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            BlockState thawed = resolveThawedState(state);
            if (thawed != null) {
                level.setBlock(cursor, thawed, 3);
                continue;
            }

            if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
                    || state.is(Blocks.POWDER_SNOW) || state.is(ModBlocks.FROZEN_ATMOSPHERE.get())
                    || state.is(ModBlocks.ACHERONITE_CRYSTAL.get())) {
                level.destroyBlock(cursor, false);
                continue;
            }

            if (state.is(BlockTags.FLOWERS)
                    || state.is(BlockTags.CROPS)
                    || state.is(BlockTags.LEAVES)
                    || state.is(Blocks.SHORT_GRASS)
                    || state.is(Blocks.TALL_GRASS)
                    || state.is(Blocks.FERN)
                    || state.is(Blocks.LARGE_FERN)
                    || state.is(Blocks.SUGAR_CANE)
                    || state.is(Blocks.DEAD_BUSH)
                    || state.is(Blocks.CACTUS)
                    || state.is(Blocks.KELP)
                    || state.is(Blocks.KELP_PLANT)
                    || state.is(Blocks.SEAGRASS)
                    || state.is(Blocks.TALL_SEAGRASS)) {
                level.destroyBlock(cursor, false);
                continue;
            }

            if (eruptionPulse && (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE))) {
                level.setBlock(cursor, Blocks.WATER.defaultBlockState(), 3);
            }
        }
    }

    private static void spawnVolcanicBombardment(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                                 int phase, float progress) {
        int stage = record.coneStage();
        RandomSource random = level.getRandom();
        int impactCount = 1 + Math.max(1, (stage - RUPTURE_BOMBARDMENT_STAGE + 2) / 2);

        for (int i = 0; i < impactCount; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            int distance = 6 + random.nextInt(5 + stage);
            int x = record.x() + Mth.floor(Math.cos(angle) * distance);
            int z = record.z() + Mth.floor(Math.sin(angle) * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos impactPos = new BlockPos(x, y, z);
            spawnMeteorTrail(level, record, impactPos);
            applyMeteorImpact(level, impactPos, stage, phase, progress);
        }
    }

    private static void spawnMeteorTrail(ServerLevel level, ThermalVentSavedData.VentRecord record, BlockPos impactPos) {
        double startX = record.x() + 0.5D;
        double startY = ruptureMouthY(record) + 1.0D;
        double startZ = record.z() + 0.5D;
        double dx = impactPos.getX() + 0.5D - startX;
        double dy = impactPos.getY() + 0.5D - startY;
        double dz = impactPos.getZ() + 0.5D - startZ;

        for (int step = 0; step <= 10; step++) {
            double t = step / 10.0D;
            double px = startX + dx * t;
            double py = startY + dy * t + Math.sin(t * Math.PI) * (2.0D + record.coneStage() * 0.25D);
            double pz = startZ + dz * t;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 2, 0.08D, 0.08D, 0.08D, 0.01D);
            level.sendParticles(ParticleTypes.ASH, px, py, pz, 2, 0.10D, 0.06D, 0.10D, 0.005D);
        }
    }

    private static void applyMeteorImpact(ServerLevel level, BlockPos center, int stage, int phase, float progress) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.getX() + 0.5D, center.getY() + 0.4D, center.getZ() + 0.5D,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.getX() + 0.5D, center.getY() + 0.4D, center.getZ() + 0.5D,
                20, 0.45D, 0.20D, 0.45D, 0.03D);
        level.sendParticles(ParticleTypes.ASH, center.getX() + 0.5D, center.getY() + 0.4D, center.getZ() + 0.5D,
                24, 0.55D, 0.25D, 0.55D, 0.02D);
        if (!PhaseManager.isVacuumActive(phase, progress)) {
            level.playSound(null, center, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0f, 0.55f);
        }

        applyBurstDamage(level, center, 2 + stage / 4, 6.0f + stage, 0.30f, 0.55f);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int impactRadius = 1 + Math.max(0, (stage - RUPTURE_BOMBARDMENT_STAGE) / 3);
        for (int dx = -impactRadius; dx <= impactRadius; dx++) {
            for (int dz = -impactRadius; dz <= impactRadius; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > impactRadius * impactRadius) {
                    continue;
                }
                cursor.set(center.getX() + dx, center.getY(), center.getZ() + dz);
                BlockState state = level.getBlockState(cursor);
                if (state.is(Blocks.BEDROCK) || state.is(ModBlocks.THERMAL_VENT_POOL.get()) || state.is(ModBlocks.VENT_LAVA.get())) {
                    continue;
                }
                if (isFragileSurface(state) || state.is(BlockTags.PLANKS) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.STONE_BRICKS)) {
                    level.destroyBlock(cursor, false);
                } else {
                    level.setBlock(cursor,
                            distSq == 0 && stage >= 8
                                    ? ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState()
                                    : ModBlocks.SCORCHED_GROUND.get().defaultBlockState(),
                            3);
                }
            }
        }
    }

    private static boolean isFragileSurface(BlockState state) {
        return state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.REPLACEABLE)
                || state.is(BlockTags.LEAVES)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE);
    }

    private static void sendEruptionImpulse(ServerLevel level, BlockPos center, float strength, int durationTicks, double radius) {
        double radiusSqr = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D) <= radiusSqr) {
                PacketDistributor.sendToPlayer(player, new ThermalVentEruptionPayload(center, strength, durationTicks));
            }
        }
    }

    private static void growRuptureCone(ServerLevel level, ThermalVentSavedData.VentRecord record) {
        int coneStage = record.coneStage();
        if (coneStage <= 0) {
            return;
        }

        int radius = 5 + coneStage + coneStage / 3;
        int baseY = record.y();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int mouthRadius = ruptureMouthRadius(record);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius + 0.25D) {
                    continue;
                }
                if (dx * dx + dz * dz <= mouthRadius * mouthRadius) {
                    continue;
                }

                int desiredLift = Math.max(0, Mth.floor((coneStage * 2.15D + 3.8D) - (dist * 0.98D)));
                if (desiredLift <= 0) {
                    continue;
                }

                int x = record.x() + dx;
                int z = record.z() + dz;
                int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                int targetTopY = Math.max(baseY + desiredLift, topY);
                for (int fillY = topY + 1; fillY <= targetTopY; fillY++) {
                    cursor.set(x, fillY, z);
                    level.setBlock(cursor, ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState(), 3);
                }
                cursor.set(x, targetTopY, z);
                BlockState capState = dist >= radius - 0.85D
                        ? ModBlocks.SCORCHED_GROUND.get().defaultBlockState()
                        : dist >= radius - 2.4D
                        ? ModBlocks.SULFUR_CRUST.get().defaultBlockState()
                        : ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState();
                level.setBlock(cursor, capState, 3);
            }
        }

        int craterTopY = ruptureMouthY(record) - 1;
        for (int clearY = baseY; clearY <= craterTopY + 1; clearY++) {
            for (int dx = -mouthRadius; dx <= mouthRadius; dx++) {
                for (int dz = -mouthRadius; dz <= mouthRadius; dz++) {
                    if (dx * dx + dz * dz > mouthRadius * mouthRadius) {
                        continue;
                    }
                    cursor.set(record.x() + dx, clearY, record.z() + dz);
                    if (clearY == baseY) {
                        level.setBlock(cursor, ModBlocks.THERMAL_VENT_POOL.get().defaultBlockState()
                                .setValue(ThermalVentPoolBlock.HEAT_STAGE, 3), 3);
                    } else {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static void maintainRuptureLava(ServerLevel level, ThermalVentSavedData.VentRecord record) {
        if (record.coneStage() < RUPTURE_LAVA_START_STAGE) {
            return;
        }

        Set<BlockPos> allowedSources = new HashSet<>();
        int throatTopY = ruptureMouthY(record);
        int throatBottomY = Math.max(record.y() + 1, throatTopY - (4 + record.coneStage() / 2));
        int throatRadius = Math.max(1, ruptureMouthRadius(record) - 1);
        fillRuptureThroat(level, record, throatBottomY, throatTopY, throatRadius, allowedSources);

        Direction[] outlets = eruptionOutlets(record);
        for (int outletIndex = 0; outletIndex < outlets.length; outletIndex++) {
            Direction outlet = outlets[outletIndex];
            int breachY = Math.max(throatBottomY + 1, throatTopY - 1 - outletIndex);
            carveFlankSpillway(level, record, throatRadius, breachY, outlet, outletIndex, allowedSources);
        }

        clearObsoleteVentLavaSources(level, record, allowedSources);
    }

    private static int ruptureMouthY(ThermalVentSavedData.VentRecord record) {
        return record.y() + Mth.floor(record.coneStage() * 2.2F) + 4;
    }

    private static int ruptureMouthRadius(ThermalVentSavedData.VentRecord record) {
        return record.coneStage() >= 10 ? 4 : record.coneStage() >= 7 ? 3 : record.coneStage() >= 4 ? 2 : 1;
    }

    private static Direction spillDirection(ThermalVentSavedData.VentRecord record) {
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        int index = Math.floorMod(record.x() * 31 + record.z() * 17, directions.length);
        return directions[index];
    }

    private static Direction[] eruptionOutlets(ThermalVentSavedData.VentRecord record) {
        Direction primary = spillDirection(record);
        if (record.coneStage() >= 6) {
            return new Direction[]{primary, primary.getOpposite()};
        }
        return new Direction[]{primary};
    }

    private static void fillRuptureThroat(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                          int throatBottomY, int throatTopY, int throatRadius,
                                          Set<BlockPos> allowedSources) {
        for (int y = throatBottomY; y <= throatTopY; y++) {
            for (int dx = -throatRadius; dx <= throatRadius; dx++) {
                for (int dz = -throatRadius; dz <= throatRadius; dz++) {
                    if (dx * dx + dz * dz > throatRadius * throatRadius + 1) {
                        continue;
                    }
                    maintainVentLavaSource(level, new BlockPos(record.x() + dx, y, record.z() + dz), allowedSources);
                }
            }
        }
    }

    private static void carveFlankSpillway(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                           int throatRadius, int breachY, Direction outlet, int outletIndex,
                                           Set<BlockPos> allowedSources) {
        BlockPos breachSource = new BlockPos(record.x(), breachY, record.z()).relative(outlet, throatRadius + 1);
        maintainVentLavaSource(level, breachSource, allowedSources);

        int tunnelLength = throatRadius + 2;
        int spillLength = 10 + record.coneStage() * 2 - outletIndex * 2;
        int spillWidth = record.coneStage() >= 8 ? 1 : 0;

        for (int step = 0; step <= tunnelLength; step++) {
            BlockPos tunnel = new BlockPos(record.x(), breachY, record.z()).relative(outlet, step);
            carveSpillwaySegment(level, tunnel, 1, outlet, step == tunnelLength);
        }

        for (int step = 1; step <= spillLength; step++) {
            int drop = 1 + Math.max(0, step - 1) / (outletIndex == 0 ? 2 : 3);
            int extraDrop = record.coneStage() >= 9 ? step / 6 : 0;
            BlockPos channel = breachSource.relative(outlet, step).below(drop + extraDrop);
            int halfWidth = step > spillLength / 3 ? spillWidth : 0;
            carveSpillwaySegment(level, channel, halfWidth, outlet, false);

            if (record.coneStage() >= 10 && step == spillLength / 2) {
                maintainVentLavaSource(level, channel, allowedSources);
            }
        }
    }

    private static void carveSpillwaySegment(ServerLevel level, BlockPos center, int halfWidth,
                                             Direction outlet, boolean preserveFloorLip) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Direction lateralDirection = outlet.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
        for (int lateral = -halfWidth; lateral <= halfWidth; lateral++) {
            for (int depth = -1; depth <= 2; depth++) {
                BlockPos lateralPos = center.relative(lateralDirection, lateral);
                cursor.set(lateralPos.getX(), center.getY() + depth, lateralPos.getZ());
                if (depth == -1) {
                    level.setBlock(cursor, ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState(), 3);
                } else if (preserveFloorLip && depth == 0 && lateral == 0) {
                    continue;
                } else {
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        cursor.set(center.getX(), center.getY() - 1, center.getZ());
        level.setBlock(cursor, ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState(), 3);
    }

    private static void maintainVentLavaSource(ServerLevel level, BlockPos pos, Set<BlockPos> allowedSources) {
        level.setBlock(pos, ModBlocks.VENT_LAVA.get().defaultBlockState(), 3);
        level.scheduleTick(pos, ModFluids.SOURCE_VENT_LAVA.get(), ModFluids.SOURCE_VENT_LAVA.get().getTickDelay(level));
        allowedSources.add(pos.immutable());
    }

    private static void clearObsoleteVentLavaSources(ServerLevel level, ThermalVentSavedData.VentRecord record, Set<BlockPos> allowedSources) {
        int clearRadius = 6 + record.coneStage() * 2;
        int minY = record.y();
        int maxY = ruptureMouthY(record) + 4;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = record.x() - clearRadius; x <= record.x() + clearRadius; x++) {
            for (int z = record.z() - clearRadius; z <= record.z() + clearRadius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(ModBlocks.VENT_LAVA.get())) {
                        continue;
                    }
                    if (state.getValue(LiquidBlock.LEVEL) != 0) {
                        continue;
                    }
                    if (allowedSources.contains(cursor.immutable())) {
                        continue;
                    }
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
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
        long materialSeed = mixHash((((long) record.x()) << 32) ^ record.z());

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > 4.15D) {
                    continue;
                }

                int x = record.x() + dx;
                int z = record.z() + dz;
                int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (topY <= level.getMinBuildHeight()) {
                    continue;
                }

                int targetY = distance <= 2.15D ? basinFloorY : rimY;
                BlockState targetState;
                if (distance <= 1.35D) {
                    targetState = ModBlocks.THERMAL_VENT_POOL.get().defaultBlockState();
                } else if (distance <= 2.45D) {
                    targetState = ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState();
                } else {
                    long localHash = mixHash(materialSeed + (dx * 73428767L) + (dz * 912931L));
                    targetState = (Math.floorMod(localHash, 5L) == 0L
                            ? ModBlocks.HYDROTHERMAL_ROCK
                            : ModBlocks.SULFUR_CRUST).get().defaultBlockState();
                }

                if (topY < targetY) {
                    for (int fillY = topY + 1; fillY < targetY; fillY++) {
                        cursor.set(x, fillY, z);
                        level.setBlock(cursor, ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState(), 3);
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

    private static long ruptureInterval(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                        long worldTime, long salt) {
        long interval = randomInterval(level, record, worldTime, RUPTURE_MIN_INTERVAL, RUPTURE_MAX_INTERVAL, salt);
        long reduction = Math.min(12L * 20L, record.coneStage() * 2L * 20L);
        return Math.max(18L * 20L, interval - reduction);
    }
}
