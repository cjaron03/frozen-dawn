package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.OrsaStructureState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scattered placement of ORSA field camps at village-like density.
 * <p>
 * Uses a grid-based deterministic approach: the world is divided into
 * 24x24-chunk regions. Each region gets at most one camp, placed at a
 * position deterministic from the world seed and region coordinates.
 * This ensures roughly even spacing (~384 blocks between camps) and
 * deterministic results per seed.
 * <p>
 * Camps are placed when their chunk loads, similar to how towers and
 * the blast pit are placed via deferred construction.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class CampPlacement {

    /** Region size in chunks. Each region gets 0 or 1 camp. */
    private static final int REGION_SIZE = 24;
    /** Minimum distance from world spawn (in blocks) before camps can appear. */
    private static final int MIN_SPAWN_DISTANCE = 200;
    /** Buffer around camp for loaded-area check (blocks). */
    private static final int PLACEMENT_BUFFER = 8;
    /** Camp footprint radius (blocks). */
    private static final int CAMP_RADIUS = 6;

    private static final Set<Long> pendingCampPlacements = ConcurrentHashMap.newKeySet();
    private static long cachedWorldSeed = Long.MIN_VALUE;

    private CampPlacement() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != ServerLevel.OVERWORLD) {
            return;
        }

        int chunkX = event.getChunk().getPos().x;
        int chunkZ = event.getChunk().getPos().z;

        // Determine which region this chunk belongs to
        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        // Check if this region's camp chunk matches the loaded chunk
        long seed = getWorldSeed(level);
        int[] campChunk = getCampChunkInRegion(seed, regionX, regionZ);
        if (campChunk == null) {
            return;
        }

        if (chunkX == campChunk[0] && chunkZ == campChunk[1]) {
            OrsaStructureState state = OrsaStructureState.get(level.getServer());
            if (!state.isCampEvaluated(chunkX, chunkZ)) {
                long key = packChunkPos(chunkX, chunkZ);
                pendingCampPlacements.add(key);
            }
        }
    }

    public static void tickPlacement(ServerLevel overworld) {
        if (overworld.players().isEmpty() || pendingCampPlacements.isEmpty()) {
            return;
        }

        OrsaStructureState state = OrsaStructureState.get(overworld.getServer());

        for (Long key : Set.copyOf(pendingCampPlacements)) {
            int chunkX = unpackChunkX(key);
            int chunkZ = unpackChunkZ(key);

            if (state.isCampEvaluated(chunkX, chunkZ)) {
                pendingCampPlacements.remove(key);
                continue;
            }

            int blockX = (chunkX << 4) + 8;
            int blockZ = (chunkZ << 4) + 8;

            // Enforce minimum spawn distance
            BlockPos spawn = overworld.getSharedSpawnPos();
            double distSq = (blockX - spawn.getX()) * (long) (blockX - spawn.getX())
                    + (blockZ - spawn.getZ()) * (long) (blockZ - spawn.getZ());
            if (distSq < (long) MIN_SPAWN_DISTANCE * MIN_SPAWN_DISTANCE) {
                pendingCampPlacements.remove(key);
                state.markCampEvaluated(chunkX, chunkZ); // prevent re-evaluation
                continue;
            }

            if (!isPlacementAreaLoaded(overworld, blockX, blockZ)) {
                continue;
            }

            // Biome validation: center must be allowed, footprint must not be ocean/river/swamp/peaks
            if (!isEligibleCampSite(overworld, blockX, blockZ)) {
                pendingCampPlacements.remove(key);
                state.markCampEvaluated(chunkX, chunkZ);
                continue;
            }

            // Resolve surface Y
            int surfaceY = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    blockX, blockZ);
            if (surfaceY <= overworld.getMinBuildHeight() + 5) {
                pendingCampPlacements.remove(key);
                state.markCampEvaluated(chunkX, chunkZ);
                continue;
            }

            // Place the camp
            BlockPos campCenter = new BlockPos(blockX, surfaceY, blockZ);
            CampStructureBuilder.place(overworld, campCenter);
            state.markCampBuilt(chunkX, chunkZ);
            pendingCampPlacements.remove(key);

            FrozenDawn.LOGGER.info("ORSA Field Camp placed at ({}, {}, {})",
                    campCenter.getX(), campCenter.getY(), campCenter.getZ());
        }
    }

    /**
     * Determines the camp chunk position within a region using a seeded hash.
     * Returns null if this region should not contain a camp (keeps density natural).
     */
    private static int[] getCampChunkInRegion(long seed, int regionX, int regionZ) {
        long hash = regionHash(seed, regionX, regionZ);

        // ~85% of regions get a camp (village-like density)
        if (Math.floorMod(hash >> 32, 100) > 85) {
            return null;
        }

        // Position within the region (offset from edges to avoid chunk boundary issues)
        int localX = 2 + Math.floorMod(hash >> 8, REGION_SIZE - 4);
        int localZ = 2 + Math.floorMod(hash >> 20, REGION_SIZE - 4);

        return new int[]{
                regionX * REGION_SIZE + localX,
                regionZ * REGION_SIZE + localZ
        };
    }

    private static long regionHash(long seed, int regionX, int regionZ) {
        long h = seed ^ 0x4F525341_43414D50L; // "ORSACAMP"
        h = h * 6364136223846793005L + regionX * 1442695040888963407L;
        h = h * 6364136223846793005L + regionZ * 7664345821815920749L;
        return h ^ (h >>> 16);
    }

    private static boolean isPlacementAreaLoaded(ServerLevel level, int centerX, int centerZ) {
        int radius = CAMP_RADIUS + PLACEMENT_BUFFER;
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

    /**
     * Returns the block coordinates [x, z] of the camp in the given region,
     * or null if the region has no camp (hash roll).
     */
    public static int[] getCampBlockPos(long seed, int regionX, int regionZ) {
        long hash = regionHash(seed, regionX, regionZ);
        if (Math.floorMod(hash >> 32, 100) > 85) {
            return null;
        }
        int localX = 2 + Math.floorMod(hash >> 8, REGION_SIZE - 4);
        int localZ = 2 + Math.floorMod(hash >> 20, REGION_SIZE - 4);
        int chunkX = regionX * REGION_SIZE + localX;
        int chunkZ = regionZ * REGION_SIZE + localZ;
        return new int[]{(chunkX << 4) + 8, (chunkZ << 4) + 8};
    }

    /**
     * Uses the same worldgen noise biome check as towers and blast pit.
     * Checks center + footprint cardinals against the hard-fail biome list
     * (oceans, rivers, swamps, peaks).
     */
    public static boolean isEligibleCampSite(ServerLevel level, int cx, int cz) {
        // Center must be in an allowed landmark biome
        if (!LandmarkBiomeRules.isEligibleLandmarkBiome(level, cx, cz)) {
            return false;
        }

        // Footprint samples must not be in hard-fail biomes (ocean, river, swamp, peaks)
        int[][] footprintSamples = {{-6, 0}, {6, 0}, {0, -6}, {0, 6}, {-4, -4}, {4, 4}};
        for (int[] offset : footprintSamples) {
            if (!LandmarkBiomeRules.isToleratedLandmarkFootprintBiome(level, cx + offset[0], cz + offset[1])) {
                return false;
            }
        }

        return true;
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
        pendingCampPlacements.clear();
        cachedWorldSeed = Long.MIN_VALUE;
    }
}
