package com.frozendawn.cyclone;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.homo.HearthProtectionPolicy;
import com.frozendawn.phase.PhaseManager;
import com.frozendawn.world.*;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.levelgen.Heightmap;

/** Extra cyclone deposits. Every accepted edit adds one layer; existing terrain is never removed. */
public final class CycloneDrift {
    public static final int RADIUS = 48;
    public static final int MAX_CHECKS_PER_LEVEL_TICK = 96;
    private CycloneDrift() {}

    public static int shadowDepth(int obstacleHeight, int distanceDownwind) {
        if (obstacleHeight <= 0 || distanceDownwind < 1 || distanceDownwind > 6) return 0;
        return Math.max(0, (int) Math.min(8L, (long) obstacleHeight * 2 - distanceDownwind));
    }
    public static int budgetPerTick(CycloneSample sample) {
        return switch (sample.stage()) {
            case ARRIVAL, PEAK, DISSIPATION -> Math.round(6 * Math.clamp(sample.intensity(), 0, 1));
            default -> 0;
        };
    }
    public static void tick(ServerLevel level, int phase, float progress) {
        if (level.dimension() != Level.OVERWORLD || phase < 5 || PhaseManager.isPhase6MidOrLater(phase, progress)) return;
        var storm = CycloneSavedData.get(level.getServer()).active().orElse(null);
        if (storm == null || CycloneController.suppressed(level)) return;
        var players = level.players();
        if (players.isEmpty()) return;
        int checks = 0, start = (int) Math.floorMod(level.getGameTime(), players.size());
        var visited = new HashSet<Long>();
        for (int index = 0; index < players.size() && checks < MAX_CHECKS_PER_LEVEL_TICK; index++) {
            var player = players.get((start + index) % players.size());
            if (player.isSpectator()) continue;
            var origin = player.blockPosition();
            int budget = budgetPerTick(CycloneField.sample(storm, player.getX(), player.getZ(), level.getGameTime()));
            for (int i = 0; i < budget && checks < MAX_CHECKS_PER_LEVEL_TICK; i++, checks++) {
                int x = origin.getX() + level.random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + level.random.nextInt(RADIUS * 2 + 1) - RADIUS;
                if (!visited.add(((long) x << 32) ^ (z & 0xffffffffL))) continue;
                place(level, x, z, storm);
            }
        }
    }
    /** Actual production column policy, exposed for native safety fixtures. No player/terrain reset. */
    public static boolean tryColumn(ServerLevel level, int x, int z) {
        var apocalypse = ApocalypseState.get(level.getServer());
        if (level.dimension() != Level.OVERWORLD || apocalypse.getPhase() < 5
                || PhaseManager.isPhase6MidOrLater(apocalypse.getPhase(), apocalypse.getProgress())
                || CycloneController.suppressed(level)) return false;
        var storm = CycloneSavedData.get(level.getServer()).active().orElse(null);
        return storm != null && place(level, x, z, storm);
    }
    private static boolean place(ServerLevel level, int x, int z, CycloneStorm storm) {
        if (budgetPerTick(CycloneField.sample(storm, x + .5, z + .5, level.getGameTime())) == 0) return false;
        // Check before heightmap access: the shared scanner must not request an absent chunk.
        if (!loaded(level, x, z) || !loaded(level, x - 1, z) || !loaded(level, x + 1, z)
                || !loaded(level, x, z - 1) || !loaded(level, x, z + 1)) return false;
        var ground = SurfaceColumnScanner.findSnowSupportBelowCover(level, x, z, SurfaceColumnScanner.DEFAULT_MAX_SCAN_DEPTH);
        if (ground == null || level.getBlockState(ground).hasBlockEntity() || !SnowAccumulator.canPlaceSnowOn(level, ground)) return false;
        var pos = ground.above();
        var at = level.getBlockState(pos);
        // Full snow blocks and eight-layer stacks stop here; never start a second vertical block.
        if (!at.isAir() && !at.is(Blocks.SNOW)) return false;
        if (CycloneThermal.surfaceWeight(pos.getY()) <= 0 || !at.getFluidState().isEmpty()
                || !SnowAccumulator.isOpenToSnow(level, pos)
                || level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) > pos.getY() + 1
                || RoomAtmosphere.cachedSealAt(level, pos) == RoomAtmosphere.Seal.SEALED
                || HearthProtectionPolicy.isEnvironmentalMutationProtected(ReturnedHearthSavedData.get(level.getServer()), pos)
                || BlastPitWarmZoneRegistry.isInsideWarmZone(level, pos) || ThermalVentRegistry.isVolcanicField(level, pos)) return false;
        int target = targetLayers(level, pos, storm.headingRad());
        int current = at.is(Blocks.SNOW) ? at.getValue(SnowLayerBlock.LAYERS) : 0;
        if (current >= target) return false;
        var next = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, current + 1);
        return next.canSurvive(level, pos) && level.setBlock(pos, next, 3);
    }
    private static int targetLayers(ServerLevel level, BlockPos pos, float heading) {
        double windX = Math.cos(heading), windZ = Math.sin(heading);
        for (int distance = 1; distance <= 6; distance++) {
            var obstacle = BlockPos.containing(pos.getX() + .5 - windX * distance, pos.getY(), pos.getZ() + .5 - windZ * distance);
            if (!loaded(level, obstacle.getX(), obstacle.getZ())) return 0;
            int height = 0;
            for (int dy = 0; dy < 6 && obstacle.getY() + dy < level.getMaxBuildHeight(); dy++) {
                var block = obstacle.above(dy); var state = level.getBlockState(block);
                if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || !state.isCollisionShapeFullBlock(level, block)) break;
                height++;
            }
            if (height > 0) return shadowDepth(height, distance);
        }
        return 0;
    }
    private static boolean loaded(ServerLevel level, int x, int z) {
        return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
    }
}
