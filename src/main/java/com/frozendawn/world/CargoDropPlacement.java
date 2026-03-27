package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.CargoDropState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class CargoDropPlacement {

    private static final int REGION_SIZE = 28;
    private static final int MIN_SPAWN_DISTANCE = 250;
    private static final int PLACEMENT_BUFFER = 12;
    private static final int DROP_RADIUS = 16;
    private static final int DISPLAY_OFFSET = 3;

    private static final Set<Long> pendingCargoDropPlacements = ConcurrentHashMap.newKeySet();
    private static long cachedWorldSeed = Long.MIN_VALUE;

    private CargoDropPlacement() {
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
        int[] cargoChunk = getCargoDropChunkInRegion(seed, regionX, regionZ);
        if (cargoChunk == null) {
            return;
        }

        if (chunkX == cargoChunk[0] && chunkZ == cargoChunk[1]) {
            CargoDropState state = CargoDropState.get(level.getServer());
            if (!state.isCargoDropEvaluated(chunkX, chunkZ)) {
                pendingCargoDropPlacements.add(packChunkPos(chunkX, chunkZ));
            }
        }
    }

    public static void tickPlacement(ServerLevel overworld) {
        if (overworld.players().isEmpty() || pendingCargoDropPlacements.isEmpty()) {
            return;
        }

        CargoDropState state = CargoDropState.get(overworld.getServer());

        for (Long key : Set.copyOf(pendingCargoDropPlacements)) {
            int chunkX = unpackChunkX(key);
            int chunkZ = unpackChunkZ(key);
            int blockX = (chunkX << 4) + 8;
            int blockZ = (chunkZ << 4) + 8;

            if (state.isCargoDropEvaluated(chunkX, chunkZ)) {
                pendingCargoDropPlacements.remove(key);
                continue;
            }

            if (!isOutsideSpawnBuffer(overworld, blockX, blockZ)) {
                pendingCargoDropPlacements.remove(key);
                state.markCargoDropEvaluated(chunkX, chunkZ);
                continue;
            }

            if (!isPlacementAreaLoaded(overworld, blockX, blockZ)) {
                continue;
            }

            if (!isEligibleCargoDropSite(overworld, blockX, blockZ)) {
                pendingCargoDropPlacements.remove(key);
                state.markCargoDropEvaluated(chunkX, chunkZ);
                continue;
            }

            int surfaceY = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
            if (surfaceY <= overworld.getMinBuildHeight() + 5) {
                pendingCargoDropPlacements.remove(key);
                state.markCargoDropEvaluated(chunkX, chunkZ);
                continue;
            }

            BlockPos cargoCenter = new BlockPos(blockX, surfaceY, blockZ);
            CargoDropStructureBuilder.place(overworld, cargoCenter);
            state.markCargoDropBuilt(chunkX, chunkZ, getCargoDropDisplayPos(overworld.getSeed(), cargoCenter));
            pendingCargoDropPlacements.remove(key);

            FrozenDawn.LOGGER.info("Cargo Drop placed at ({}, {}, {})",
                    cargoCenter.getX(), cargoCenter.getY(), cargoCenter.getZ());
        }
    }

    public static boolean isOutsideSpawnBuffer(ServerLevel level, int blockX, int blockZ) {
        BlockPos spawn = level.getSharedSpawnPos();
        double distSq = horizontalDistSq(blockX, blockZ, spawn.getX(), spawn.getZ());
        return distSq >= (long) MIN_SPAWN_DISTANCE * MIN_SPAWN_DISTANCE;
    }

    @Nullable
    private static int[] getCargoDropChunkInRegion(long seed, int regionX, int regionZ) {
        long hash = regionHash(seed, regionX, regionZ);
        if (Math.floorMod(hash >> 32, 100) >= 55) {
            return null;
        }

        int localX = 3 + Math.floorMod(hash >> 8, REGION_SIZE - 6);
        int localZ = 3 + Math.floorMod(hash >> 20, REGION_SIZE - 6);
        return new int[] {
                regionX * REGION_SIZE + localX,
                regionZ * REGION_SIZE + localZ
        };
    }

    public static int[] getCargoDropBlockPos(long seed, int regionX, int regionZ) {
        int[] cargoChunk = getCargoDropChunkInRegion(seed, regionX, regionZ);
        if (cargoChunk == null) {
            return null;
        }
        return new int[] {(cargoChunk[0] << 4) + 8, (cargoChunk[1] << 4) + 8};
    }

    public static BlockPos getCargoDropDisplayPos(long seed, BlockPos anchor) {
        Direction facing = getCargoFacing(seed, anchor.getX(), anchor.getZ());
        return anchor.relative(facing, -DISPLAY_OFFSET);
    }

    public static boolean isEligibleCargoDropSite(ServerLevel level, int cx, int cz) {
        if (!LandmarkBiomeRules.isEligibleLandmarkBiome(level, cx, cz)) {
            return false;
        }

        int[][] footprintSamples = {
                {-14, 0}, {14, 0}, {0, -14}, {0, 14},
                {-10, -10}, {-10, 10}, {10, -10}, {10, 10},
                {-6, 12}, {6, -12}
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

        return (maxY - minY) <= 7;
    }

    private static long regionHash(long seed, int regionX, int regionZ) {
        long h = seed ^ 0x4F5253414352474FL; // "ORSACRGO"
        h = h * 6364136223846793005L + regionX * 1442695040888963407L;
        h = h * 6364136223846793005L + regionZ * 7664345821815920749L;
        return h ^ (h >>> 16);
    }

    private static Direction getCargoFacing(long seed, int x, int z) {
        long hash = cargoHash(seed, x, z);
        return switch (Math.floorMod((int) (hash >> 8), 4)) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            default -> Direction.WEST;
        };
    }

    private static boolean isPlacementAreaLoaded(ServerLevel level, int centerX, int centerZ) {
        int radius = DROP_RADIUS + PLACEMENT_BUFFER;
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

    private static long cargoHash(long seed, int x, int z) {
        long h = seed ^ 0x434152474F44524FL; // "CARGODRO"
        h = h * 6364136223846793005L + x * 1442695040888963407L;
        h = h * 6364136223846793005L + z * 7664345821815920749L;
        return h ^ (h >>> 23);
    }

    private static double horizontalDistSq(int x1, int z1, int x2, int z2) {
        long dx = x1 - (long) x2;
        long dz = z1 - (long) z2;
        return dx * dx + dz * dz;
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
        pendingCargoDropPlacements.clear();
        cachedWorldSeed = Long.MIN_VALUE;
    }
}
