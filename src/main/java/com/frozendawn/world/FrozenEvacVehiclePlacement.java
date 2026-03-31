package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.CampSatelliteState;
import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class FrozenEvacVehiclePlacement {

    private static final int CAMP_REGION_SIZE = 24;
    private static final int VEHICLE_MIN_DISTANCE = 30;
    private static final int VEHICLE_MAX_DISTANCE = 80;
    private static final int VEHICLE_RADIUS = 5;
    private static final int PLACEMENT_BUFFER = 6;
    private static final int MAX_HEIGHT_VARIATION = 6;
    private static final int LOOSE_MAX_HEIGHT_VARIATION = 8;
    private static final int FORCED_MAX_HEIGHT_VARIATION = 12;
    private static final int SEARCH_ANGLE_STEPS = 32;
    private static final int ROLL_PERCENT = 50;
    private static final int FALLBACK_SEARCH_ANGLE_STEPS = 64;
    private static final int FALLBACK_MAX_DISTANCE = 112;

    private static final Set<Long> pendingVehiclePlacements = ConcurrentHashMap.newKeySet();

    private FrozenEvacVehiclePlacement() {
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

        maybeBackfillCampSatellite(level, chunkX, chunkZ);

        CampSatelliteState satelliteState = CampSatelliteState.get(level.getServer());
        Long campKey = satelliteState.getCampKeyForVehicleChunk(chunkX, chunkZ);
        if (campKey != null && !satelliteState.isVehicleBuilt(unpackChunkX(campKey), unpackChunkZ(campKey))) {
            pendingVehiclePlacements.add(campKey);
        }
    }

    public static boolean ensureCampSatellite(ServerLevel level, BlockPos campCenter) {
        int campChunkX = campCenter.getX() >> 4;
        int campChunkZ = campCenter.getZ() >> 4;
        CampSatelliteState state = CampSatelliteState.get(level.getServer());
        if (!state.hasDecision(campChunkX, campChunkZ)) {
            VehiclePlan plan = createVehiclePlan(level, campCenter);
            if (plan == null) {
                state.markNoVehicle(campChunkX, campChunkZ, campCenter);
            } else {
                state.markVehiclePlanned(campChunkX, campChunkZ, campCenter, plan.center(), plan.variant().ordinal());
            }
        }

        if (state.hasLinkedVehicle(campChunkX, campChunkZ) && state.getVehicleCenter(campChunkX, campChunkZ) != null) {
            long campKey = packChunkPos(campChunkX, campChunkZ);
            BlockPos vehicleCenter = state.getVehicleCenter(campChunkX, campChunkZ);
            if (vehicleCenter != null && isPlacementAreaLoaded(level, vehicleCenter.getX(), vehicleCenter.getZ())) {
                pendingVehiclePlacements.add(campKey);
            }
        }

        return state.hasLinkedVehicle(campChunkX, campChunkZ);
    }

    public static boolean hasLinkedVehicle(ServerLevel level, BlockPos campCenter) {
        int campChunkX = campCenter.getX() >> 4;
        int campChunkZ = campCenter.getZ() >> 4;
        CampSatelliteState state = CampSatelliteState.get(level.getServer());
        if (state.hasDecision(campChunkX, campChunkZ)) {
            return state.hasLinkedVehicle(campChunkX, campChunkZ);
        }
        return createVehiclePlan(level, campCenter) != null;
    }

    public static void tickPlacement(ServerLevel overworld) {
        if (overworld.players().isEmpty() || pendingVehiclePlacements.isEmpty()) {
            return;
        }

        CampSatelliteState state = CampSatelliteState.get(overworld.getServer());

        for (Long campKey : Set.copyOf(pendingVehiclePlacements)) {
            int campChunkX = unpackChunkX(campKey);
            int campChunkZ = unpackChunkZ(campKey);

            if (!state.hasLinkedVehicle(campChunkX, campChunkZ) || state.isVehicleBuilt(campChunkX, campChunkZ)) {
                pendingVehiclePlacements.remove(campKey);
                continue;
            }

            BlockPos campCenter = state.getCampCenter(campChunkX, campChunkZ);
            BlockPos vehicleCenter = state.getVehicleCenter(campChunkX, campChunkZ);
            int variantId = state.getVehicleVariant(campChunkX, campChunkZ);
            if (campCenter == null || vehicleCenter == null || variantId < 0) {
                pendingVehiclePlacements.remove(campKey);
                continue;
            }

            if (!isPlacementAreaLoaded(overworld, vehicleCenter.getX(), vehicleCenter.getZ())) {
                continue;
            }

            FrozenEvacVehicleStructureBuilder.place(
                    overworld,
                    campCenter,
                    vehicleCenter,
                    VehicleVariant.fromId(variantId)
            );
            state.markVehicleBuilt(campChunkX, campChunkZ);
            pendingVehiclePlacements.remove(campKey);

            FrozenDawn.LOGGER.info("Frozen Evac Vehicle placed at ({}, {}, {}) for camp ({}, {})",
                    vehicleCenter.getX(), vehicleCenter.getY(), vehicleCenter.getZ(),
                    campCenter.getX(), campCenter.getZ());
        }
    }

    public static void reset() {
        pendingVehiclePlacements.clear();
    }

    @Nullable
    public static VehiclePlan createVehiclePlan(ServerLevel level, BlockPos campCenter) {
        long seed = vehicleHash(level.getSeed(), campCenter.getX(), campCenter.getZ());
        if (Math.floorMod((int) (seed >>> 32), 100) >= ROLL_PERCENT) {
            return null;
        }

        VehicleVariant variant = VehicleVariant.fromId(Math.floorMod((int) (seed >>> 12), VehicleVariant.values().length));
        double baseAngle = ((seed >>> 20) & 0xFFFFL) / 65535.0D * (Math.PI * 2.0D);

        Candidate best = null;
        Candidate looseBest = null;
        Candidate forcedBest = null;
        for (int angleIndex = 0; angleIndex < SEARCH_ANGLE_STEPS; angleIndex++) {
            double angle = baseAngle + angleIndex * ((Math.PI * 2.0D) / SEARCH_ANGLE_STEPS);
            for (int distance = VEHICLE_MIN_DISTANCE; distance <= VEHICLE_MAX_DISTANCE; distance += 4) {
                int x = campCenter.getX() + Mth.floor(Math.cos(angle) * distance);
                int z = campCenter.getZ() + Mth.floor(Math.sin(angle) * distance);
                Candidate candidate = evaluateCandidate(level, campCenter, x, z, distance, seed, angleIndex);
                if (candidate != null) {
                    if (best == null || candidate.score() < best.score()) {
                        best = candidate;
                    }
                    continue;
                }

                Candidate looseCandidate = evaluateLooseCandidate(level, campCenter, x, z, distance, seed, angleIndex);
                if (looseCandidate != null && (looseBest == null || looseCandidate.score() < looseBest.score())) {
                    looseBest = looseCandidate;
                }

                Candidate forcedCandidate = evaluateForcedCandidate(level, campCenter, x, z, distance, seed, angleIndex);
                if (forcedCandidate != null && (forcedBest == null || forcedCandidate.score() < forcedBest.score())) {
                    forcedBest = forcedCandidate;
                }
            }
        }

        Candidate chosen = best != null ? best : (looseBest != null ? looseBest : forcedBest);
        if (chosen == null) {
            Candidate dryFallback = findDryFallback(level, campCenter, baseAngle, seed);
            if (dryFallback != null) {
                return new VehiclePlan(dryFallback.center(), variant);
            }
            return null;
        }
        return new VehiclePlan(chosen.center(), variant);
    }

    @Nullable
    private static Candidate evaluateCandidate(ServerLevel level, BlockPos campCenter, int x, int z,
                                               int distance, long seed, int angleIndex) {
        if (!LandmarkBiomeRules.isEligibleLandmarkBiome(level, x, z)) {
            return null;
        }

        int sampleY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (sampleY <= level.getMinBuildHeight() + 3) {
            return null;
        }

        Direction facing = directionToward(new BlockPos(x, sampleY, z), campCenter);
        int[][] samples = {
                {0, 0}, {0, 3}, {0, -3}, {2, 0}, {-2, 0}, {2, 3}, {-2, 3}, {1, -3}, {-1, -3}
        };

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int surfaceBias = 0;
        for (int[] sample : samples) {
            BlockPos worldPos = toWorld(new BlockPos(x, sampleY, z), facing, sample[0], 0, sample[1]);
            if (!LandmarkBiomeRules.isToleratedLandmarkFootprintBiome(level, worldPos.getX(), worldPos.getZ())) {
                return null;
            }
            int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldPos.getX(), worldPos.getZ());
            if (isWetFootprint(level, worldPos.getX(), topY, worldPos.getZ())) {
                return null;
            }
            minY = Math.min(minY, topY);
            maxY = Math.max(maxY, topY);
            surfaceBias += surfaceBias(level.getBlockState(new BlockPos(worldPos.getX(), topY - 1, worldPos.getZ())));
        }

        int heightVariation = maxY - minY;
        if (heightVariation > MAX_HEIGHT_VARIATION) {
            return null;
        }

        int placementY = placementY(sampleY, minY, maxY);

        int score = heightVariation * 20;
        score += Math.abs(distance - 52) * 2;
        score += surfaceBias;
        score += Math.abs(placementY - sampleY) * 3;
        score += Math.floorMod((int) (seed + angleIndex * 17L + x * 7L - z * 13L), 9);
        return new Candidate(new BlockPos(x, placementY, z), score);
    }

    @Nullable
    private static Candidate evaluateLooseCandidate(ServerLevel level, BlockPos campCenter, int x, int z,
                                                    int distance, long seed, int angleIndex) {
        if (!LandmarkBiomeRules.isEligibleLandmarkBiome(level, x, z)) {
            return null;
        }

        int sampleY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (sampleY <= level.getMinBuildHeight() + 3) {
            return null;
        }

        Direction facing = directionToward(new BlockPos(x, sampleY, z), campCenter);
        int[][] samples = {
                {0, 0}, {0, 2}, {0, -2}, {1, 0}, {-1, 0}
        };

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int surfaceBias = 0;
        for (int[] sample : samples) {
            BlockPos worldPos = toWorld(new BlockPos(x, sampleY, z), facing, sample[0], 0, sample[1]);
            if (!LandmarkBiomeRules.isToleratedLandmarkFootprintBiome(level, worldPos.getX(), worldPos.getZ())) {
                return null;
            }
            int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldPos.getX(), worldPos.getZ());
            if (isWetFootprint(level, worldPos.getX(), topY, worldPos.getZ())) {
                return null;
            }
            minY = Math.min(minY, topY);
            maxY = Math.max(maxY, topY);
            surfaceBias += surfaceBias(level.getBlockState(new BlockPos(worldPos.getX(), topY - 1, worldPos.getZ())));
        }

        int heightVariation = maxY - minY;
        if (heightVariation > LOOSE_MAX_HEIGHT_VARIATION) {
            return null;
        }

        int placementY = placementY(sampleY, minY, maxY);

        int score = 100 + heightVariation * 18;
        score += Math.abs(distance - 50);
        score += surfaceBias;
        score += Math.abs(placementY - sampleY) * 3;
        score += Math.floorMod((int) (seed + angleIndex * 13L + x * 5L - z * 11L), 11);
        return new Candidate(new BlockPos(x, placementY, z), score);
    }

    @Nullable
    private static Candidate evaluateForcedCandidate(ServerLevel level, BlockPos campCenter, int x, int z,
                                                     int distance, long seed, int angleIndex) {
        if (!LandmarkBiomeRules.isToleratedLandmarkFootprintBiome(level, x, z)) {
            return null;
        }

        int sampleY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (sampleY <= level.getMinBuildHeight() + 3) {
            return null;
        }

        BlockPos below = new BlockPos(x, sampleY - 1, z);
        BlockState belowState = level.getBlockState(below);
        if (belowState.liquid() || belowState.isAir()) {
            return null;
        }

        Direction facing = directionToward(new BlockPos(x, sampleY, z), campCenter);
        int[][] samples = {
                {0, 0}, {0, 2}, {0, -2}, {2, 0}, {-2, 0}
        };

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int surfaceBias = 0;
        for (int[] sample : samples) {
            BlockPos worldPos = toWorld(new BlockPos(x, sampleY, z), facing, sample[0], 0, sample[1]);
            int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldPos.getX(), worldPos.getZ());
            if (isWetFootprint(level, worldPos.getX(), topY, worldPos.getZ())) {
                return null;
            }
            minY = Math.min(minY, topY);
            maxY = Math.max(maxY, topY);
            surfaceBias += surfaceBias(level.getBlockState(new BlockPos(worldPos.getX(), topY - 1, worldPos.getZ())));
        }

        int heightVariation = maxY - minY;
        if (heightVariation > FORCED_MAX_HEIGHT_VARIATION) {
            return null;
        }

        int placementY = placementY(sampleY, minY, maxY);

        int score = 200 + heightVariation * 12;
        score += Math.abs(distance - 48);
        score += surfaceBias;
        score += Math.abs(placementY - campCenter.getY());
        score += Math.floorMod((int) (seed + angleIndex * 7L + x * 3L - z * 5L), 13);
        return new Candidate(new BlockPos(x, placementY, z), score);
    }

    @Nullable
    private static Candidate findDryFallback(ServerLevel level, BlockPos campCenter, double baseAngle, long seed) {
        Candidate best = null;
        for (int angleIndex = 0; angleIndex < FALLBACK_SEARCH_ANGLE_STEPS; angleIndex++) {
            double angle = baseAngle + angleIndex * ((Math.PI * 2.0D) / FALLBACK_SEARCH_ANGLE_STEPS);
            for (int distance = VEHICLE_MIN_DISTANCE; distance <= FALLBACK_MAX_DISTANCE; distance += 2) {
                int x = campCenter.getX() + Mth.floor(Math.cos(angle) * distance);
                int z = campCenter.getZ() + Mth.floor(Math.sin(angle) * distance);
                int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (topY <= level.getMinBuildHeight() + 3 || isWetFootprint(level, x, topY, z)) {
                    continue;
                }

                int score = Math.abs(distance - 48) * 3
                        + Math.abs(topY - campCenter.getY())
                        + Math.floorMod((int) (seed + angleIndex * 19L + x * 3L - z * 7L), 17);
                Candidate candidate = new Candidate(new BlockPos(x, topY, z), score);
                if (best == null || candidate.score() < best.score()) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static int placementY(int sampleY, int minY, int maxY) {
        return Mth.clamp((minY + maxY + 1) / 2, minY, maxY);
    }

    private static boolean isWetFootprint(ServerLevel level, int x, int topY, int z) {
        if (topY <= level.getMinBuildHeight() + 1) {
            return true;
        }

        BlockPos below = new BlockPos(x, topY - 1, z);
        BlockPos at = new BlockPos(x, topY, z);
        if (!level.getFluidState(below).isEmpty() || !level.getFluidState(at).isEmpty()) {
            return true;
        }
        BlockState belowState = level.getBlockState(below);
        BlockState atState = level.getBlockState(at);
        return belowState.liquid() || atState.liquid()
                || belowState.is(Blocks.WATER) || atState.is(Blocks.WATER)
                || belowState.is(Blocks.KELP) || belowState.is(Blocks.KELP_PLANT)
                || belowState.is(Blocks.SEAGRASS) || belowState.is(Blocks.TALL_SEAGRASS);
    }

    private static int surfaceBias(BlockState state) {
        if (state.is(Blocks.GRAVEL) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PACKED_MUD) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.SNOW_BLOCK)) {
            return 0;
        }
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.PODZOL)) {
            return 2;
        }
        if (state.is(Blocks.STONE) || state.is(Blocks.ANDESITE) || state.is(Blocks.COBBLESTONE)) {
            return 4;
        }
        return 7;
    }

    private static boolean isPlacementAreaLoaded(ServerLevel level, int centerX, int centerZ) {
        int radius = VEHICLE_RADIUS + PLACEMENT_BUFFER;
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

    private static void maybeBackfillCampSatellite(ServerLevel level, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, CAMP_REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, CAMP_REGION_SIZE);
        int[] campPos = CampPlacement.getCampBlockPos(level.getSeed(), regionX, regionZ);
        if (campPos == null) {
            return;
        }

        int campChunkX = campPos[0] >> 4;
        int campChunkZ = campPos[1] >> 4;
        if (chunkX != campChunkX || chunkZ != campChunkZ) {
            return;
        }

        OrsaStructureState orsaState = OrsaStructureState.get(level.getServer());
        if (!orsaState.isCampBuilt(campChunkX, campChunkZ)) {
            return;
        }

        CampSatelliteState satelliteState = CampSatelliteState.get(level.getServer());
        if (satelliteState.hasDecision(campChunkX, campChunkZ)) {
            return;
        }

        BlockPos campCenter = resolveCampCenter(level, campPos[0], campPos[1]);
        boolean hasVehicle = ensureCampSatellite(level, campCenter);
        CampStructureBuilder.syncTransferDocument(level, campCenter, hasVehicle);
    }

    public static BlockPos resolveCampCenter(ServerLevel level, int campX, int campZ) {
        int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, campX, campZ);
        for (int y = topY + 2; y >= level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(campX, y, campZ);
            if (level.getBlockState(pos).is(ModBlocks.CAMP_RADIO.get())) {
                return pos.below();
            }
        }
        return new BlockPos(campX, Math.max(level.getMinBuildHeight() + 1, topY - 1), campZ);
    }

    private static Direction directionToward(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static BlockPos toWorld(BlockPos origin, Direction facing, int right, int up, int forward) {
        Direction rightDir = facing.getClockWise();
        int dx = rightDir.getStepX() * right + facing.getStepX() * forward;
        int dz = rightDir.getStepZ() * right + facing.getStepZ() * forward;
        return origin.offset(dx, up, dz);
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

    private static long vehicleHash(long seed, int x, int z) {
        long h = seed ^ 0x455641435645484CL; // "EVACVEHL"
        h = h * 6364136223846793005L + x * 1442695040888963407L;
        h = h * 6364136223846793005L + z * 7664345821815920749L;
        return h ^ (h >>> 21);
    }

    public enum VehicleVariant {
        ABANDONED_EMPTY,
        ROADSIDE_BREAKDOWN,
        FAILED_TRANSFER;

        public static VehicleVariant fromId(int id) {
            VehicleVariant[] values = values();
            return values[Math.floorMod(id, values.length)];
        }
    }

    public record VehiclePlan(BlockPos center, VehicleVariant variant) {
    }

    private record Candidate(BlockPos center, int score) {
    }
}
