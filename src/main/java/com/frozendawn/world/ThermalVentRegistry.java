package com.frozendawn.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class ThermalVentRegistry {

    private static final int MAX_VERTICAL_DRIFT = 12;
    private static final WeakHashMap<Level, Map<BlockPos, ThermalVentSnapshot>> ventsByLevel = new WeakHashMap<>();

    private ThermalVentRegistry() {
    }

    public static void beginTick(Level level) {
        ventsByLevel.computeIfAbsent(level, ignored -> new HashMap<>()).clear();
    }

    public static void register(Level level, ThermalVentSnapshot snapshot) {
        ventsByLevel.computeIfAbsent(level, ignored -> new HashMap<>()).put(snapshot.anchorPos().immutable(), snapshot);
    }

    public static Collection<ThermalVentSnapshot> getVents(Level level) {
        Map<BlockPos, ThermalVentSnapshot> vents = ventsByLevel.get(level);
        return vents != null ? vents.values() : Collections.emptyList();
    }

    public static float getWarmthFloor(Level level, BlockPos pos) {
        float warmthFloor = Float.NEGATIVE_INFINITY;
        for (ThermalVentSnapshot snapshot : getVents(level)) {
            if (!snapshot.contributesWarmth()) {
                continue;
            }
            if (Math.abs(pos.getY() - snapshot.poolPos().getY()) > MAX_VERTICAL_DRIFT) {
                continue;
            }
            if (horizontalDistanceSqr(pos, snapshot.poolPos()) <= snapshot.warmthRadius() * snapshot.warmthRadius()) {
                warmthFloor = Math.max(warmthFloor, snapshot.warmthFloor());
            }
        }
        return warmthFloor;
    }

    public static float getOverheatBonus(Level level, BlockPos pos) {
        float overheat = 0.0f;
        for (ThermalVentSnapshot snapshot : getVents(level)) {
            if (Math.abs(pos.getY() - snapshot.poolPos().getY()) > MAX_VERTICAL_DRIFT) {
                continue;
            }
            int horizontalDistSqr = horizontalDistanceSqr(pos, snapshot.poolPos());
            if (snapshot.rimRadius() > 0 && horizontalDistSqr <= snapshot.rimRadius() * snapshot.rimRadius()) {
                overheat = Math.max(overheat, snapshot.rimOverheatBonus());
            }
            if (snapshot.isErupting() && snapshot.eruptionRadius() > 0
                    && horizontalDistSqr <= snapshot.eruptionRadius() * snapshot.eruptionRadius()) {
                overheat = Math.max(overheat, snapshot.eruptionHeatBonus());
            }
        }
        return overheat;
    }

    public static boolean isLethalPool(Level level, BlockPos pos) {
        for (ThermalVentSnapshot snapshot : getVents(level)) {
            if (!snapshot.isPoolLethal()) {
                continue;
            }
            if (Math.abs(pos.getY() - snapshot.poolPos().getY()) > 1) {
                continue;
            }
            if (horizontalDistanceSqr(pos, snapshot.poolPos()) <= 2) {
                return true;
            }
        }
        return false;
    }

    public static void reset() {
        ventsByLevel.clear();
    }

    private static int horizontalDistanceSqr(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
