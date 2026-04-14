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
    private static final int FREEZE_PROTECTION_VERTICAL_DRIFT = 18;
    private static final int VOLCANIC_FIELD_VERTICAL_DRIFT = 20;
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

    public static float getCalderaOverheatBonus(Level level, BlockPos pos) {
        float overheat = 0.0f;
        for (ThermalVentSnapshot snapshot : getVents(level)) {
            if (snapshot.archetype() != ThermalVentArchetype.RUPTURE || !snapshot.state().contributesWarmth()) {
                continue;
            }
            if (Math.abs(pos.getY() - snapshot.poolPos().getY()) > VOLCANIC_FIELD_VERTICAL_DRIFT) {
                continue;
            }
            int calderaRadius = ruptureCalderaRadius(snapshot.coneStage());
            if (snapshot.isWarning() || snapshot.isErupting()) {
                calderaRadius += 1;
            }
            if (horizontalDistanceSqr(pos, snapshot.poolPos()) <= calderaRadius * calderaRadius) {
                overheat = Math.max(overheat, snapshot.rimOverheatBonus());
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

    public static boolean isFreezeProtected(Level level, BlockPos pos) {
        for (ThermalVentSnapshot snapshot : getVents(level)) {
            if (snapshot.state() == ThermalVentState.DORMANT || snapshot.state() == ThermalVentState.SPENT) {
                continue;
            }
            if (Math.abs(pos.getY() - snapshot.poolPos().getY()) > FREEZE_PROTECTION_VERTICAL_DRIFT) {
                continue;
            }
            int protectionRadius = switch (snapshot.archetype()) {
                case WARM -> snapshot.warmthRadius() + 2;
                case ACTIVE -> Math.max(snapshot.warmthRadius() + 4, snapshot.eruptionRadius() + 2);
                case RUPTURE -> Math.max(snapshot.warmthRadius() + 8 + snapshot.coneStage(),
                        snapshot.eruptionRadius() + 6 + snapshot.coneStage());
            };
            if (snapshot.isWarning() || snapshot.isErupting()) {
                protectionRadius += 2;
            }
            if (protectionRadius <= 0) {
                continue;
            }
            if (horizontalDistanceSqr(pos, snapshot.poolPos()) <= protectionRadius * protectionRadius) {
                return true;
            }
        }
        return false;
    }

    public static boolean isVolcanicField(Level level, BlockPos pos) {
        for (ThermalVentSnapshot snapshot : getVents(level)) {
            if (snapshot.archetype() != ThermalVentArchetype.RUPTURE || !snapshot.state().contributesWarmth()) {
                continue;
            }
            if (Math.abs(pos.getY() - snapshot.poolPos().getY()) > VOLCANIC_FIELD_VERTICAL_DRIFT) {
                continue;
            }
            int fieldRadius = ruptureVolcanicFieldRadius(snapshot.coneStage());
            if (snapshot.isWarning() || snapshot.isErupting()) {
                fieldRadius += 2;
            }
            if (horizontalDistanceSqr(pos, snapshot.poolPos()) <= fieldRadius * fieldRadius) {
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

    private static int ruptureVolcanicFieldRadius(int coneStage) {
        return 23 + coneStage + coneStage / 2 + coneStage / 4 + coneStage / 3;
    }

    private static int ruptureCalderaRadius(int coneStage) {
        return 4 + coneStage / 3;
    }
}
