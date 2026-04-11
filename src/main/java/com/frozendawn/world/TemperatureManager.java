package com.frozendawn.world;

import com.frozendawn.block.GeothermalCoreBlockEntity;
import com.frozendawn.block.ThermalHeaterBlock;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.FrostmiteEntity;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.phase.PhaseManager;
import com.frozendawn.world.BlastPitWarmZoneRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Calculates temperature at any world position.
 *
 * finalTemp = phaseModifier + depthModifier + shelterModifier + heatSourceModifier
 *
 * Used by PlayerTickHandler for exposure damage.
 */
public final class TemperatureManager {

    private TemperatureManager() {}

    /** Max radius for non-heater heat sources (soul campfire = 6). */
    private static final int AMBIENT_HEAT_RADIUS = 6;
    private static final int MOB_HEAT_RADIUS = 3;
    private static final int BREATHABLE_MAX_VISITED = 12_000;
    private static final int BREATHABLE_MAX_HORIZONTAL = 48;
    private static final int BREATHABLE_MAX_VERTICAL = 24;

    /**
     * Full-precision temperature check (used for players).
     */
    public static float getTemperatureAt(Level level, BlockPos pos, int currentDay, int totalDays) {
        return getTemperatureAt(level, pos, currentDay, totalDays, false);
    }

    /**
     * Get the effective temperature at a position, accounting for all modifiers.
     *
     * @param quickScan  If true, uses reduced heat scan radius and exits on first heat source found.
     *                   Use for mobs where exact best-warmth isn't needed.
     */
    public static float getTemperatureAt(Level level, BlockPos pos, int currentDay, int totalDays, boolean quickScan) {
        // Clamp inputs to prevent bad interpolation from corrupted world data
        currentDay = Math.max(0, currentDay);
        totalDays = Math.max(1, totalDays);
        float phaseTemp = PhaseManager.getTemperatureOffset(currentDay, totalDays);
        float depthTemp = PhaseManager.getDepthModifier(pos.getY())
                * FrozenDawnConfig.GEOTHERMAL_STRENGTH.get().floatValue();
        float shelterTemp = getShelterModifier(level, pos);
        float heatTemp = getHeatSourceModifier(level, pos, currentDay, totalDays, quickScan)
                * FrozenDawnConfig.HEAT_SOURCE_MULTIPLIER.get().floatValue();
        float finalTemp = phaseTemp + depthTemp + shelterTemp + heatTemp;

        if (BlastPitWarmZoneRegistry.isInsideWarmZone(level, pos)) {
            return Math.max(finalTemp, 24.0f);
        }
        float ventFloor = ThermalVentRegistry.getWarmthFloor(level, pos);
        if (ventFloor > Float.NEGATIVE_INFINITY) {
            finalTemp = Math.max(finalTemp, ventFloor);
        }
        finalTemp += ThermalVentRegistry.getOverheatBonus(level, pos);
        return finalTemp;
    }

    /**
     * Shelter modifier: +5C if there's a solid block or insulated glass overhead (roof).
     * Simple check: scan upward up to 4 blocks for a solid block or insulated glass.
     */
    public static float getShelterModifier(Level level, BlockPos pos) {
        for (int dy = 1; dy <= 4; dy++) {
            BlockPos above = pos.above(dy);
            BlockState aboveState = level.getBlockState(above);
            if (aboveState.isSolidRender(level, above) || aboveState.is(ModBlocks.INSULATED_GLASS.get())) {
                return 5.0f;
            }
        }
        return 0.0f;
    }

    /**
     * Returns true if the position is in an enclosed room: roof within 4 blocks
     * above AND a solid wall in each cardinal direction within 10 blocks.
     * A single floating block won't qualify — actual walls + ceiling required.
     */
    public static boolean isEnclosed(Level level, BlockPos pos) {
        // Roof check
        boolean hasRoof = false;
        for (int dy = 1; dy <= 4; dy++) {
            BlockPos above = pos.above(dy);
            BlockState state = level.getBlockState(above);
            if (state.isSolidRender(level, above) || state.is(ModBlocks.INSULATED_GLASS.get())) {
                hasRoof = true;
                break;
            }
        }
        if (!hasRoof) return false;

        // Wall check: solid block in each cardinal direction within 10 blocks
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            boolean hasWall = false;
            for (int i = 1; i <= 10; i++) {
                BlockPos wallPos = pos.relative(dir, i);
                BlockState state = level.getBlockState(wallPos);
                if (state.isSolidRender(level, wallPos) || state.is(ModBlocks.INSULATED_GLASS.get())) {
                    hasWall = true;
                    break;
                }
            }
            if (!hasWall) return false;
        }
        return true;
    }

    /**
     * Returns true when the position has breathable air according to the shared
     * late-phase vacuum authority: intentional ORSA support systems or a sealed room.
     */
    public static boolean hasBreathableAir(Level level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) return false;
        if (BlastPitWarmZoneRegistry.isInsideWarmZone(level, pos)) return true;
        if (isInsideGeothermalO2Range(level, pos)) return true;
        return isInsideSealedRoom(level, pos);
    }

    private static boolean isInsideGeothermalO2Range(Level level, BlockPos pos) {
        for (BlockPos corePos : GeothermalCoreRegistry.getCores(level)) {
            int o2Range;
            BlockEntity be = level.getBlockEntity(corePos);
            if (be instanceof GeothermalCoreBlockEntity core) {
                o2Range = core.getEffectiveO2Range();
            } else {
                o2Range = GeothermalCoreBlockEntity.BASE_O2_RANGE;
            }
            if (pos.distSqr(corePos) <= (long) o2Range * o2Range) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInsideSealedRoom(Level level, BlockPos origin) {
        if (!level.isLoaded(origin) || !isBreathablePassage(level, origin)) {
            return false;
        }

        Queue<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        BlockPos start = origin.immutable();
        open.add(start);
        visited.add(start);

        while (!open.isEmpty()) {
            BlockPos current = open.remove();

            if (!level.isLoaded(current)) {
                return false;
            }
            if (!isWithinBreathableBounds(origin, current)) {
                return false;
            }
            if (level.canSeeSky(current)) {
                return false;
            }

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (visited.contains(next)) {
                    continue;
                }
                if (!level.isLoaded(next)) {
                    return false;
                }
                if (!isWithinBreathableBounds(origin, next)) {
                    return false;
                }
                if (!isBreathablePassage(level, next)) {
                    continue;
                }
                if (visited.size() >= BREATHABLE_MAX_VISITED) {
                    return false;
                }

                BlockPos immutable = next.immutable();
                visited.add(immutable);
                open.add(immutable);
            }
        }

        return true;
    }

    private static boolean isWithinBreathableBounds(BlockPos origin, BlockPos pos) {
        int dx = Math.abs(pos.getX() - origin.getX());
        int dy = Math.abs(pos.getY() - origin.getY());
        int dz = Math.abs(pos.getZ() - origin.getZ());
        return dx <= BREATHABLE_MAX_HORIZONTAL
                && dz <= BREATHABLE_MAX_HORIZONTAL
                && dy <= BREATHABLE_MAX_VERTICAL;
    }

    private static boolean isBreathablePassage(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.isAir() || !state.blocksMotion() || state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Heat source modifier: sums warmth from all nearby heat sources (stacking).
     * Thermal heaters use HeaterRegistry (O(n) where n = lit heaters).
     * Other heat sources (campfires, lava, etc.) use a small block scan (radius 6).
     *
     * @param quickScan  Reduced radius + early exit on first heat found (for mobs)
     */
    public static float getHeatSourceModifier(Level level, BlockPos pos, int currentDay, int totalDays, boolean quickScan) {
        float totalWarmth = 0.0f;
        int phase = PhaseManager.getPhase(currentDay, totalDays);

        // --- Registered thermal heaters (no block scan needed) ---
        for (BlockPos heaterPos : HeaterRegistry.getHeaters(level)) {
            int distSq = (int) pos.distSqr(heaterPos);
            BlockState state = level.getBlockState(heaterPos);
            boolean sheltered = false;
            boolean hasCapacitor = false;
            int radiusPenalty = 0;
            float heatPenalty = 0.0f;
            BlockEntity heaterBE = level.getBlockEntity(heaterPos);
            if (heaterBE instanceof ThermalHeaterBlockEntity thbe) {
                sheltered = thbe.getCachedSheltered();
                hasCapacitor = thbe.hasCapacitor();
                radiusPenalty = FrostmiteEntity.getHeaterRadiusPenalty(level, heaterPos);
                heatPenalty = thbe.getFrostmiteHeatPenalty();
            }
            float warmth = getHeaterHeat(state, distSq, phase, sheltered, hasCapacitor, radiusPenalty, heatPenalty);
            if (warmth > 0) {
                totalWarmth += warmth;
                if (quickScan) return totalWarmth;
            }
        }

        // --- Ambient heat sources: campfires, furnaces, lava, fire (small radius scan) ---
        int radius = quickScan ? MOB_HEAT_RADIUS : AMBIENT_HEAT_RADIUS;
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int distSq = dx * dx + dy * dy + dz * dz;
                    checkPos.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState state = level.getBlockState(checkPos);
                    float warmth = getAmbientHeat(state, distSq);
                    if (warmth > 0) {
                        totalWarmth += warmth;
                        if (quickScan) return totalWarmth;
                    }
                }
            }
        }

        // --- Registered geothermal cores (range up to 32, beyond block scan radius) ---
        for (BlockPos corePos : GeothermalCoreRegistry.getCores(level)) {
            double distSq = pos.distSqr(corePos);
            float coreRange, coreTemp;

            BlockEntity be = level.getBlockEntity(corePos);
            if (be instanceof GeothermalCoreBlockEntity core) {
                coreRange = core.getEffectiveRange();
                coreTemp = core.getEffectiveTemp();
            } else {
                coreRange = GeothermalCoreBlockEntity.BASE_RANGE;
                coreTemp = GeothermalCoreBlockEntity.BASE_TEMP;
            }

            coreRange = GeothermalCoreBlockEntity.applySurfaceWarmthPenalty(coreRange, corePos);
            coreTemp = GeothermalCoreBlockEntity.applySurfaceWarmthPenalty(coreTemp, corePos);

            if (distSq <= coreRange * coreRange) {
                totalWarmth += coreTemp;
                if (quickScan) return totalWarmth;
            }
        }

        return totalWarmth;
    }

    /**
     * Returns warmth from a registered thermal heater at the given distance-squared.
     * In phase 5+, exposed heaters (no roof) have 60% radius (distSq × 0.36).
     * This ensures Diamond exposed (r≈8.4) > Base enclosed (r=7).
     *
     * @param sheltered  Cached shelter status from the heater's block entity.
     */
    private static float getHeaterHeat(BlockState state, int distSq, int phase, boolean sheltered, boolean hasCapacitor,
                                       int radiusPenalty, float heatPenalty) {
        if (state.is(ModBlocks.THERMAL_HEATER.get()) && state.getValue(ThermalHeaterBlock.LIT)) {
            int baseRadius = hasCapacitor ? 14 : 7;
            if (phase >= 5 && !sheltered) baseRadius = Math.max(2, (int) (baseRadius * 0.6f));
            baseRadius = Math.max(2, baseRadius - radiusPenalty);
            int maxDistSq = baseRadius * baseRadius;
            float heat = hasCapacitor ? 52.5f : 35.0f;
            return distSq <= maxDistSq ? Math.max(0.0f, heat - heatPenalty) : 0.0f;
        }
        if (state.is(ModBlocks.IRON_THERMAL_HEATER.get()) && state.getValue(ThermalHeaterBlock.LIT)) {
            int baseRadius = hasCapacitor ? 18 : 9;
            if (phase >= 5 && !sheltered) baseRadius = Math.max(2, (int) (baseRadius * 0.6f));
            baseRadius = Math.max(2, baseRadius - radiusPenalty);
            int maxDistSq = baseRadius * baseRadius;
            float heat = hasCapacitor ? 75.0f : 50.0f;
            return distSq <= maxDistSq ? Math.max(0.0f, heat - heatPenalty) : 0.0f;
        }
        if (state.is(ModBlocks.GOLD_THERMAL_HEATER.get()) && state.getValue(ThermalHeaterBlock.LIT)) {
            int baseRadius = hasCapacitor ? 22 : 11;
            if (phase >= 5 && !sheltered) baseRadius = Math.max(2, (int) (baseRadius * 0.6f));
            baseRadius = Math.max(2, baseRadius - radiusPenalty);
            int maxDistSq = baseRadius * baseRadius;
            float heat = hasCapacitor ? 97.5f : 65.0f;
            return distSq <= maxDistSq ? Math.max(0.0f, heat - heatPenalty) : 0.0f;
        }
        if (state.is(ModBlocks.DIAMOND_THERMAL_HEATER.get()) && state.getValue(ThermalHeaterBlock.LIT)) {
            int baseRadius = hasCapacitor ? 28 : 14;
            if (phase >= 5 && !sheltered) baseRadius = Math.max(2, (int) (baseRadius * 0.6f));
            baseRadius = Math.max(2, baseRadius - radiusPenalty);
            int maxDistSq = baseRadius * baseRadius;
            float heat = hasCapacitor ? 120.0f : 80.0f;
            return distSq <= maxDistSq ? Math.max(0.0f, heat - heatPenalty) : 0.0f;
        }
        return 0.0f;
    }

    /**
     * Returns warmth from ambient (non-heater) heat sources at the given distance-squared.
     */
    private static float getAmbientHeat(BlockState state, int distSq) {
        if (state.is(Blocks.CAMPFIRE) && state.getValue(BlockStateProperties.LIT)) {
            return distSq <= 25 ? 25.0f : 0.0f;
        }
        if (state.is(Blocks.SOUL_CAMPFIRE) && state.getValue(BlockStateProperties.LIT)) {
            return distSq <= 36 ? 28.0f : 0.0f;
        }
        if ((state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER))
                && state.getValue(BlockStateProperties.LIT)) {
            return distSq <= 9 ? 15.0f : 0.0f;
        }
        if (state.is(Blocks.LAVA)) {
            return distSq <= 16 ? 30.0f : 0.0f;
        }
        if (state.is(Blocks.MAGMA_BLOCK)) {
            return distSq <= 4 ? 10.0f : 0.0f;
        }
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            return distSq <= 9 ? 20.0f : 0.0f;
        }
        // Acheronite block: passive warmth aura (radius 3, +10C)
        if (state.is(ModBlocks.ACHERONITE_BLOCK.get())) {
            return distSq <= 9 ? 10.0f : 0.0f;
        }
        return 0.0f;
    }

    /**
     * Returns the base heat value a block would contribute at its own position.
     * This is used by client-side thermal rendering so heat signatures can match
     * the same source strengths the survival simulation uses.
     */
    public static float getAmbientSignatureHeat(BlockState state) {
        return getAmbientHeat(state, 0);
    }

    /**
     * Check if a heater has a roof overhead — reuses shelter detection logic.
     * Scan upward up to 4 blocks for a solid block or insulated glass.
     * Used to determine if wind exposure halves heater radius in phase 5+.
     */
    public static boolean isSheltered(Level level, BlockPos pos) {
        for (int dy = 1; dy <= 4; dy++) {
            BlockPos above = pos.above(dy);
            BlockState aboveState = level.getBlockState(above);
            if (aboveState.isSolidRender(level, above) || aboveState.is(ModBlocks.INSULATED_GLASS.get())) {
                return true;
            }
        }
        return false;
    }
}
