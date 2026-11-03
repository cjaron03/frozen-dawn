package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.HelionSiteState;
import com.frozendawn.init.ModBlocks;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;

/** Deferred placement of buried ORSA Helion Confinement Test Facilities: one candidate per 128x128-chunk region
 * (about 2,000 blocks), half the regions roll one, so they are about half as common as CFU-7 reactor sites. The
 * hall sits DEPTH blocks under the lowest ground of its footprint. New chunks only; built sites are never revisited. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class HelionSitePlacement {
    public static final int REGION_SIZE = 128, ROLL_PERCENT = 50, MIN_SPAWN_DISTANCE = 1000, DEPTH = 24, RADIUS = HelionSiteBuilder.HALF + 2,
            LANDMARK_CLEARANCE = 64;
    private static final int[][] OFFSETS = {{0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}, {16, 16}, {-16, 16}, {16, -16}, {-16, -16}};
    private static final Set<Long> pending = ConcurrentHashMap.newKeySet();
    private HelionSitePlacement() {}

    @SubscribeEvent public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != ServerLevel.OVERWORLD) return;
        int chunkX = event.getChunk().getPos().x, chunkZ = event.getChunk().getPos().z;
        var site = siteChunk(level.getSeed(), Math.floorDiv(chunkX, REGION_SIZE), Math.floorDiv(chunkZ, REGION_SIZE));
        if (site != null && site[0] == chunkX && site[1] == chunkZ && !HelionSiteState.get(level.getServer()).isEvaluated(chunkX, chunkZ))
            pending.add(pack(chunkX, chunkZ));
    }

    /** Taking a site's Confinement Core loots it, so the Uplink stops revealing it. */
    @SubscribeEvent public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !event.getState().is(ModBlocks.CONFINEMENT_CORE.get())) return;
        var state = HelionSiteState.get(level.getServer());
        for (var hall : state.unlooted()) if (hall.offset(HelionSiteBuilder.CORE).equals(event.getPos())) state.markLooted(hall);
    }

    public static void tickPlacement(ServerLevel overworld) {
        if (overworld.players().isEmpty() || pending.isEmpty()) return;
        var state = HelionSiteState.get(overworld.getServer());
        for (long key : Set.copyOf(pending)) {
            int chunkX = (int) (key >> 32), chunkZ = (int) key, x = (chunkX << 4) + 8, z = (chunkZ << 4) + 8;
            if (state.isEvaluated(chunkX, chunkZ)) { pending.remove(key); continue; }
            if (!areaLoaded(overworld, x, z, RADIUS + 16)) continue;
            pending.remove(key);
            var spawn = overworld.getSharedSpawnPos();
            long dx = x - spawn.getX(), dz = z - spawn.getZ();
            if (dx * dx + dz * dz < (long) MIN_SPAWN_DISTANCE * MIN_SPAWN_DISTANCE) { state.markEvaluated(chunkX, chunkZ); continue; }
            BlockPos hall = null; String reason = null;
            for (var o : OFFSETS) {
                int sx = x + o[0], sz = z + o[1];
                String why = unsuitable(overworld, sx, sz);
                if (why == null) { hall = hallAt(overworld, sx, sz); break; }
                if (reason == null) reason = why;
            }
            // A site revealed by the ORSA Uplink stands at its candidate on any ground deep enough to bury it.
            if (hall == null && com.frozendawn.uplink.UplinkSavedData.get(overworld.getServer()).promised(chunkX, chunkZ) && deepEnough(overworld, x, z))
                hall = hallAt(overworld, x, z);
            if (hall == null) {
                state.markEvaluated(chunkX, chunkZ);
                FrozenDawn.LOGGER.info("Helion site candidate at ({}, {}) rejected: {}", x, z, reason);
                continue;
            }
            build(overworld, hall);
            state.markBuilt(chunkX, chunkZ, hall);
            FrozenDawn.LOGGER.info("Helion site placed at ({}, {}, {})", hall.getX(), hall.getY(), hall.getZ());
        }
    }

    /** Op test hook: bury a site under this column now and record it. */
    public static BlockPos placeNow(ServerLevel level, int x, int z) {
        var hall = hallAt(level, x, z);
        build(level, hall);
        HelionSiteState.get(level.getServer()).markBuilt(x >> 4, z >> 4, hall);
        return hall;
    }

    private static void build(ServerLevel level, BlockPos hall) {
        var shaft = hall.offset(HelionSiteBuilder.SHAFT);
        HelionSiteBuilder.place(level, hall, Math.max(hall.getY() + DEPTH, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, shaft.getX(), shaft.getZ())));
    }
    /** The hall floor centre: DEPTH blocks below the lowest ground over the footprint. */
    private static BlockPos hallAt(ServerLevel level, int x, int z) {
        return new BlockPos(x, lowestGround(level, x, z) - DEPTH, z);
    }
    private static int lowestGround(ServerLevel level, int x, int z) {
        int min = Integer.MAX_VALUE;
        for (int ox = -RADIUS; ox <= RADIUS; ox += RADIUS) for (int oz = -RADIUS; oz <= RADIUS; oz += RADIUS)
            min = Math.min(min, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x + ox, z + oz));
        return min;
    }
    private static boolean deepEnough(ServerLevel level, int x, int z) { return lowestGround(level, x, z) - DEPTH - 2 > level.getMinBuildHeight() + 4; }

    /** The site chunk for a region, or null when the region rolls none. */
    public static int @Nullable [] siteChunk(long seed, int regionX, int regionZ) {
        long h = seed ^ 0x48454C494F4E4346L; // "HELIONCF"
        h = h * 6364136223846793005L + regionX * 1442695040888963407L;
        h = h * 6364136223846793005L + regionZ * 7664345821815920749L;
        h ^= h >>> 16;
        if (Math.floorMod(h >> 32, 100) >= ROLL_PERCENT) return null;
        return new int[] {regionX * REGION_SIZE + 8 + Math.floorMod(h >> 8, REGION_SIZE - 16), regionZ * REGION_SIZE + 8 + Math.floorMod(h >> 20, REGION_SIZE - 16)};
    }
    /** Why this spot cannot hold a site, or null. Any land will do, since the hall is buried; the shaft cannot open
     * into water, and the site keeps clear of surface structures and other ORSA landmarks. */
    public static @Nullable String unsuitable(ServerLevel level, int x, int z) {
        for (int[] o : new int[][] {{0, 0}, {-RADIUS, 0}, {RADIUS, 0}, {0, -RADIUS}, {0, RADIUS}}) {
            var biome = LandmarkBiomeRules.getLandmarkNoiseBiome(level, x + o[0], z + o[1]);
            if (!LandmarkBiomeRules.isToleratedLandmarkFootprintBiome(biome))
                return "biome " + biome.unwrapKey().map(k -> k.location().getPath()).orElse("?");
        }
        if (!deepEnough(level, x, z)) return "too shallow";
        var center = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        var crowded = ReactorSitePlacement.crowded(level, center);
        if (crowded != null) return crowded;
        long near = (long) LANDMARK_CLEARANCE * LANDMARK_CLEARANCE;
        for (var p : HelionSiteState.get(level.getServer()).built()) {
            long dx = p.getX() - x, dz = p.getZ() - z;
            if (dx * dx + dz * dz < near) return "near helion site";
        }
        return null;
    }
    private static boolean areaLoaded(ServerLevel level, int x, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx += 16) for (int dz = -radius; dz <= radius; dz += 16)
            if (!level.isLoaded(new BlockPos(x + dx, 0, z + dz))) return false;
        return level.isLoaded(new BlockPos(x + radius, 0, z + radius));
    }
    private static long pack(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }
    public static void reset() { pending.clear(); }
}
