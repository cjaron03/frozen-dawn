package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.MonitoringStationState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deferred placement of civilian Monitoring Stations using the same
 * deterministic region-based landmark pipeline as ORSA field camps.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class MonitoringStationPlacement {

    private static final int REGION_SIZE = 32;
    private static final int MIN_SPAWN_DISTANCE = 300;
    private static final int PLACEMENT_BUFFER = 10;
    private static final int STATION_RADIUS = 12;

    private static final Set<Long> pendingStationPlacements = ConcurrentHashMap.newKeySet();
    private static long cachedWorldSeed = Long.MIN_VALUE;

    private MonitoringStationPlacement() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != ServerLevel.OVERWORLD) {
            return;
        }

        int chunkX = event.getChunk().getPos().x;
        int chunkZ = event.getChunk().getPos().z;
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        long seed = getWorldSeed(level);
        int[] stationChunk = getStationChunkInRegion(seed, regionX, regionZ);
        if (stationChunk == null) {
            return;
        }

        if (chunkX == stationChunk[0] && chunkZ == stationChunk[1]) {
            MonitoringStationState state = MonitoringStationState.get(level.getServer());
            if (!state.isStationEvaluated(chunkX, chunkZ)) {
                pendingStationPlacements.add(packChunkPos(chunkX, chunkZ));
            }
        }
    }

    public static void tickPlacement(ServerLevel overworld) {
        if (overworld.players().isEmpty() || pendingStationPlacements.isEmpty()) {
            return;
        }

        MonitoringStationState state = MonitoringStationState.get(overworld.getServer());

        for (Long key : Set.copyOf(pendingStationPlacements)) {
            int chunkX = unpackChunkX(key);
            int chunkZ = unpackChunkZ(key);

            if (state.isStationEvaluated(chunkX, chunkZ)) {
                pendingStationPlacements.remove(key);
                continue;
            }

            int blockX = (chunkX << 4) + 8;
            int blockZ = (chunkZ << 4) + 8;

            BlockPos spawn = overworld.getSharedSpawnPos();
            double distSq = (blockX - spawn.getX()) * (long) (blockX - spawn.getX())
                    + (blockZ - spawn.getZ()) * (long) (blockZ - spawn.getZ());
            if (distSq < (long) MIN_SPAWN_DISTANCE * MIN_SPAWN_DISTANCE) {
                pendingStationPlacements.remove(key);
                state.markStationEvaluated(chunkX, chunkZ);
                continue;
            }

            if (!isPlacementAreaLoaded(overworld, blockX, blockZ)) {
                continue;
            }

            if (!isEligibleStationSite(overworld, blockX, blockZ)) {
                pendingStationPlacements.remove(key);
                state.markStationEvaluated(chunkX, chunkZ);
                continue;
            }

            int surfaceY = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
            if (surfaceY <= overworld.getMinBuildHeight() + 5) {
                pendingStationPlacements.remove(key);
                state.markStationEvaluated(chunkX, chunkZ);
                continue;
            }

            BlockPos stationCenter = new BlockPos(blockX, surfaceY, blockZ);
            MonitoringStationStructureBuilder.place(overworld, stationCenter);
            state.markStationBuilt(chunkX, chunkZ, stationCenter);
            pendingStationPlacements.remove(key);

            FrozenDawn.LOGGER.info("Monitoring Station placed at ({}, {}, {})",
                    stationCenter.getX(), stationCenter.getY(), stationCenter.getZ());
        }
    }

    @Nullable
    public static BlockPos findNearestBuiltOrCreate(ServerLevel level, BlockPos origin, double minDistSq) {
        MonitoringStationState state = MonitoringStationState.get(level.getServer());
        BlockPos nearestBuilt = null;
        double nearestBuiltDistSq = Double.MAX_VALUE;

        for (BlockPos center : state.getBuiltStationCenters()) {
            double distSq = horizontalDistSq(center.getX(), center.getZ(), origin.getX(), origin.getZ());
            if (distSq > minDistSq && distSq < nearestBuiltDistSq) {
                nearestBuilt = center;
                nearestBuiltDistSq = distSq;
            }
        }
        if (nearestBuilt != null) {
            return nearestBuilt;
        }

        int originRegionX = Math.floorDiv(origin.getX() >> 4, REGION_SIZE);
        int originRegionZ = Math.floorDiv(origin.getZ() >> 4, REGION_SIZE);
        long seed = getWorldSeed(level);

        java.util.List<StationCandidate> candidates = new java.util.ArrayList<>();
        for (int drx = -8; drx <= 8; drx++) {
            for (int drz = -8; drz <= 8; drz++) {
                int regionX = originRegionX + drx;
                int regionZ = originRegionZ + drz;
                int[] pos = getStationBlockPos(seed, regionX, regionZ);
                if (pos == null) {
                    continue;
                }

                double distSq = horizontalDistSq(pos[0], pos[1], origin.getX(), origin.getZ());
                if (distSq <= minDistSq) {
                    continue;
                }

                candidates.add(new StationCandidate(pos[0] >> 4, pos[1] >> 4, pos[0], pos[1], distSq));
            }
        }

        candidates.sort(java.util.Comparator.comparingDouble(StationCandidate::distSq));
        for (StationCandidate candidate : candidates) {
            BlockPos built = ensureStationBuilt(level, candidate.chunkX(), candidate.chunkZ());
            if (built != null) {
                return built;
            }
        }

        return null;
    }

    @Nullable
    private static int[] getStationChunkInRegion(long seed, int regionX, int regionZ) {
        long hash = regionHash(seed, regionX, regionZ);

        // Less common than camps: roughly half of regions roll a station.
        if (Math.floorMod(hash >> 32, 100) > 48) {
            return null;
        }

        int localX = 4 + Math.floorMod(hash >> 8, REGION_SIZE - 8);
        int localZ = 4 + Math.floorMod(hash >> 20, REGION_SIZE - 8);
        return new int[]{
                regionX * REGION_SIZE + localX,
                regionZ * REGION_SIZE + localZ
        };
    }

    public static int[] getStationBlockPos(long seed, int regionX, int regionZ) {
        int[] stationChunk = getStationChunkInRegion(seed, regionX, regionZ);
        if (stationChunk == null) {
            return null;
        }
        return new int[]{(stationChunk[0] << 4) + 8, (stationChunk[1] << 4) + 8};
    }

    public static boolean isEligibleStationSite(ServerLevel level, int cx, int cz) {
        if (!LandmarkBiomeRules.isEligibleLandmarkBiome(level, cx, cz)) {
            return false;
        }

        int[][] footprintSamples = {
                {-11, 0}, {11, 0}, {0, -11}, {0, 11},
                {-8, -8}, {-8, 8}, {8, -8}, {8, 8}
        };
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int[] offset : footprintSamples) {
            int sampleX = cx + offset[0];
            int sampleZ = cz + offset[1];
            if (!LandmarkBiomeRules.isToleratedLandmarkFootprintBiome(level, sampleX, sampleZ)) {
                return false;
            }
            int sampleY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ);
            minY = Math.min(minY, sampleY);
            maxY = Math.max(maxY, sampleY);
        }

        return (maxY - minY) <= 5;
    }

    @Nullable
    public static BlockPos findBuiltStationNear(ServerLevel level, BlockPos origin, int radius) {
        int radiusSq = radius * radius;
        MonitoringStationState state = MonitoringStationState.get(level.getServer());
        for (BlockPos center : state.getBuiltStationCenters()) {
            if (center.distSqr(origin) <= radiusSq) {
                return center;
            }
        }
        return null;
    }

    @Nullable
    public static BlockPos findLockedStationCovering(ServerLevel level, BlockPos pos) {
        MonitoringStationState state = MonitoringStationState.get(level.getServer());
        for (BlockPos center : state.getBuiltStationCenters()) {
            int chunkX = center.getX() >> 4;
            int chunkZ = center.getZ() >> 4;
            if (state.isStationUnlocked(chunkX, chunkZ)) {
                continue;
            }
            if (MonitoringStationStructureBuilder.isProtectedBackRoomBlock(center, pos)) {
                return center;
            }
        }
        return null;
    }

    private static long regionHash(long seed, int regionX, int regionZ) {
        long h = seed ^ 0x4F52534157585354L; // "ORSAWXST"
        h = h * 6364136223846793005L + regionX * 1442695040888963407L;
        h = h * 6364136223846793005L + regionZ * 7664345821815920749L;
        return h ^ (h >>> 16);
    }

    @Nullable
    private static BlockPos ensureStationBuilt(ServerLevel level, int chunkX, int chunkZ) {
        MonitoringStationState state = MonitoringStationState.get(level.getServer());
        BlockPos storedCenter = state.getStationCenter(chunkX, chunkZ);
        if (storedCenter != null) {
            return storedCenter;
        }
        if (state.isStationBuilt(chunkX, chunkZ)) {
            return new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8);
        }
        if (state.isStationEvaluated(chunkX, chunkZ)) {
            return null;
        }

        int blockX = (chunkX << 4) + 8;
        int blockZ = (chunkZ << 4) + 8;

        BlockPos spawn = level.getSharedSpawnPos();
        double distSq = horizontalDistSq(blockX, blockZ, spawn.getX(), spawn.getZ());
        if (distSq < (long) MIN_SPAWN_DISTANCE * MIN_SPAWN_DISTANCE) {
            state.markStationEvaluated(chunkX, chunkZ);
            pendingStationPlacements.remove(packChunkPos(chunkX, chunkZ));
            return null;
        }

        loadPlacementArea(level, blockX, blockZ);

        if (!isEligibleStationSite(level, blockX, blockZ)) {
            state.markStationEvaluated(chunkX, chunkZ);
            pendingStationPlacements.remove(packChunkPos(chunkX, chunkZ));
            return null;
        }

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        if (surfaceY <= level.getMinBuildHeight() + 5) {
            state.markStationEvaluated(chunkX, chunkZ);
            pendingStationPlacements.remove(packChunkPos(chunkX, chunkZ));
            return null;
        }

        BlockPos stationCenter = new BlockPos(blockX, surfaceY, blockZ);
        MonitoringStationStructureBuilder.place(level, stationCenter);
        state.markStationBuilt(chunkX, chunkZ, stationCenter);
        pendingStationPlacements.remove(packChunkPos(chunkX, chunkZ));

        FrozenDawn.LOGGER.info("Monitoring Station force-placed at ({}, {}, {}) for landmark query",
                stationCenter.getX(), stationCenter.getY(), stationCenter.getZ());
        return stationCenter;
    }

    private static void loadPlacementArea(ServerLevel level, int centerX, int centerZ) {
        int radius = STATION_RADIUS + PLACEMENT_BUFFER;
        int minChunkX = Math.floorDiv(centerX - radius, 16);
        int maxChunkX = Math.floorDiv(centerX + radius, 16);
        int minChunkZ = Math.floorDiv(centerZ - radius, 16);
        int maxChunkZ = Math.floorDiv(centerZ + radius, 16);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static double horizontalDistSq(int ax, int az, int bx, int bz) {
        long dx = (long) ax - bx;
        long dz = (long) az - bz;
        return dx * dx + dz * dz;
    }

    private static boolean isPlacementAreaLoaded(ServerLevel level, int centerX, int centerZ) {
        int radius = STATION_RADIUS + PLACEMENT_BUFFER;
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

    private static long getWorldSeed(ServerLevel level) {
        if (cachedWorldSeed == Long.MIN_VALUE) {
            cachedWorldSeed = level.getSeed();
        }
        return cachedWorldSeed;
    }

    private static long packChunkPos(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static int unpackChunkX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackChunkZ(long packed) {
        return (int) packed;
    }

    public static void reset() {
        pendingStationPlacements.clear();
        cachedWorldSeed = Long.MIN_VALUE;
    }

    private record StationCandidate(int chunkX, int chunkZ, int blockX, int blockZ, double distSq) {
    }
}
