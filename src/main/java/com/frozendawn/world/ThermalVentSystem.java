package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.FuelProcessingSiloMultiblock;
import com.frozendawn.block.ThermalVentPoolBlock;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModDamageTypes;
import com.frozendawn.init.ModFluids;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.ThermalVentEruptionPayload;
import com.frozendawn.network.GeothermalCuePayload;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

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
    private static final long RUPTURE_MID_SURGE_DURATION = 8L * 20L;
    private static final long RUPTURE_MATURE_SURGE_DURATION = 12L * 20L;
    private static final int MAX_CONE_STAGE = 12;
    private static final int RUPTURE_LAVA_START_STAGE = 3;
    private static final int PRIMARY_RUPTURE_OVERFLOW_STAGE = 4;
    private static final int SECONDARY_RUPTURE_OVERFLOW_STAGE = 6;
    private static final int RUPTURE_BOMBARDMENT_STAGE = 4;
    private static final int MATURE_RUPTURE_STAGE = 4;
    private static final double ACTIVE_TREMOR_RADIUS = 32.0D;
    private static final double AMBIENT_TREMOR_RADIUS = 24.0D;
    private static final double RUPTURE_IDLE_TREMOR_BASE_RADIUS = 34.0D;
    private static final double RUPTURE_WARNING_TREMOR_BASE_RADIUS = 42.0D;
    private static final double RUPTURE_ERUPTION_QUAKE_BASE_RADIUS = 72.0D;
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
    private static final WeakHashMap<ServerLevel, List<PendingBombardment>> pendingBombardments = new WeakHashMap<>();

    private ThermalVentSystem() {
    }

    public static void tick(ServerLevel level, int phase, float progress, long worldTime) {
        processPendingBombardments(level, phase, progress, worldTime);
        if (level.players().isEmpty()) {
            return;
        }
        ThermalVentRegistry.beginTick(level);

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
        pendingBombardments.clear();
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
        playGeothermalTremor(level, pos, ACTIVE_TREMOR_RADIUS, 0.48f, 0.82f);
        awardWitnessingPlayers(level, pos, 20.0D, "krakatoa");
        spawnActiveBurstParticles(level, pos);
        meltColdTerrain(level, pos, 4, true);
        applyBurstDamage(level, pos, ACTIVE_ERUPTION_RADIUS, 6.0f, 0.2f, 0.4f);
    }

    private static void startRuptureBurst(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                          long worldTime, int phase, float progress) {
        int previousConeStage = record.coneStage();
        int nextConeStage = Math.min(MAX_CONE_STAGE, previousConeStage + 1);
        record.setConeStage(nextConeStage);
        record.setEruptionEndTick(worldTime + RUPTURE_BURST_DURATION);
        record.setNextEventTick(record.eruptionEndTick() + ruptureInterval(level, record, worldTime, 61L));

        BlockPos pos = record.anchorPos();
        if (!PhaseManager.isVacuumActive(phase, progress)) {
            level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.5f, 0.46f);
        }
        boolean reshapeCone = record.shapedConeStage() < nextConeStage;
        boolean maxStageLocked = isFinalRuptureLock(record);
        if (reshapeCone) {
            rebuildRuptureVolcano(level, record, true);
            record.setShapedConeStage(record.coneStage());
        } else {
            if (!maxStageLocked) {
                applyVolcanicField(level, record, true);
            }
        }
        applyRuptureLavaSurge(level, record, worldTime, true);
        BlockPos impulsePos = new BlockPos(record.x(), ruptureMouthY(record), record.z());
        float impulseStrength = 1.70f + nextConeStage * 0.18f + (nextConeStage >= MAX_CONE_STAGE ? 0.35f : 0.0f);
        int impulseDuration = 46 + nextConeStage * 6;
        double impulseRadius = 34.0D + nextConeStage * 3.4D;
        sendEruptionImpulse(level, impulsePos, impulseStrength, impulseDuration, impulseRadius);
        playGeothermalQuake(level, impulsePos,
                Math.max(impulseRadius, RUPTURE_ERUPTION_QUAKE_BASE_RADIUS + nextConeStage * 2.5D),
                0.68f, 0.58f);
        awardWitnessingPlayers(level, impulsePos, impulseRadius, "krakatoa");
        spawnRuptureBurstParticles(level, record);
        meltColdTerrain(level, pos, 6 + nextConeStage, true);
        applyBurstDamage(level, pos, RUPTURE_ERUPTION_RADIUS + nextConeStage / 2,
                14.0f + nextConeStage * 1.8f, 0.50f + nextConeStage * 0.05f, 0.90f + nextConeStage * 0.07f);
        if (reshapeCone) {
            applyRuptureScar(level, record);
        }
        if (nextConeStage >= RUPTURE_BOMBARDMENT_STAGE) {
            queueVolcanicBombardment(level, record, worldTime);
        }
    }

    private static void playRuptureWarning(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                           int phase, float progress) {
        BlockPos impulsePos = new BlockPos(record.x(), ruptureMouthY(record), record.z());
        sendEruptionImpulse(level, impulsePos,
                0.60f + record.coneStage() * 0.06f,
                30 + record.coneStage() * 2,
                28.0D + record.coneStage() * 2.6D);
        playGeothermalTremor(level, impulsePos,
                RUPTURE_WARNING_TREMOR_BASE_RADIUS + record.coneStage() * 3.2D,
                0.42f, 0.74f);
        if (!PhaseManager.isVacuumActive(phase, progress)) {
            level.playSound(null, record.anchorPos(), SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.7f, 0.6f);
        }
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
            if (record.shapedConeStage() < record.coneStage()) {
                rebuildRuptureVolcano(level, record, false);
                record.setShapedConeStage(record.coneStage());
                ThermalVentSavedData.get(level.getServer()).markDirty();
            }
            applyRuptureLavaSurge(level, record, worldTime, false);
            ensureSulfurDeposits(level, record);
        }

        boolean ashHeavyRupture = snapshot.archetype() == ThermalVentArchetype.RUPTURE
                && record.coneStage() >= MATURE_RUPTURE_STAGE;

        if (snapshot.isErupting()) {
            if (snapshot.archetype() == ThermalVentArchetype.RUPTURE) {
                spawnSteamColumn(level, record.anchorPos(),
                        ashHeavyRupture ? 8 : 16,
                        ashHeavyRupture ? 14 : 32,
                        ashHeavyRupture ? 0.34D : 0.48D,
                        ashHeavyRupture ? 0.12D : 0.20D,
                        true,
                        !ashHeavyRupture);
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
                spawnSteamColumn(level, record.anchorPos(),
                        ashHeavyRupture ? 4 : 10,
                        ashHeavyRupture ? 8 : 18,
                        ashHeavyRupture ? 0.20D : 0.28D,
                        ashHeavyRupture ? 0.05D : 0.10D,
                        false,
                        !ashHeavyRupture);
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
                } else if (PhaseManager.isVacuumActive(phase, progress) && worldTime % 160L == 0L) {
                    playGeothermalTremor(level, record.anchorPos(), AMBIENT_TREMOR_RADIUS, 0.26f, 1.08f);
                }
            } else {
                if (worldTime % 8L == 0L) {
                    spawnSteamColumn(level, record.anchorPos(), 7, 12, 0.24D, 0.070D, false, false);
                }
                if (snapshot.archetype() == ThermalVentArchetype.RUPTURE
                        && record.coneStage() >= MATURE_RUPTURE_STAGE
                        && worldTime % 16L == 0L) {
                    spawnRupturePlume(level, record, false);
                }
                spawnMatureRuptureAmbient(level, record, worldTime, false);
                if (!PhaseManager.isVacuumActive(phase, progress) && worldTime % 80L == 0L) {
                    playAmbientBoil(level, record.anchorPos(), 0.65f, 0.95f);
                } else if (PhaseManager.isVacuumActive(phase, progress) && worldTime % 100L == 0L) {
                    playGeothermalTremor(level, new BlockPos(record.x(), ruptureMouthY(record), record.z()),
                            RUPTURE_IDLE_TREMOR_BASE_RADIUS + record.coneStage() * 2.8D, 0.30f, 0.90f);
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
        boolean ashHeavy = coneStage >= MATURE_RUPTURE_STAGE;
        int jetCount = erupting ? 26 + coneStage * 4 : 10 + coneStage * 2;
        int capCount = erupting ? 18 + coneStage * 3 : 7 + coneStage;
        int falloutCount = erupting ? 22 + coneStage * 3 : 8 + coneStage;
        double jetHeight = 10.0D + coneStage * 1.6D;
        double capY = mouthY + jetHeight;
        double jetSpread = 0.22D + coneStage * 0.018D;
        double capSpread = (ashHeavy ? 2.4D : 1.25D) + coneStage * (ashHeavy ? 0.32D : 0.22D);
        double falloutRadius = (ashHeavy ? 4.2D : 3.0D) + coneStage * (ashHeavy ? 0.72D : 0.55D);

        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mouthX, mouthY, mouthZ,
                erupting ? 18 + coneStage * 2 : 8 + coneStage, 0.34D, 0.22D, 0.34D, erupting ? 0.08D : 0.03D);

        if (ashHeavy) {
            level.sendParticles(ParticleTypes.ASH, mouthX, mouthY, mouthZ, jetCount,
                    jetSpread, 0.45D, jetSpread, erupting ? 0.30D : 0.14D);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.SCORCHED_GROUND.get().defaultBlockState()),
                    mouthX, mouthY + jetHeight * 0.35D, mouthZ,
                    Math.max(10, jetCount / 2), jetSpread * 0.9D, 0.55D, jetSpread * 0.9D, erupting ? 0.05D : 0.02D);
            level.sendParticles(ParticleTypes.WHITE_ASH, mouthX, mouthY + jetHeight * 0.58D, mouthZ,
                    Math.max(10, jetCount / 2), jetSpread * 1.1D, 0.70D, jetSpread * 1.1D, erupting ? 0.03D : 0.012D);

            level.sendParticles(ParticleTypes.ASH, mouthX, capY, mouthZ, capCount,
                    capSpread, 0.28D, capSpread, 0.012D);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.SCORCHED_GROUND.get().defaultBlockState()),
                    mouthX, capY + 0.5D, mouthZ, Math.max(8, capCount / 2),
                    capSpread * 1.1D, 0.16D, capSpread * 1.1D, 0.003D);
            level.sendParticles(ParticleTypes.WHITE_ASH, mouthX, capY + 0.9D, mouthZ, Math.max(5, capCount / 4),
                    capSpread * 0.85D, 0.10D, capSpread * 0.85D, 0.002D);

            level.sendParticles(ParticleTypes.ASH, mouthX, capY - 0.8D, mouthZ, falloutCount,
                    falloutRadius, 0.36D, falloutRadius, -0.04D);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.SCORCHED_GROUND.get().defaultBlockState()),
                    mouthX, capY - 1.0D, mouthZ, Math.max(8, falloutCount / 3),
                    falloutRadius * 0.8D, 0.24D, falloutRadius * 0.8D, -0.02D);
            level.sendParticles(ParticleTypes.WHITE_ASH, mouthX, capY - 0.4D, mouthZ, Math.max(6, falloutCount / 4),
                    falloutRadius * 0.75D, 0.18D, falloutRadius * 0.75D, -0.02D);
        } else {
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
    }

    private static void spawnMatureRuptureAmbient(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                                  long worldTime, boolean erupting) {
        if (record.coneStage() < MATURE_RUPTURE_STAGE) {
            return;
        }
        if (worldTime % (erupting ? 2L : 4L) == 0L) {
            spawnCalderaAshVent(level, record, erupting);
        }
        if (worldTime % 8L == 0L) {
            spawnPersistentAshCanopy(level, record, erupting);
        }
        if (worldTime % 10L == 0L) {
            spawnSatelliteFumaroles(level, record, erupting);
        }
        if (worldTime % (erupting ? 4L : 10L) == 0L) {
            depositVolcanicAsh(level, record, erupting);
        }
    }

    private static void spawnCalderaAshVent(ServerLevel level, ThermalVentSavedData.VentRecord record, boolean erupting) {
        if (record.coneStage() < MATURE_RUPTURE_STAGE) {
            return;
        }

        double x = record.x() + 0.5D;
        double y = ruptureLakeSurfaceY(record) + 1.1D;
        double z = record.z() + 0.5D;
        int stage = record.coneStage();
        int smokeCount = erupting ? 10 + stage : 5 + stage / 2;
        int ashCount = erupting ? 12 + stage : 6 + stage / 2;
        double spread = erupting ? 0.55D : 0.34D;
        double fragmentLift = erupting ? 0.008D : 0.003D;

        level.sendParticles(ParticleTypes.ASH, x, y + 0.2D, z, ashCount,
                spread * 0.85D, 0.22D, spread * 0.85D, erupting ? 0.025D : 0.010D);
        level.sendParticles(ParticleTypes.WHITE_ASH, x, y + 0.6D, z, Math.max(4, ashCount / 4),
                spread * 0.55D, 0.10D, spread * 0.55D, erupting ? 0.010D : 0.004D);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.SCORCHED_GROUND.get().defaultBlockState()),
                x, y + 0.35D, z, Math.max(4, smokeCount / 2),
                spread * 0.65D, 0.12D, spread * 0.65D, fragmentLift);
    }

    private static void spawnPersistentAshCanopy(ServerLevel level, ThermalVentSavedData.VentRecord record, boolean erupting) {
        if (record.coneStage() < MATURE_RUPTURE_STAGE) {
            return;
        }
        double x = record.x() + 0.5D;
        double z = record.z() + 0.5D;
        double canopyY = ruptureMouthY(record) + 7.5D + record.coneStage() * 1.0D;
        double canopySpread = 4.2D + record.coneStage() * 0.65D;
        int ashCount = erupting ? 14 + record.coneStage() * 3 : 8 + record.coneStage() * 2;

        level.sendParticles(ParticleTypes.ASH, x, canopyY, z, ashCount,
                canopySpread * 1.15D, 0.26D, canopySpread * 1.15D, erupting ? 0.012D : 0.003D);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.SCORCHED_GROUND.get().defaultBlockState()),
                x, canopyY + 0.4D, z, Math.max(6, ashCount / 2),
                canopySpread * 1.25D, 0.18D, canopySpread * 1.25D, erupting ? 0.006D : 0.001D);
        level.sendParticles(ParticleTypes.ASH, x, canopyY - 0.2D, z, ashCount,
                canopySpread * 1.25D, 0.20D, canopySpread * 1.25D, 0.002D);
        level.sendParticles(ParticleTypes.WHITE_ASH, x, canopyY + 0.8D, z, Math.max(4, ashCount / 4),
                canopySpread * 0.80D, 0.12D, canopySpread * 0.80D, 0.001D);

        double falloutSpread = 6.4D + record.coneStage() * 1.05D;
        level.sendParticles(ParticleTypes.ASH, x, canopyY - 1.0D, z, Math.max(12, ashCount),
                falloutSpread, 0.24D, falloutSpread, -0.026D);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.SCORCHED_GROUND.get().defaultBlockState()),
                x, canopyY - 1.4D, z, Math.max(8, ashCount / 2),
                falloutSpread * 0.85D, 0.18D, falloutSpread * 0.85D, -0.014D);
        level.sendParticles(ParticleTypes.WHITE_ASH, x, canopyY - 0.6D, z, Math.max(4, ashCount / 4),
                falloutSpread * 0.60D, 0.12D, falloutSpread * 0.60D, -0.010D);

        int slopeEmitters = record.coneStage() >= 7 ? 6 : 4;
        double slopeRadius = 4.8D + record.coneStage() * 0.85D;
        for (int i = 0; i < slopeEmitters; i++) {
            double angle = (Mth.TWO_PI * i / slopeEmitters) + (record.x() * 0.037D) + (record.z() * 0.021D);
            double px = x + Math.cos(angle) * slopeRadius;
            double pz = z + Math.sin(angle) * slopeRadius;
            double py = ruptureMouthY(record) + 1.4D + record.coneStage() * 0.35D;
            level.sendParticles(ParticleTypes.ASH, px, py, pz, erupting ? 7 : 4,
                    0.38D, 0.10D, 0.38D, -0.022D);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.SCORCHED_GROUND.get().defaultBlockState()),
                    px, py + 0.3D, pz, erupting ? 3 : 2,
                    0.22D, 0.06D, 0.22D, -0.006D);
        }
    }

    private static void spawnSatelliteFumaroles(ServerLevel level, ThermalVentSavedData.VentRecord record, boolean erupting) {
        boolean lockTerrain = record.coneStage() >= MAX_CONE_STAGE;
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
            if (!lockTerrain && (surface.isAir() || surface.is(BlockTags.REPLACEABLE) || isFragileSurface(surface)
                    || surface.is(Blocks.DIRT) || surface.is(Blocks.GRASS_BLOCK)
                    || surface.is(ModBlocks.FROZEN_DIRT.get()) || surface.is(ModBlocks.FROZEN_SAND.get())
                    || surface.is(Blocks.STONE) || surface.is(Blocks.COBBLESTONE) || surface.is(Blocks.SAND))) {
                level.setBlock(cursor, (i & 1) == 0
                        ? ModBlocks.SULFUR_CRUST.get().defaultBlockState()
                        : ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState(), 3);
            }

            double px = x + 0.5D;
            double py = y + 0.25D;
            double pz = z + 0.5D;
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, px, py, pz,
                    erupting ? 5 : 3, 0.12D, 0.08D, 0.12D, erupting ? 0.08D : 0.04D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, px, py + 0.25D, pz,
                    erupting ? 4 : 2, 0.10D, 0.06D, 0.10D, erupting ? 0.03D : 0.015D);
            level.sendParticles(ParticleTypes.WHITE_ASH, px, py + 0.15D, pz,
                    erupting ? 3 : 2, 0.10D, 0.04D, 0.10D, 0.006D);
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
                if (surfaceState.is(ModBlocks.THERMAL_VENT_POOL.get())
                        || surfaceState.is(Blocks.BEDROCK)
                        || surfaceState.is(ModBlocks.SULFUR_ORE.get())) {
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

    private static void playGeothermalTremor(ServerLevel level, BlockPos pos, double radius, float volume, float pitch) {
        playGeothermalCue(level, pos, radius, ModSounds.GEOTHERMAL_TREMOR.get(), volume, pitch);
    }

    private static void playGeothermalQuake(ServerLevel level, BlockPos pos, double radius, float volume, float pitch) {
        playGeothermalCue(level, pos, radius, ModSounds.GEOTHERMAL_QUAKE.get(), volume, pitch);
    }

    private static void playGeothermalCue(ServerLevel level, BlockPos pos, double radius,
                                          SoundEvent sound, float volume, float pitch) {
        double radiusSqr = radius * radius;
        for (ServerPlayer player : level.players()) {
            double cueX = pos.getX() + 0.5D;
            double cueY = pos.getY() + 0.5D;
            double cueZ = pos.getZ() + 0.5D;
            if (player.distanceToSqr(cueX, cueY, cueZ) <= radiusSqr) {
                PacketDistributor.sendToPlayer(player, new GeothermalCuePayload(
                        BuiltInRegistries.SOUND_EVENT.getKey(sound).toString(),
                        volume,
                        pitch
                ));
            }
        }
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
        boolean ashHeavy = record.coneStage() >= MATURE_RUPTURE_STAGE;
        if (ashHeavy) {
            level.sendParticles(ParticleTypes.ASH, x, y, z, 24, 0.34D, 0.16D, 0.34D, 0.12D);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.SCORCHED_GROUND.get().defaultBlockState()),
                    x, y + 0.4D, z, 28, 0.40D, 0.22D, 0.40D, 0.02D);
        } else {
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y, z, 28, 0.34D, 0.12D, 0.34D, 0.18D);
            level.sendParticles(ParticleTypes.CLOUD, x, y, z, 32, 0.36D, 0.16D, 0.36D, 0.14D);
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 28, 0.52D, 0.24D, 0.52D, 0.05D);
        level.sendParticles(ParticleTypes.SPLASH, x, y, z, 22, 0.42D, 0.16D, 0.42D, 0.10D);
        level.sendParticles(ParticleTypes.ASH, x, y, z, 36, 0.62D, 0.30D, 0.62D, 0.03D);
        level.sendParticles(ParticleTypes.WHITE_ASH, x, y, z, ashHeavy ? 10 : 20, 0.44D, 0.22D, 0.44D, 0.02D);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState()),
                x, y, z, 18, 0.45D, 0.18D, 0.45D, 0.03D);
    }

    private static void applyRuptureScar(ServerLevel level, ThermalVentSavedData.VentRecord record) {
        BlockPos center = record.anchorPos();
        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int innerSkipRadius = ruptureSkirtRadius(record) + (record.coneStage() >= MAX_CONE_STAGE ? 2 : 0);
        int scarRadius = innerSkipRadius + 6 + record.coneStage() / 2;
        int innerSkipSq = innerSkipRadius * innerSkipRadius;
        for (int dx = -scarRadius; dx <= scarRadius; dx++) {
            for (int dz = -scarRadius; dz <= scarRadius; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq <= innerSkipSq || distSq > scarRadius * scarRadius || random.nextFloat() > 0.58f) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                cursor.set(x, y, z);
                BlockState state = level.getBlockState(cursor);
                if (state.is(ModBlocks.THERMAL_VENT_POOL.get())
                        || state.is(Blocks.BEDROCK)
                        || state.is(ModBlocks.SULFUR_ORE.get())) {
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

        int fieldRadius = ruptureVolcanicFieldRadius(coneStage);
        int skirtRadius = ruptureSkirtRadius(record);
        int skirtRadiusSq = skirtRadius * skirtRadius;
        int innerRadius = 4 + coneStage;
        int middleRadius = 7 + coneStage * 2;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -fieldRadius; dx <= fieldRadius; dx++) {
            for (int dz = -fieldRadius; dz <= fieldRadius; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > fieldRadius * fieldRadius) {
                    continue;
                }
                if (distSq <= skirtRadiusSq) {
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
                while (state.is(ModBlocks.VOLCANIC_ASH.get()) && cursor.getY() > level.getMinBuildHeight() + 1) {
                    cursor.move(Direction.DOWN);
                    state = level.getBlockState(cursor);
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

    private static void queueVolcanicBombardment(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                                 long worldTime) {
        int stage = record.coneStage();
        RandomSource random = level.getRandom();
        int impactCount = stage >= SECONDARY_RUPTURE_OVERFLOW_STAGE
                ? 6 + random.nextInt(5)
                : 3 + random.nextInt(3);
        int minDistance = ruptureSkirtRadius(record) + 8;
        int maxDistance = minDistance + 16;
        int launchY = ruptureMouthY(record) + 1;

        for (int i = 0; i < impactCount; i++) {
            BlockPos impactPos = null;
            for (int attempt = 0; attempt < 8; attempt++) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                int distance = minDistance + random.nextInt(maxDistance - minDistance + 1);
                int x = record.x() + Mth.floor(Math.cos(angle) * distance);
                int z = record.z() + Mth.floor(Math.sin(angle) * distance);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (y <= level.getMinBuildHeight()) {
                    continue;
                }
                BlockPos candidate = new BlockPos(x, y, z);
                BlockState surface = level.getBlockState(candidate);
                if (surface.is(ModBlocks.VENT_LAVA.get()) || surface.is(ModBlocks.THERMAL_VENT_POOL.get())) {
                    continue;
                }
                impactPos = candidate;
                break;
            }
            if (impactPos == null) {
                continue;
            }

            long launchTick = worldTime + i * 3L + random.nextInt(3);
            long travelDuration = 16L + stage + random.nextInt(9);
            enqueuePendingBombardment(level, new PendingBombardment(
                    new BlockPos(record.x(), launchY, record.z()),
                    impactPos,
                    launchTick,
                    launchTick + travelDuration,
                    stage
            ));
        }
    }

    private static void enqueuePendingBombardment(ServerLevel level, PendingBombardment bombardment) {
        pendingBombardments.computeIfAbsent(level, ignored -> new ArrayList<>()).add(bombardment);
    }

    private static void processPendingBombardments(ServerLevel level, int phase, float progress, long worldTime) {
        List<PendingBombardment> bombardments = pendingBombardments.get(level);
        if (bombardments == null || bombardments.isEmpty()) {
            return;
        }

        Iterator<PendingBombardment> iterator = bombardments.iterator();
        while (iterator.hasNext()) {
            PendingBombardment bombardment = iterator.next();
            if (worldTime < bombardment.launchTick()) {
                continue;
            }

            spawnBombardmentArc(level, bombardment, worldTime);
            if (worldTime >= bombardment.impactTick()) {
                applyMeteorImpact(level, bombardment.target(), bombardment.stage(), phase, progress);
                iterator.remove();
            }
        }
    }

    private static void spawnBombardmentArc(ServerLevel level, PendingBombardment bombardment, long worldTime) {
        double totalTicks = Math.max(1.0D, bombardment.impactTick() - bombardment.launchTick());
        double progress = Mth.clamp((worldTime - bombardment.launchTick()) / totalTicks, 0.0D, 1.0D);
        double previousProgress = Math.max(0.0D, progress - 0.12D);

        for (int sample = 0; sample < 3; sample++) {
            double t = Mth.lerp(sample / 2.0D, previousProgress, progress);
            double px = Mth.lerp(t, bombardment.origin().getX() + 0.5D, bombardment.target().getX() + 0.5D);
            double pz = Mth.lerp(t, bombardment.origin().getZ() + 0.5D, bombardment.target().getZ() + 0.5D);
            double baseY = Mth.lerp(t, bombardment.origin().getY() + 0.5D, bombardment.target().getY() + 0.5D);
            double py = baseY + Math.sin(t * Math.PI) * (6.0D + bombardment.stage() * 0.65D);

            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState()),
                    px, py, pz, 3, 0.14D, 0.14D, 0.14D, 0.01D);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 3,
                    0.08D, 0.08D, 0.08D, 0.01D);
            level.sendParticles(ParticleTypes.ASH, px, py, pz, 3,
                    0.10D, 0.08D, 0.10D, 0.004D);
            level.sendParticles(ParticleTypes.WHITE_ASH, px, py, pz, 1,
                    0.06D, 0.06D, 0.06D, 0.002D);
        }
    }

    private static void applyMeteorImpact(ServerLevel level, BlockPos center, int stage, int phase, float progress) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.getX() + 0.5D, center.getY() + 0.4D, center.getZ() + 0.5D,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.getX() + 0.5D, center.getY() + 0.4D, center.getZ() + 0.5D,
                28, 0.55D, 0.22D, 0.55D, 0.03D);
        level.sendParticles(ParticleTypes.ASH, center.getX() + 0.5D, center.getY() + 0.4D, center.getZ() + 0.5D,
                32, 0.72D, 0.30D, 0.72D, 0.02D);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.SCORCHED_GROUND.get().defaultBlockState()),
                center.getX() + 0.5D, center.getY() + 0.4D, center.getZ() + 0.5D,
                24, 0.66D, 0.22D, 0.66D, 0.02D);
        if (!PhaseManager.isVacuumActive(phase, progress)) {
            level.playSound(null, center, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0f, 0.55f);
        }

        applyBurstDamage(level, center, 3 + stage / 3, 10.0f + stage * 1.6f, 0.42f, 0.80f);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int impactRadius = 2 + Math.max(0, (stage - RUPTURE_BOMBARDMENT_STAGE) / 2);
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
                if (isFragileSurface(state)
                        || state.is(BlockTags.PLANKS)
                        || state.is(Blocks.COBBLESTONE)
                        || state.is(Blocks.STONE_BRICKS)
                        || state.is(Blocks.STONE)
                        || state.is(Blocks.DIRT)
                        || state.is(Blocks.GRASS_BLOCK)
                        || state.is(Blocks.SAND)
                        || state.is(ModBlocks.FROZEN_DIRT.get())
                        || state.is(ModBlocks.FROZEN_SAND.get())
                        || distSq <= 1) {
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
        depositImpactAsh(level, center, stage);
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
                PacketDistributor.sendToPlayer(player, new ThermalVentEruptionPayload(center, strength, durationTicks, (float) radius));
            }
        }
    }

    private static void awardWitnessingPlayers(ServerLevel level, BlockPos center, double radius, String advancementName) {
        double radiusSqr = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D) <= radiusSqr) {
                grantAdvancement(player, advancementName);
            }
        }
    }

    private static void grantAdvancement(ServerPlayer player, String name) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, name);
        AdvancementHolder holder = server.getAdvancements().get(loc);
        if (holder == null) {
            return;
        }

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (!progress.isDone()) {
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(holder, criterion);
            }
        }
    }

    private static void growRuptureCone(ServerLevel level, ThermalVentSavedData.VentRecord record) {
        int coneStage = record.coneStage();
        if (coneStage <= 0) {
            return;
        }

        int skirtRadius = ruptureSkirtRadius(record);
        int outerRadius = ruptureOuterRadius(record);
        int maxClearY = ruptureRimY(record) + 8 + coneStage / 2;
        int minFoundationY = Math.max(level.getMinBuildHeight() + 1, ruptureGroundY(record) - 5);
        int bodyBaseY = ruptureConeBodyBaseY(level, record);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -skirtRadius; dx <= skirtRadius; dx++) {
            for (int dz = -skirtRadius; dz <= skirtRadius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > skirtRadius + 0.25D) {
                    continue;
                }

                int x = record.x() + dx;
                int z = record.z() + dz;
                int targetTopY = ruptureTargetTopY(record, dx, dz, dist);
                if (targetTopY == Integer.MIN_VALUE) {
                    continue;
                }

                clearRuptureColumn(level, x, z, targetTopY, maxClearY);

                int foundationY = findRuptureFoundationY(level, x, z, targetTopY, minFoundationY);
                int fillStartY = foundationY + 1;
                if (dist <= outerRadius + 0.25D) {
                    fillStartY = Math.min(fillStartY, bodyBaseY);
                }
                fillStartY = Math.max(minFoundationY, fillStartY);

                for (int fillY = fillStartY; fillY <= targetTopY; fillY++) {
                    cursor.set(x, fillY, z);
                    level.setBlock(cursor, ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState(), 3);
                }

                if (targetTopY >= minFoundationY) {
                    cursor.set(x, targetTopY, z);
                    level.setBlock(cursor, ruptureSurfaceState(record, dist, dx, dz), 3);
                }
            }
        }
    }

    private static void rebuildRuptureVolcano(ServerLevel level, ThermalVentSavedData.VentRecord record, boolean eruptionPulse) {
        growRuptureCone(level, record);
        scrubRuptureConeEnvelope(level, record);
        maintainRuptureLava(level, record, false);
        applyVolcanicField(level, record, eruptionPulse);
    }

    private static void ensureSulfurDeposits(ServerLevel level, ThermalVentSavedData.VentRecord record) {
        if (!isFinalRuptureLock(record) || record.sulfurDepositsSeeded()) {
            return;
        }

        seedSulfurPockets(level, record);
        seedSulfurNodes(level, record);
        record.setSulfurDepositsSeeded(true);
        ThermalVentSavedData.get(level.getServer()).markDirty();
    }

    private static void seedSulfurPockets(ServerLevel level, ThermalVentSavedData.VentRecord record) {
        long baseSeed = mixHash((((long) record.x()) << 32) ^ record.z() ^ 0x50504F434B45544CL);
        int calderaRadius = ruptureCalderaRadius(record);
        int lakeRadius = ruptureLakeRadius(record);
        int pocketCount = 6 + Math.max(0, record.coneStage() - MATURE_RUPTURE_STAGE);

        for (int index = 0; index < pocketCount; index++) {
            long hash = mixHash(baseSeed + index * 0x9E3779B97F4A7C15L);
            double angle = ((hash >>> 12) & 0x3FFL) / 1024.0D * Mth.TWO_PI;
            int radialSpan = Math.max(1, calderaRadius - lakeRadius);
            double radius = lakeRadius + 1.0D + Math.floorMod((int) (hash >>> 28), radialSpan + 1);
            int dx = Mth.floor(Math.cos(angle) * radius);
            int dz = Mth.floor(Math.sin(angle) * radius);
            double dist = Math.sqrt(dx * dx + dz * dz);
            int targetY = ruptureTargetTopY(record, dx, dz, dist);
            if (targetY == Integer.MIN_VALUE) {
                continue;
            }

            BlockPos pos = new BlockPos(record.x() + dx, targetY, record.z() + dz);
            replaceWithSulfurOre(level, pos, true);
        }
    }

    private static void seedSulfurNodes(ServerLevel level, ThermalVentSavedData.VentRecord record) {
        long baseSeed = mixHash((((long) record.x()) << 32) ^ record.z() ^ 0x4E4F444553554C46L);
        int calderaRadius = ruptureCalderaRadius(record);
        int lakeRadius = ruptureLakeRadius(record);
        int nodeCount = 9 + record.coneStage() * 2;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int index = 0; index < nodeCount; index++) {
            long hash = mixHash(baseSeed + index * 0x632BE59BD9B4E019L);
            double angle = ((hash >>> 10) & 0x7FFL) / 2048.0D * Mth.TWO_PI;
            int radialSpan = Math.max(1, calderaRadius - lakeRadius + 1);
            double radius = lakeRadius + 1.0D + Math.floorMod((int) (hash >>> 27), radialSpan + 1);
            int dx = Mth.floor(Math.cos(angle) * radius);
            int dz = Mth.floor(Math.sin(angle) * radius);
            double dist = Math.sqrt(dx * dx + dz * dz);
            int surfaceY = ruptureTargetTopY(record, dx, dz, dist);
            if (surfaceY == Integer.MIN_VALUE) {
                continue;
            }

            int centerY = Math.max(ruptureLakeBottomY(record) + 1, surfaceY - 1 - Math.floorMod((int) (hash >>> 41), 3));
            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        if (ox == 0 && oy == 0 && oz == 0) {
                            cursor.set(record.x() + dx, centerY, record.z() + dz);
                            replaceWithSulfurOre(level, cursor, false);
                            continue;
                        }
                        long localHash = mixHash(hash + ox * 73428767L + oy * 19459691L + oz * 912931L);
                        if ((localHash & 3L) != 0L) {
                            continue;
                        }
                        cursor.set(record.x() + dx + ox, centerY + oy, record.z() + dz + oz);
                        replaceWithSulfurOre(level, cursor, false);
                    }
                }
            }
        }
    }

    private static void replaceWithSulfurOre(ServerLevel level, BlockPos pos, boolean exposedPocket) {
        BlockState state = level.getBlockState(pos);
        if (!isSulfurDepositHost(state, exposedPocket)) {
            return;
        }
        level.setBlock(pos, ModBlocks.SULFUR_ORE.get().defaultBlockState(), 3);
    }

    private static boolean isSulfurDepositHost(BlockState state, boolean exposedPocket) {
        if (state.isAir()
                || state.is(Blocks.BEDROCK)
                || state.is(ModBlocks.THERMAL_VENT_POOL.get())
                || state.is(ModBlocks.VENT_LAVA.get())) {
            return false;
        }
        if (state.is(ModBlocks.SULFUR_ORE.get())) {
            return false;
        }
        if (state.is(ModBlocks.HYDROTHERMAL_ROCK.get())
                || state.is(ModBlocks.SULFUR_CRUST.get())
                || state.is(ModBlocks.SCORCHED_GROUND.get())
                || state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(ModBlocks.FROZEN_COBBLESTONE.get())
                || state.is(ModBlocks.FROZEN_STONE_BRICKS.get())
                || state.is(ModBlocks.FROZEN_DIRT.get())
                || state.is(ModBlocks.FROZEN_SAND.get())) {
            return true;
        }
        return exposedPocket && state.is(Blocks.DIRT);
    }

    private static boolean isFinalRuptureLock(ThermalVentSavedData.VentRecord record) {
        return record.coneStage() >= MAX_CONE_STAGE;
    }

    private static void maintainRuptureLava(ServerLevel level, ThermalVentSavedData.VentRecord record, boolean minimalMaintenance) {
        if (record.coneStage() < RUPTURE_LAVA_START_STAGE) {
            return;
        }

        Set<BlockPos> allowedSources = new HashSet<>();
        int lakeBottomY = ruptureLakeBottomY(record);
        int lakeRadius = ruptureLakeRadius(record);
        int lakeSurfaceY = ruptureLakeSurfaceY(record);
        int maintainedLakeRadius = minimalMaintenance ? Math.min(1, lakeRadius) : lakeRadius;
        for (int dx = -lakeRadius; dx <= lakeRadius; dx++) {
            for (int dz = -lakeRadius; dz <= lakeRadius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > lakeRadius + 0.25D || dist > maintainedLakeRadius + 0.25D) {
                    continue;
                }
                int x = record.x() + dx;
                int z = record.z() + dz;
                for (int y = lakeBottomY; y <= lakeSurfaceY; y++) {
                    maintainVentLavaSource(level, new BlockPos(x, y, z), allowedSources);
                }
            }
        }

        Direction[] outlets = eruptionOutlets(record);
        for (int outletIndex = 0; outletIndex < outlets.length; outletIndex++) {
            Direction outlet = outlets[outletIndex];
            maintainRuptureFlankSource(level, record, outlet, outletIndex, allowedSources);
        }

        if (!minimalMaintenance) {
            clearObsoleteVentLavaSources(level, record, allowedSources);
        }
    }

    private static void applyRuptureLavaSurge(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                              long worldTime, boolean eruptionStart) {
        if (record.coneStage() < RUPTURE_LAVA_START_STAGE
                || record.coneStage() >= MAX_CONE_STAGE
                || !isRuptureSurgeActive(record, worldTime)) {
            return;
        }
        if (!eruptionStart && worldTime % 4L != 0L) {
            return;
        }

        Set<BlockPos> allowedSources = new HashSet<>();
        seedRuptureLakeCore(level, record, allowedSources);
        Direction[] outlets = eruptionOutlets(record);
        for (int outletIndex = 0; outletIndex < outlets.length; outletIndex++) {
            Direction outlet = outlets[outletIndex];
            BlockPos mouth = ruptureFlankSource(record, outlet, outletIndex);
            maintainVentLavaSource(level, mouth, allowedSources);

            BlockPos exterior = mouth.relative(outlet);
            BlockState exteriorState = level.getBlockState(exterior);
            if (exteriorState.isAir() || exteriorState.is(ModBlocks.VENT_LAVA.get())) {
                maintainVentLavaSource(level, exterior, allowedSources);
            }
        }
    }

    private static void seedRuptureLakeCore(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                            Set<BlockPos> allowedSources) {
        int coreRadius = record.coneStage() >= SECONDARY_RUPTURE_OVERFLOW_STAGE ? 2 : 1;
        int surfaceY = ruptureLakeSurfaceY(record);
        int floorY = Math.max(ruptureLakeBottomY(record), surfaceY - 1);
        for (int dx = -coreRadius; dx <= coreRadius; dx++) {
            for (int dz = -coreRadius; dz <= coreRadius; dz++) {
                if (dx * dx + dz * dz > coreRadius * coreRadius) {
                    continue;
                }
                int x = record.x() + dx;
                int z = record.z() + dz;
                for (int y = floorY; y <= surfaceY; y++) {
                    maintainVentLavaSource(level, new BlockPos(x, y, z), allowedSources);
                }
            }
        }
    }

    private static boolean isRuptureSurgeActive(ThermalVentSavedData.VentRecord record, long worldTime) {
        if (record.eruptionEndTick() < 0L) {
            return false;
        }
        return worldTime <= record.eruptionEndTick() + ruptureSurgeDuration(record.coneStage());
    }

    private static long ruptureSurgeDuration(int coneStage) {
        return coneStage >= SECONDARY_RUPTURE_OVERFLOW_STAGE
                ? RUPTURE_MATURE_SURGE_DURATION
                : RUPTURE_MID_SURGE_DURATION;
    }

    private static int ruptureGroundY(ThermalVentSavedData.VentRecord record) {
        return record.y() + 1;
    }

    private static int ruptureRimY(ThermalVentSavedData.VentRecord record) {
        return ruptureGroundY(record) + 6 + Mth.floor(record.coneStage() * 1.75F);
    }

    private static int ruptureMouthY(ThermalVentSavedData.VentRecord record) {
        return ruptureRimY(record);
    }

    private static int ruptureMouthRadius(ThermalVentSavedData.VentRecord record) {
        return ruptureCalderaRadius(record);
    }

    private static int ruptureCalderaRadius(ThermalVentSavedData.VentRecord record) {
        return 4 + record.coneStage() / 3;
    }

    private static int ruptureLakeRadius(ThermalVentSavedData.VentRecord record) {
        return Math.max(2, ruptureCalderaRadius(record) - 2 - record.coneStage() / 8);
    }

    private static int ruptureOuterRadius(ThermalVentSavedData.VentRecord record) {
        return 8 + record.coneStage() + record.coneStage() / 2;
    }

    private static int ruptureSkirtRadius(ThermalVentSavedData.VentRecord record) {
        return ruptureOuterRadius(record) + 3 + record.coneStage() / 4;
    }

    private static int ruptureLakeSurfaceY(ThermalVentSavedData.VentRecord record) {
        return ruptureRimY(record) - 3 - record.coneStage() / 5;
    }

    private static int ruptureLakeBottomY(ThermalVentSavedData.VentRecord record) {
        return ruptureLakeSurfaceY(record) - 2 - record.coneStage() / 4;
    }

    private static Direction spillDirection(ThermalVentSavedData.VentRecord record) {
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        int index = Math.floorMod(record.x() * 31 + record.z() * 17, directions.length);
        return directions[index];
    }

    private static Direction[] eruptionOutlets(ThermalVentSavedData.VentRecord record) {
        if (record.coneStage() < PRIMARY_RUPTURE_OVERFLOW_STAGE) {
            return new Direction[0];
        }
        Direction primary = spillDirection(record);
        if (record.coneStage() >= SECONDARY_RUPTURE_OVERFLOW_STAGE) {
            return new Direction[]{primary, primary.getOpposite()};
        }
        return new Direction[]{primary};
    }

    private static int ruptureTargetTopY(ThermalVentSavedData.VentRecord record, int dx, int dz, double dist) {
        int groundY = ruptureGroundY(record);
        int rimY = ruptureRimY(record);
        int lakeBottomY = ruptureLakeBottomY(record);
        int lakeRadius = ruptureLakeRadius(record);
        int calderaRadius = ruptureCalderaRadius(record);
        int outerRadius = ruptureOuterRadius(record);
        int skirtRadius = ruptureSkirtRadius(record);

        int targetTopY;
        if (dist <= lakeRadius + 0.25D) {
            targetTopY = lakeBottomY - 1;
        } else if (dist <= calderaRadius + 0.25D) {
            double t = Mth.clamp((dist - lakeRadius) / Math.max(1.0D, calderaRadius - lakeRadius), 0.0D, 1.0D);
            double eased = t * t * (3.0D - 2.0D * t);
            targetTopY = Mth.floor(Mth.lerp(eased, lakeBottomY - 1, rimY));
        } else if (dist <= outerRadius + 0.25D) {
            double t = Mth.clamp((dist - calderaRadius) / Math.max(1.0D, outerRadius - calderaRadius), 0.0D, 1.0D);
            double eased = Math.pow(1.0D - t, 1.18D);
            targetTopY = groundY + Mth.floor((rimY - groundY) * eased);
        } else if (dist <= skirtRadius + 0.25D) {
            double t = Mth.clamp((dist - outerRadius) / Math.max(1.0D, skirtRadius - outerRadius), 0.0D, 1.0D);
            double skirtLift = (2.5D + record.coneStage() * 0.12D) * Math.pow(1.0D - t, 1.75D);
            targetTopY = groundY + Mth.floor(skirtLift);
        } else {
            return Integer.MIN_VALUE;
        }

        return applyBreachMouthOpening(record, dx, dz, targetTopY);
    }

    private static int applyBreachMouthOpening(ThermalVentSavedData.VentRecord record, int dx, int dz, int targetTopY) {
        Direction[] outlets = eruptionOutlets(record);
        if (outlets.length == 0) {
            return targetTopY;
        }

        double calderaRadius = ruptureCalderaRadius(record);
        int breachCeilingBase = ruptureLakeSurfaceY(record) - 1;

        for (int outletIndex = 0; outletIndex < outlets.length; outletIndex++) {
            Direction outlet = outlets[outletIndex];
            int dirX = outlet.getStepX();
            int dirZ = outlet.getStepZ();
            int perpX = -dirZ;
            int perpZ = dirX;
            double along = dx * dirX + dz * dirZ;
            double across = Math.abs(dx * perpX + dz * perpZ);
            double openingStart = calderaRadius - 0.35D;
            double openingEnd = calderaRadius + 2.4D + outletIndex * 0.65D;
            double openingWidth = 0.85D + outletIndex * 0.25D;
            if (along < openingStart || along > openingEnd || across > openingWidth) {
                continue;
            }

            targetTopY = Math.min(targetTopY, breachCeilingBase - outletIndex);
        }

        return targetTopY;
    }

    private static BlockState ruptureSurfaceState(ThermalVentSavedData.VentRecord record, double dist, int dx, int dz) {
        int lakeRadius = ruptureLakeRadius(record);
        int calderaRadius = ruptureCalderaRadius(record);
        int outerRadius = ruptureOuterRadius(record);
        int skirtRadius = ruptureSkirtRadius(record);
        long mix = mixHash((((long) record.x()) << 32) ^ record.z() ^ (dx * 73428767L) ^ (dz * 912931L));

        if (dist <= lakeRadius + 0.45D) {
            return ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState();
        }
        if (dist <= calderaRadius + 0.75D) {
            return (mix & 1L) == 0L
                    ? ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState()
                    : ModBlocks.SCORCHED_GROUND.get().defaultBlockState();
        }
        if (dist <= outerRadius + 0.25D) {
            if (dist >= outerRadius - 1.5D) {
                return (mix & 3L) == 0L
                        ? ModBlocks.SULFUR_CRUST.get().defaultBlockState()
                        : ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState();
            }
            return (mix & 7L) <= 4L
                    ? ModBlocks.SCORCHED_GROUND.get().defaultBlockState()
                    : ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState();
        }
        if (dist <= skirtRadius + 0.25D) {
            return (mix & 1L) == 0L
                    ? ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState()
                    : ModBlocks.SULFUR_CRUST.get().defaultBlockState();
        }
        return ModBlocks.HYDROTHERMAL_ROCK.get().defaultBlockState();
    }

    private static void clearRuptureColumn(ServerLevel level, int x, int z, int targetTopY, int maxClearY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = maxClearY; y > targetTopY; y--) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (state.isAir() || state.is(Blocks.BEDROCK)) {
                continue;
            }
            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static int findRuptureFoundationY(ServerLevel level, int x, int z, int targetTopY, int minFoundationY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = targetTopY; y >= minFoundationY; y--) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (isRuptureFoundationState(state)) {
                return y;
            }
        }
        return minFoundationY - 1;
    }

    private static boolean isRuptureFoundationState(BlockState state) {
        return !state.isAir()
                && !state.is(BlockTags.REPLACEABLE)
                && !state.is(ModBlocks.THERMAL_VENT_POOL.get())
                && !state.is(ModBlocks.VENT_LAVA.get());
    }

    private static BlockPos ruptureFlankSource(ThermalVentSavedData.VentRecord record, Direction outlet, int outletIndex) {
        int breachDistance = Math.min(
                ruptureOuterRadius(record) - 2,
                ruptureCalderaRadius(record) + 3 + outletIndex
        );
        int dx = outlet.getStepX() * breachDistance;
        int dz = outlet.getStepZ() * breachDistance;
        int topY = ruptureTargetTopY(record, dx, dz, Math.sqrt(dx * dx + dz * dz));
        int y = Math.min(ruptureLakeSurfaceY(record) - outletIndex, topY + 1);
        return new BlockPos(record.x() + dx, y, record.z() + dz);
    }

    private static void maintainRuptureFlankSource(ServerLevel level, ThermalVentSavedData.VentRecord record,
                                                   Direction outlet, int outletIndex, Set<BlockPos> allowedSources) {
        BlockPos source = ruptureFlankSource(record, outlet, outletIndex);
        BlockPos exterior = source.relative(outlet);
        BlockPos drop = exterior.below();
        clearRuptureBreachCell(level, source.above());
        clearRuptureBreachCell(level, exterior);
        clearRuptureBreachCell(level, exterior.above());
        clearRuptureBreachCell(level, drop);
        maintainVentLavaSource(level, source, allowedSources);
        level.scheduleTick(source, ModFluids.SOURCE_VENT_LAVA.get(), 1);
    }

    private static void clearRuptureBreachCell(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.BEDROCK) && !state.is(ModBlocks.VENT_LAVA.get())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void maintainVentLavaSource(ServerLevel level, BlockPos pos, Set<BlockPos> allowedSources) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.VENT_LAVA.get()) || state.getValue(LiquidBlock.LEVEL) != 0) {
            level.setBlock(pos, ModBlocks.VENT_LAVA.get().defaultBlockState(), 3);
        }
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

    private static int ruptureConeBodyBaseY(ServerLevel level, ThermalVentSavedData.VentRecord record) {
        int targetBase = ruptureGroundY(record) - 2 - record.coneStage() / 3;
        return Math.max(level.getMinBuildHeight() + 1, targetBase);
    }

    private static void depositVolcanicAsh(ServerLevel level, ThermalVentSavedData.VentRecord record, boolean erupting) {
        if (record.coneStage() < MATURE_RUPTURE_STAGE) {
            return;
        }

        RandomSource random = level.getRandom();
        int attempts = erupting ? 18 + record.coneStage() : 8 + record.coneStage() / 2;
        int minRadius = Math.max(ruptureOuterRadius(record) - 2, ruptureCalderaRadius(record) + 4);
        int maxRadius = ruptureVolcanicFieldRadius(record.coneStage()) + 4;
        int maxLayers = erupting ? 6 : 4;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            int distance = random.nextIntBetweenInclusive(minRadius, maxRadius);
            int x = record.x() + Mth.floor(Math.cos(angle) * distance);
            int z = record.z() + Mth.floor(Math.sin(angle) * distance);
            int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (topY <= level.getMinBuildHeight()) {
                continue;
            }

            cursor.set(x, topY, z);
            BlockState topState = level.getBlockState(cursor);
            while (topState.is(ModBlocks.VOLCANIC_ASH.get()) && cursor.getY() > level.getMinBuildHeight() + 1) {
                cursor.move(Direction.DOWN);
                topState = level.getBlockState(cursor);
            }

            double dist = Math.sqrt((x - record.x()) * (double) (x - record.x()) + (z - record.z()) * (double) (z - record.z()));
            if (dist < ruptureOuterRadius(record) - 2.0D) {
                continue;
            }
            if (topState.is(ModBlocks.VENT_LAVA.get()) || topState.is(ModBlocks.THERMAL_VENT_POOL.get())) {
                continue;
            }
            if (!SurfaceColumnScanner.canSupportSnow(level, cursor, topState)) {
                continue;
            }

            BlockPos depositPos = cursor.above();
            BlockState existing = level.getBlockState(depositPos);
            if (!level.canSeeSky(depositPos)) {
                continue;
            }
            if (FuelProcessingSiloMultiblock.isProtectedFromEnvironmentalDeposit(level, depositPos)) {
                continue;
            }
            if (existing.is(ModBlocks.VENT_LAVA.get()) || existing.is(ModBlocks.THERMAL_VENT_POOL.get())) {
                continue;
            }
            if (existing.is(ModBlocks.VOLCANIC_ASH.get())) {
                int layers = existing.getValue(SnowLayerBlock.LAYERS);
                if (layers < maxLayers) {
                    level.setBlock(depositPos, existing.setValue(SnowLayerBlock.LAYERS, layers + 1), 3);
                }
                continue;
            }
            if (!existing.isAir() && !existing.is(BlockTags.REPLACEABLE)) {
                continue;
            }

            level.setBlock(depositPos, ModBlocks.VOLCANIC_ASH.get().defaultBlockState(), 3);
        }
    }

    private static void depositImpactAsh(ServerLevel level, BlockPos center, int stage) {
        int radius = 2 + stage / 4;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        RandomSource random = level.getRandom();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius || random.nextFloat() > 0.55F) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (topY <= level.getMinBuildHeight()) {
                    continue;
                }
                cursor.set(x, topY, z);
                BlockState topState = level.getBlockState(cursor);
                if (!SurfaceColumnScanner.canSupportSnow(level, cursor, topState)
                        || topState.is(ModBlocks.VENT_LAVA.get())
                        || topState.is(ModBlocks.THERMAL_VENT_POOL.get())) {
                    continue;
                }
                BlockPos placePos = cursor.above();
                BlockState placeState = level.getBlockState(placePos);
                if (FuelProcessingSiloMultiblock.isProtectedFromEnvironmentalDeposit(level, placePos)) {
                    continue;
                }
                if (placeState.is(ModBlocks.VOLCANIC_ASH.get())) {
                    int layers = Math.min(6, placeState.getValue(SnowLayerBlock.LAYERS) + 1);
                    level.setBlock(placePos, placeState.setValue(SnowLayerBlock.LAYERS, layers), 3);
                } else if (placeState.isAir() || placeState.is(BlockTags.REPLACEABLE)) {
                    level.setBlock(placePos, ModBlocks.VOLCANIC_ASH.get().defaultBlockState(), 3);
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

    private static void scrubRuptureConeEnvelope(ServerLevel level, ThermalVentSavedData.VentRecord record) {
        int skirtRadius = ruptureSkirtRadius(record);
        int maxClearY = ruptureRimY(record) + 3;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -skirtRadius; dx <= skirtRadius; dx++) {
            for (int dz = -skirtRadius; dz <= skirtRadius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > skirtRadius + 0.25D) {
                    continue;
                }

                int targetTopY = ruptureTargetTopY(record, dx, dz, dist);
                if (targetTopY == Integer.MIN_VALUE) {
                    continue;
                }

                int x = record.x() + dx;
                int z = record.z() + dz;
                cursor.set(x, targetTopY, z);
                BlockState currentTop = level.getBlockState(cursor);
                if (currentTop.is(Blocks.BEDROCK)
                        || currentTop.is(ModBlocks.VENT_LAVA.get())
                        || currentTop.is(ModBlocks.SULFUR_ORE.get())) {
                    continue;
                }

                if (currentTop.is(ModBlocks.FROZEN_DIRT.get())
                        || currentTop.is(ModBlocks.FROZEN_SAND.get())
                        || currentTop.is(ModBlocks.FROZEN_COBBLESTONE.get())
                        || currentTop.is(ModBlocks.FROZEN_STONE_BRICKS.get())
                        || currentTop.is(Blocks.SNOW)
                        || currentTop.is(Blocks.SNOW_BLOCK)
                        || currentTop.is(Blocks.POWDER_SNOW)
                        || currentTop.is(ModBlocks.FROZEN_ATMOSPHERE.get())
                        || currentTop.is(Blocks.ICE)
                        || currentTop.is(Blocks.PACKED_ICE)
                        || currentTop.is(Blocks.BLUE_ICE)) {
                    level.setBlock(cursor, ruptureSurfaceState(record, dist, dx, dz), 3);
                }

                for (int y = targetTopY + 1; y <= maxClearY; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(ModBlocks.FROZEN_ATMOSPHERE.get())
                            || state.is(ModBlocks.ACHERONITE_CRYSTAL.get())
                            || state.is(Blocks.SNOW)
                            || state.is(Blocks.SNOW_BLOCK)
                            || state.is(Blocks.POWDER_SNOW)
                            || state.is(Blocks.ICE)
                            || state.is(Blocks.PACKED_ICE)
                            || state.is(Blocks.BLUE_ICE)) {
                        level.destroyBlock(cursor, false);
                    }
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

    private static int ruptureVolcanicFieldRadius(int coneStage) {
        return 17 + coneStage + coneStage / 2 + coneStage / 4 + coneStage / 3;
    }

    private record PendingBombardment(BlockPos origin, BlockPos target, long launchTick, long impactTick, int stage) {
    }
}
