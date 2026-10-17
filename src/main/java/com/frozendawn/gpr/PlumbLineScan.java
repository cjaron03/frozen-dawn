package com.frozendawn.gpr;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.CargoDropState;
import com.frozendawn.data.MonitoringStationState;
import com.frozendawn.data.ReactorSiteState;
import com.frozendawn.gpr.PlumbLinePolicy.Medium;
import com.frozendawn.gpr.PlumbLinePolicy.Setting;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.world.TemperatureManager;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * One pulse: rays from the scanner march outward through the ground, each spending range by the material it crosses.
 * Water stops a ray, metal reflects it (a bright return), ice and frozen ground carry it further. Rays spread out with
 * distance, so far targets are sampled coarsely: that, plus padding, is the blur. Runs in bounded slices on the server
 * thread and only reads chunks whose data is already present; it never loads or generates one.
 */
public final class PlumbLineScan {
    public enum Kind { ORE, VOID, METAL, STRUCTURE }
    /** A return as sent to clients: an axis-aligned box, its label, and whether triangulation made it exact. */
    public record Return(Kind kind, String label, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean precise) { }

    private static final double STEP = 0.75;
    private final ServerLevel level;
    private final BlockPos origin;
    private final Setting setting;
    private final int rays, maxSteps;
    private final double ox, oy, oz;
    private final float[] budget;
    private final short[] step;
    private final float[] rock;
    private final boolean[] done;
    private int cursor, remaining;
    private final int day, totalDays;
    private final Map<Integer, Boolean> frozenByY = new HashMap<>();
    /** Ore cells hit, with the nearest distance and rock crossed to reach them. */
    private final Map<Long, double[]> oreCells = new HashMap<>();
    private final Map<Long, String> oreTypes = new HashMap<>();
    private final Set<Long> voidCells = new HashSet<>(), metalCells = new HashSet<>();
    private final Map<Long, String> metalTypes = new HashMap<>();
    private long cachedChunk = Long.MIN_VALUE;
    private LevelChunk chunk;

    public PlumbLineScan(ServerLevel level, BlockPos origin, Setting setting) {
        this.level = level; this.origin = origin.immutable(); this.setting = setting; this.rays = setting.rays;
        this.ox = origin.getX() + .5; this.oy = origin.getY() + .5; this.oz = origin.getZ() + .5;
        // Ice and frozen ground can carry a ray past the nominal radius; nothing reaches beyond twice it.
        this.maxSteps = (int) Math.ceil(setting.radius * 2 / STEP);
        budget = new float[rays]; step = new short[rays]; rock = new float[rays]; done = new boolean[rays];
        Arrays.fill(budget, setting.radius);
        remaining = rays;
        var a = ApocalypseState.get(level.getServer());
        day = a.getCurrentDay(); totalDays = a.getTotalDays();
    }

    public boolean finished() { return remaining == 0; }
    /** Upper bound of ray steps left, for slicing the work evenly across the pulse. */
    public long workLeft() { return (long) remaining * maxSteps; }

    /** Advances up to {@code steps} ray steps, round-robin across live rays. */
    public void advance(int steps) {
        for (int n = 0; n < steps && remaining > 0; n++) {
            int i = cursor; cursor = (cursor + 1) % rays;
            if (done[i]) { n--; continue; }
            march(i);
        }
    }

    private void march(int i) {
        var d = PlumbLinePolicy.direction(i, rays);
        int s = ++step[i];
        double t = s * STEP;
        int x = floor(ox + d[0] * t), y = floor(oy + d[1] * t), z = floor(oz + d[2] * t);
        if (s >= maxSteps || y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) { stop(i); return; }
        var state = read(x, y, z);
        if (state == null) { stop(i); return; } // not yet loaded: the scan cannot see there
        boolean below = y < chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) - 2;
        Medium medium = medium(state, below);
        long key = BlockPos.asLong(x, y, z);
        switch (medium) {
            case WATER -> { stop(i); return; }
            case METAL -> { metalCells.add(key); metalTypes.putIfAbsent(key, id(state)); stop(i); return; }
            default -> { }
        }
        if (isOre(state)) {
            oreTypes.putIfAbsent(key, id(state));
            var prev = oreCells.get(key);
            if (prev == null || t < prev[0]) oreCells.put(key, new double[] {t, rock[i]});
        } else if (medium == Medium.OPEN && below) voidCells.add(key);
        if (medium == Medium.STONE || medium == Medium.DEEPSLATE) rock[i] += STEP;
        budget[i] -= (float) (PlumbLinePolicy.cost(medium, frozen(y)) * STEP);
        if (budget[i] <= 0) stop(i);
    }

    private void stop(int i) { if (!done[i]) { done[i] = true; remaining--; } }
    private static int floor(double v) { return (int) Math.floor(v); }

    private BlockState read(int x, int y, int z) {
        long c = ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
        if (c != cachedChunk) { cachedChunk = c; chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4); }
        return chunk == null ? null : chunk.getBlockState(new BlockPos(x, y, z));
    }

    private boolean frozen(int y) {
        return frozenByY.computeIfAbsent(y, k -> TemperatureManager.getBackgroundTemperature(k, day, totalDays) < 0);
    }

    static Medium medium(BlockState s, boolean below) {
        if (s.getFluidState().is(FluidTags.WATER)) return Medium.WATER;
        if (isMetal(s)) return Medium.METAL;
        if (s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty() && !s.is(Blocks.SNOW)) return Medium.OPEN;
        if (s.is(BlockTags.ICE) || s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)
                || s.is(ModBlocks.FROZEN_ATMOSPHERE.get())) return Medium.ICE;
        if (s.is(Blocks.DEEPSLATE) || s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES) || id(s).contains("deepslate")) return Medium.DEEPSLATE;
        return Medium.STONE;
    }

    /** Ores by exact type, vanilla and Frozen Dawn alike: anything tagged as an ore or named like one. */
    static boolean isOre(BlockState s) {
        if (s.is(net.neoforged.neoforge.common.Tags.Blocks.ORES) || s.is(Blocks.ANCIENT_DEBRIS)) return true;
        String path = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return path.endsWith("_ore") || path.contains("_ore_");
    }

    private static final Set<Block> METALS = Set.of(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER,
            Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER, Blocks.CUT_COPPER, Blocks.NETHERITE_BLOCK, Blocks.RAW_IRON_BLOCK,
            Blocks.RAW_GOLD_BLOCK, Blocks.RAW_COPPER_BLOCK, Blocks.ANVIL, Blocks.IRON_DOOR, Blocks.IRON_TRAPDOOR, Blocks.IRON_BARS);
    static boolean isMetal(BlockState s) {
        if (METALS.contains(s.getBlock()) || s.is(net.neoforged.neoforge.common.Tags.Blocks.STORAGE_BLOCKS_IRON)) return true;
        String path = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return path.equals("lead_block") || path.startsWith("reactor_") || path.equals("orsa_supply_crate");
    }

    private static String id(BlockState s) { return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString(); }

    /**
     * Clusters hits into returns, nearest first, and adds ORSA structure markers within range. Each vein's exact
     * bounds go to {@code triangulate}; when it reports a match from another scanner the vein is shown exactly.
     */
    public List<Return> results(java.util.function.Predicate<Return> triangulate) {
        var out = new ArrayList<Return>();
        for (var vein : cluster(oreCells.keySet(), oreTypes, 2)) {
            var exact = new Return(Kind.ORE, vein.label, vein.minX, vein.minY, vein.minZ, vein.maxX, vein.maxY, vein.maxZ, true);
            if (triangulate.test(exact)) { out.add(exact); continue; }
            double nearest = Double.MAX_VALUE, rockAt = 0;
            for (long k : vein.cells) { var info = oreCells.get(k); if (info[0] < nearest) { nearest = info[0]; rockAt = info[1]; } }
            int pad = (int) Math.round(PlumbLinePolicy.pad(nearest, rockAt, setting));
            // Shift the blob a little, deterministically, so its centre does not give away the vein's.
            var r = new Random(BlockPos.asLong(vein.minX, vein.minY, vein.minZ) ^ origin.asLong());
            int jx = r.nextInt(pad / 2 * 2 + 1) - pad / 2, jy = r.nextInt(pad / 2 * 2 + 1) - pad / 2, jz = r.nextInt(pad / 2 * 2 + 1) - pad / 2;
            out.add(new Return(Kind.ORE, vein.label, vein.minX - pad + jx, vein.minY - pad + jy, vein.minZ - pad + jz,
                    vein.maxX + pad + jx, vein.maxY + pad + jy, vein.maxZ + pad + jz, false));
        }
        for (var cave : cluster(voidCells, null, 1)) if (cave.cells.size() >= PlumbLinePolicy.MIN_VOID_CELLS)
            out.add(new Return(Kind.VOID, "void", cave.minX, cave.minY, cave.minZ, cave.maxX, cave.maxY, cave.maxZ, false));
        for (var metal : cluster(metalCells, metalTypes, 1))
            out.add(new Return(Kind.METAL, metal.label, metal.minX, metal.minY, metal.minZ, metal.maxX, metal.maxY, metal.maxZ, false));
        structures(out);
        out.sort(Comparator.comparingDouble(r -> distSq(r)));
        return out.size() > PlumbLinePolicy.MAX_RETURNS ? new ArrayList<>(out.subList(0, PlumbLinePolicy.MAX_RETURNS)) : out;
    }

    private double distSq(Return r) {
        double cx = (r.minX() + r.maxX()) / 2.0 + .5 - ox, cy = (r.minY() + r.maxY()) / 2.0 + .5 - oy, cz = (r.minZ() + r.maxZ()) / 2.0 + .5 - oz;
        return cx * cx + cy * cy + cz * cz;
    }

    private void structures(List<Return> out) {
        var server = level.getServer();
        if (level.dimension() != ServerLevel.OVERWORLD) return;
        record Site(String label, BlockPos pos, int half) { }
        var sites = new ArrayList<Site>();
        for (var p : MonitoringStationState.get(server).getBuiltStationCenters()) sites.add(new Site("orsa.monitoring_station", p, 12));
        for (var p : ReactorSiteState.get(server).built()) sites.add(new Site("orsa.reactor_site", p, 22));
        for (var p : CargoDropState.get(server).getBuiltDropCenters()) sites.add(new Site("orsa.cargo_drop", p, 8));
        long r = setting.radius;
        for (var s : sites) {
            long dx = s.pos.getX() - origin.getX(), dz = s.pos.getZ() - origin.getZ();
            if (dx * dx + dz * dz > r * r) continue;
            out.add(new Return(Kind.STRUCTURE, s.label, s.pos.getX() - s.half, s.pos.getY() - 4, s.pos.getZ() - s.half,
                    s.pos.getX() + s.half, s.pos.getY() + 8, s.pos.getZ() + s.half, false));
        }
    }

    record Cluster(String label, List<Long> cells, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) { }

    /** Groups cells of the same label whose positions are within {@code reach} blocks on every axis. */
    static List<Cluster> cluster(Set<Long> cells, Map<Long, String> labels, int reach) {
        var out = new ArrayList<Cluster>();
        var left = new HashSet<>(cells);
        var sorted = new ArrayList<>(cells); Collections.sort(sorted);
        for (long seed : sorted) {
            if (!left.remove(seed)) continue;
            String label = labels == null ? "void" : labels.get(seed);
            var members = new ArrayList<Long>(); var queue = new ArrayDeque<Long>(); queue.add(seed);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            while (!queue.isEmpty()) {
                long k = queue.removeFirst(); members.add(k);
                int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
                for (int dx = -reach; dx <= reach; dx++) for (int dy = -reach; dy <= reach; dy++) for (int dz = -reach; dz <= reach; dz++) {
                    long n = BlockPos.asLong(x + dx, y + dy, z + dz);
                    if (left.contains(n) && (labels == null || label.equals(labels.get(n)))) { left.remove(n); queue.add(n); }
                }
            }
            out.add(new Cluster(label, members, minX, minY, minZ, maxX, maxY, maxZ));
        }
        return out;
    }
}
