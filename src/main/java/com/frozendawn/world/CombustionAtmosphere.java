package com.frozendawn.world;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.phase.PhaseManager;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Ordinary combustion shares the existing vacuum/room-air authority, not temperature. */
public final class CombustionAtmosphere {
    private record Sample(long tick, boolean breathable) {}
    private static final Map<ServerLevel, Map<BlockPos, Sample>> CACHE = new WeakHashMap<>();
    private CombustionAtmosphere() {}

    public static boolean isVacuum(Level level) {
        if (level.isClientSide() || level.dimension() != Level.OVERWORLD || level.getServer() == null) return false;
        var state = ApocalypseState.get(level.getServer());
        return PhaseManager.isVacuumActive(state.getPhase(), state.getProgress());
    }

    public static boolean canBurnAt(Level level, BlockPos pos) {
        if (!isVacuum(level)) return true;
        var serverLevel = (ServerLevel) level;
        var cache = CACHE.computeIfAbsent(serverLevel, ignored -> new HashMap<>());
        var sample = cache.get(pos);
        long tick = level.getGameTime();
        if (sample != null && tick >= sample.tick() && tick - sample.tick() < 10) return sample.breathable();
        boolean breathable = hasAir(level, pos);
        if (cache.size() >= 4096) cache.clear();
        cache.put(pos.immutable(), new Sample(tick, breathable));
        return breathable;
    }

    private static boolean hasAir(Level level, BlockPos pos) {
        if (TemperatureManager.hasOxygenSupport(level, pos)) return true;
        // A solid furnace/lantern cannot itself be the flood-fill origin.
        // Sample a loaded open intake cell; never load a chunk to find oxygen.
        if (isAirCell(level, pos) && TemperatureManager.hasBreathableAir(level, pos)) return true;
        for (Direction direction : Direction.values()) {
            BlockPos intake = pos.relative(direction);
            if (isAirCell(level, intake) && TemperatureManager.hasBreathableAir(level, intake)) return true;
        }
        return false;
    }

    private static boolean isAirCell(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        var state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && (state.isAir() || !state.blocksMotion() || state.getCollisionShape(level, pos).isEmpty());
    }

    public static void reset() { CACHE.clear(); }
}
