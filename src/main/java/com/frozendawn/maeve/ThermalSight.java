package com.frozendawn.maeve;

import com.frozendawn.block.HeatVentBlock;
import com.frozendawn.block.ResistiveHeaterBlock;
import com.frozendawn.block.ThermalHeaterBlock;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.thermal.RoomInsulation;
import com.frozendawn.world.GeothermalCoreRegistry;
import com.frozendawn.world.TemperatureManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * §9.4a thermal sight: what a Pyro standing where it is can see of the heat around a suspected home. Read-only.
 * Heat is a lit heater, an open vent, lava, fire, a lit furnace or campfire: anything the thermal model counts,
 * decoys included. Warm air spreads from each source through open cells (open doors, holes, gaps, open heat vents)
 * but never through full blocks, closed doors or glass. Thermal line of sight is blocked by any collider, glass included.
 * A Pyro sees heat three ways: the source itself, warm air escaping where it can see, or the outer face of a thin,
 * uninsulated wall right beside a source. Wool, snow and double walls show nothing.
 */
final class ThermalSight {
    record Sighting(String kind, BlockPos source, BlockPos seen, BlockPos inside) {
        Sighting { source = source.immutable(); seen = seen.immutable(); inside = inside.immutable(); }
        String action() { return "PYRO_" + kind + "_HEAT"; }
        double weight() { return kind.equals("WALL_GLOW") ? PyroPolicy.GLOW : PyroPolicy.SEEN; }
    }
    /**
     * {@code complete} only when every chunk between the eyes and the home area was loaded; nothing is read otherwise.
     * {@code inView} when the home itself is in sight, so a look that found nothing is evidence of a cold home.
     */
    record Scan(List<Sighting> sightings, int sources, boolean complete, boolean inView) {
        Scan { sightings = List.copyOf(sightings); }
    }
    static final int SOURCES = 32, AIR_RADIUS = 5, AIR_CELLS = 160, GLOW_REACH = 2, RAYS_PER_SOURCE = 48;

    private ThermalSight() { }

    static Scan scan(ServerLevel level, Entity viewer, BlockPos home) {
        Vec3 eye = viewer.getEyePosition();
        int r = PyroPolicy.SCAN_RADIUS;
        int minX = Math.min(BlockPos.containing(eye).getX(), home.getX() - r - AIR_RADIUS - 1) >> 4;
        int maxX = Math.max(BlockPos.containing(eye).getX(), home.getX() + r + AIR_RADIUS + 1) >> 4;
        int minZ = Math.min(BlockPos.containing(eye).getZ(), home.getZ() - r - AIR_RADIUS - 1) >> 4;
        int maxZ = Math.max(BlockPos.containing(eye).getZ(), home.getZ() + r + AIR_RADIUS + 1) >> 4;
        // A look must never turn into chunk loading.
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) if (!level.hasChunk(x, z)) return new Scan(List.of(), 0, false, false);
        var sources = sources(level, home);
        var sightings = new ArrayList<Sighting>();
        for (BlockPos source : sources) {
            var sighting = sight(level, viewer, eye, source);
            if (sighting != null) sightings.add(sighting);
        }
        return new Scan(sightings, sources.size(), true, inView(level, viewer, eye, home));
    }

    /** Nearest first, bounded. */
    static List<BlockPos> sources(ServerLevel level, BlockPos home) {
        int r = PyroPolicy.SCAN_RADIUS;
        int minY = Math.max(level.getMinBuildHeight(), home.getY() - PyroPolicy.SCAN_HEIGHT);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, home.getY() + PyroPolicy.SCAN_HEIGHT);
        var found = new ArrayList<BlockPos>();
        for (int cx = (home.getX() - r) >> 4; cx <= (home.getX() + r) >> 4; cx++) {
            for (int cz = (home.getZ() - r) >> 4; cz <= (home.getZ() + r) >> 4; cz++) {
                var chunk = level.getChunk(cx, cz);
                for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                    var section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.maybeHas(ThermalSight::hot)) continue;
                    for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        BlockPos pos = new BlockPos((cx << 4) + x, (sy << 4) + y, (cz << 4) + z);
                        if (pos.getY() < minY || pos.getY() > maxY || horizontal(pos, home) > r * r) continue;
                        if (hot(section.getBlockState(x, y, z))) found.add(pos);
                    }
                }
            }
        }
        for (BlockPos core : GeothermalCoreRegistry.nearby(level, home, r, 16))
            if (Math.abs(core.getY() - home.getY()) <= PyroPolicy.SCAN_HEIGHT && !found.contains(core)) found.add(core);
        return found.stream().sorted(Comparator.comparingDouble((BlockPos p) -> p.distSqr(home)).thenComparingLong(BlockPos::asLong))
                .limit(SOURCES).toList();
    }

    private static long horizontal(BlockPos a, BlockPos b) {
        long x = a.getX() - b.getX(), z = a.getZ() - b.getZ(); return x * x + z * z;
    }

    /** Anything the thermal model counts as a heat source; geothermal cores come from their registry. */
    static boolean hot(BlockState state) {
        if (TemperatureManager.getAmbientSignatureHeat(state) > 0) return true;
        var block = state.getBlock();
        if (block instanceof ThermalHeaterBlock || block instanceof ResistiveHeaterBlock) return state.getValue(BlockStateProperties.LIT);
        return block instanceof HeatVentBlock && (state.getValue(HeatVentBlock.VENTING) || state.getValue(HeatVentBlock.RADIATING));
    }

    static boolean glass(BlockState state) {
        return state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES) || state.is(ModBlocks.INSULATED_GLASS.get());
    }

    /** Warm air passes here: open doors and vents, empty cells and partial blocks (gaps). Never glass or full blocks. */
    static boolean permeable(ServerLevel level, BlockPos pos, BlockState state) {
        if (glass(state)) return false;
        var block = state.getBlock();
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) return state.getValue(BlockStateProperties.OPEN);
        if (block instanceof HeatVentBlock) return HeatVentBlock.active(state);
        return !state.isCollisionShapeFullBlock(level, pos);
    }

    /** The air one source warms, nearest first. */
    static Set<BlockPos> warmAir(ServerLevel level, BlockPos source) {
        var warm = new LinkedHashSet<BlockPos>(); var queue = new ArrayDeque<BlockPos>();
        warm.add(source.immutable()); queue.add(source.immutable());
        while (!queue.isEmpty() && warm.size() < AIR_CELLS) {
            BlockPos cell = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = cell.relative(direction);
                if (warm.contains(next) || next.distSqr(source) > AIR_RADIUS * AIR_RADIUS || !level.hasChunkAt(next)
                        || !permeable(level, next, level.getBlockState(next))) continue;
                warm.add(next); queue.add(next);
                if (warm.size() == AIR_CELLS) break;
            }
        }
        return warm;
    }

    private static Sighting sight(ServerLevel level, Entity viewer, Vec3 eye, BlockPos source) {
        if (visible(level, viewer, eye, source)) return new Sighting("DIRECT", source, source, source);
        var warm = warmAir(level, source);
        int rays = 0;
        for (BlockPos cell : warm) {
            if (cell.equals(source) || !frontier(level, warm, cell)) continue;
            if (rays++ >= RAYS_PER_SOURCE) break;
            if (visible(level, viewer, eye, cell)) return new Sighting("LEAKED", source, cell, cell);
        }
        for (BlockPos cell : warm) {
            if (cell.distSqr(source) > GLOW_REACH * GLOW_REACH) continue;
            for (Direction direction : Direction.values()) {
                BlockPos wall = cell.relative(direction), beyond = wall.relative(direction);
                if (warm.contains(wall) || !level.hasChunkAt(beyond)) continue;
                BlockState state = level.getBlockState(wall);
                if (permeable(level, wall, state) || glass(state) || RoomInsulation.conductance(level, wall, state) <= .1) continue;
                // Thin: open, unwarmed air straight behind it. A second layer, or anything solid there, hides the heat.
                if (warm.contains(beyond) || !permeable(level, beyond, level.getBlockState(beyond))) continue;
                if (visible(level, viewer, eye, wall)) return new Sighting("WALL_GLOW", source, wall, cell);
            }
        }
        return null;
    }

    /** Warm air with unwarmed open air beside it: where heat could show to an outside eye. */
    private static boolean frontier(ServerLevel level, Set<BlockPos> warm, BlockPos cell) {
        for (Direction direction : Direction.values()) {
            BlockPos next = cell.relative(direction);
            if (!warm.contains(next) && level.hasChunkAt(next) && permeable(level, next, level.getBlockState(next))) return true;
        }
        return false;
    }

    /** Within range, and the line toward the home first meets something of the home itself (or nothing), not a hill on the way. */
    private static boolean inView(ServerLevel level, Entity viewer, Vec3 eye, BlockPos home) {
        Vec3 center = Vec3.atCenterOf(home);
        if (eye.distanceToSqr(center) > PyroPolicy.SIGHT * PyroPolicy.SIGHT) return false;
        var hit = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().distSqr(home) <= PyroPolicy.HOME_VIEW * PyroPolicy.HOME_VIEW;
    }

    /** Within range, and the first collider on the way is the target block itself, or none at all. */
    private static boolean visible(ServerLevel level, Entity viewer, Vec3 eye, BlockPos target) {
        Vec3 center = Vec3.atCenterOf(target);
        if (eye.distanceToSqr(center) > PyroPolicy.SIGHT * PyroPolicy.SIGHT) return false;
        var hit = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }
}
