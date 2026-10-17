package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReactorSiteState;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;

/** Deferred placement of ruined ORSA reactor sites: rare, one candidate per 96x96-chunk region (about 1,500 blocks),
 * same deterministic region pipeline as Monitoring Stations. New chunks only; existing builds are never revisited. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ReactorSitePlacement {
    public static final int REGION_SIZE = 96, ROLL_PERCENT = 60, MIN_SPAWN_DISTANCE = 600, RADIUS = 22, MAX_SLOPE = 8, SEARCH = 32,
            STRUCTURE_PADDING = 32, STRUCTURE_SEARCH = 10, LANDMARK_CLEARANCE = 64;
    /** Spots tried around the candidate, nearest first, when its own ground does not suit a site. */
    private static final int[][] OFFSETS = {{0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}, {16, 16}, {-16, 16}, {16, -16}, {-16, -16},
            {32, 0}, {-32, 0}, {0, 32}, {0, -32}, {32, 32}, {-32, 32}, {32, -32}, {-32, -32}};
    private static final Set<Long> pending = ConcurrentHashMap.newKeySet();
    private ReactorSitePlacement() {}

    @SubscribeEvent public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != ServerLevel.OVERWORLD) return;
        int chunkX = event.getChunk().getPos().x, chunkZ = event.getChunk().getPos().z;
        var site = siteChunk(level.getSeed(), Math.floorDiv(chunkX, REGION_SIZE), Math.floorDiv(chunkZ, REGION_SIZE));
        if (site != null && site[0] == chunkX && site[1] == chunkZ && !ReactorSiteState.get(level.getServer()).isEvaluated(chunkX, chunkZ))
            pending.add(pack(chunkX, chunkZ));
    }

    public static void tickPlacement(ServerLevel overworld) {
        if (overworld.players().isEmpty() || pending.isEmpty()) return;
        var state = ReactorSiteState.get(overworld.getServer());
        for (long key : Set.copyOf(pending)) {
            int chunkX = (int) (key >> 32), chunkZ = (int) key, x = (chunkX << 4) + 8, z = (chunkZ << 4) + 8;
            if (state.isEvaluated(chunkX, chunkZ)) { pending.remove(key); continue; }
            if (!areaLoaded(overworld, x, z, RADIUS + SEARCH)) continue;
            pending.remove(key);
            var spawn = overworld.getSharedSpawnPos();
            long dx = x - spawn.getX(), dz = z - spawn.getZ();
            if (dx * dx + dz * dz < (long) MIN_SPAWN_DISTANCE * MIN_SPAWN_DISTANCE) { state.markEvaluated(chunkX, chunkZ); continue; }
            BlockPos center = null; String reason = null;
            for (var o : OFFSETS) {
                int sx = x + o[0], sz = z + o[1], y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
                String why = y <= overworld.getMinBuildHeight() + 12 ? "no ground" : unsuitable(overworld, sx, sz);
                if (why == null) { center = new BlockPos(sx, y, sz); break; }
                if (reason == null) reason = why;
            }
            // A site promised by an ORSA Uplink station map stands at its candidate even on poor ground.
            if (center == null && com.frozendawn.uplink.UplinkSavedData.get(overworld.getServer()).promised(chunkX, chunkZ)) {
                int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y > overworld.getMinBuildHeight() + 12) center = new BlockPos(x, y, z);
            }
            if (center == null) {
                state.markEvaluated(chunkX, chunkZ);
                FrozenDawn.LOGGER.info("Ruined ORSA reactor site candidate at ({}, {}) rejected: {}", x, z, reason);
                continue;
            }
            ReactorSiteBuilder.place(overworld, center);
            state.markBuilt(chunkX, chunkZ, center);
            FrozenDawn.LOGGER.info("Ruined ORSA reactor site placed at ({}, {}, {})", center.getX(), center.getY(), center.getZ());
        }
    }

    /** Op test hook: build a site here now and record it. */
    public static BlockPos placeNow(ServerLevel level, int x, int z) {
        var center = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        ReactorSiteBuilder.place(level, center);
        ReactorSiteState.get(level.getServer()).markBuilt(x >> 4, z >> 4, center);
        return center;
    }

    /** The site chunk for a region, or null when the region rolls none. */
    public static int @Nullable [] siteChunk(long seed, int regionX, int regionZ) {
        long h = seed ^ 0x4F52534152454143L; // "ORSAREAC"
        h = h * 6364136223846793005L + regionX * 1442695040888963407L;
        h = h * 6364136223846793005L + regionZ * 7664345821815920749L;
        h ^= h >>> 16;
        if (Math.floorMod(h >> 32, 100) >= ROLL_PERCENT) return null;
        return new int[] {regionX * REGION_SIZE + 8 + Math.floorMod(h >> 8, REGION_SIZE - 16), regionZ * REGION_SIZE + 8 + Math.floorMod(h >> 20, REGION_SIZE - 16)};
    }
    public static boolean eligible(ServerLevel level, int x, int z) { return unsuitable(level, x, z) == null; }
    /** Why this spot cannot hold a site, or null. Any land biome will do (ORSA built wherever a colony needed
     * heat); oceans, rivers, swamps and peaks cannot, and the footprint must be within MAX_SLOPE of level. */
    public static @Nullable String unsuitable(ServerLevel level, int x, int z) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int[] o : new int[][] {{0, 0}, {-12, 0}, {12, 0}, {0, -12}, {0, 12}, {-9, -9}, {-9, 9}, {9, -9}, {9, 9}}) {
            var biome = LandmarkBiomeRules.getLandmarkNoiseBiome(level, x + o[0], z + o[1]);
            if (!LandmarkBiomeRules.isToleratedLandmarkFootprintBiome(biome))
                return "biome " + biome.unwrapKey().map(k -> k.location().getPath()).orElse("?");
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + o[0], z + o[1]);
            min = Math.min(min, y); max = Math.max(max, y);
        }
        if (max - min > MAX_SLOPE) return "slope " + (max - min);
        return crowded(level, new BlockPos(x, max, z));
    }
    /** Keeps a site clear of towns, villages and other surface structures (32 blocks past their bounds) and of
     * built ORSA landmarks (64 blocks), so it never grades over someone else's buildings. */
    public static @Nullable String crowded(ServerLevel level, BlockPos center) {
        int clearance = RADIUS + STRUCTURE_PADDING;
        var chunk = new net.minecraft.world.level.ChunkPos(center);
        for (int dx = -STRUCTURE_SEARCH; dx <= STRUCTURE_SEARCH; dx++) for (int dz = -STRUCTURE_SEARCH; dz <= STRUCTURE_SEARCH; dz++)
            for (var start : level.structureManager().startsForStructure(new net.minecraft.world.level.ChunkPos(chunk.x + dx, chunk.z + dz),
                    s -> s.step() == net.minecraft.world.level.levelgen.GenerationStep.Decoration.SURFACE_STRUCTURES)) {
                if (start == null || !start.isValid()) continue;
                var b = start.getBoundingBox();
                if (center.getX() >= b.minX() - clearance && center.getX() <= b.maxX() + clearance
                        && center.getZ() >= b.minZ() - clearance && center.getZ() <= b.maxZ() + clearance)
                    return "near " + level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                            .getKey(start.getStructure()).getPath();
            }
        long near = (long) LANDMARK_CLEARANCE * LANDMARK_CLEARANCE;
        var server = level.getServer();
        for (var p : com.frozendawn.data.MonitoringStationState.get(server).getBuiltStationCenters()) if (flatSq(p, center) < near) return "near monitoring station";
        for (var p : com.frozendawn.data.CargoDropState.get(server).getBuiltDropCenters()) if (flatSq(p, center) < near) return "near cargo drop";
        for (var p : com.frozendawn.data.ReactorSiteState.get(server).built()) if (flatSq(p, center) < near) return "near reactor site";
        var orsa = com.frozendawn.data.OrsaStructureState.get(server);
        int reach = LANDMARK_CLEARANCE / 16 + 1;
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++)
            if (orsa.isCampBuilt(chunk.x + dx, chunk.z + dz)
                    && flatSq(new BlockPos(((chunk.x + dx) << 4) + 8, 0, ((chunk.z + dz) << 4) + 8), center) < near) return "near camp";
        return null;
    }
    private static long flatSq(BlockPos a, BlockPos b) { long dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ(); return dx * dx + dz * dz; }
    private static boolean areaLoaded(ServerLevel level, int x, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx += 16) for (int dz = -radius; dz <= radius; dz += 16)
            if (!level.isLoaded(new BlockPos(x + dx, 0, z + dz))) return false;
        return level.isLoaded(new BlockPos(x + radius, 0, z + radius));
    }
    private static long pack(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }
    public static void reset() { pending.clear(); }
}
