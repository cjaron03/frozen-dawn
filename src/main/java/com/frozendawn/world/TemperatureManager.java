package com.frozendawn.world;

import com.frozendawn.block.GeothermalCoreBlockEntity;
import com.frozendawn.block.ThermalHeaterBlock;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

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

        return phaseTemp + depthTemp + shelterTemp + heatTemp;
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
            BlockEntity heaterBE = level.getBlockEntity(heaterPos);
            if (heaterBE instanceof ThermalHeaterBlockEntity thbe) {
                sheltered = thbe.getCachedSheltered();
            }
            float warmth = getHeaterHeat(state, distSq, phase, sheltered);
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

            // Above Y=0: halve effectiveness
            if (corePos.getY() >= 0) {
                coreRange /= 2;
                coreTemp /= 2;
            }

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
    private static float getHeaterHeat(BlockState state, int distSq, int phase, boolean sheltered) {
        if (state.is(ModBlocks.THERMAL_HEATER.get()) && state.getValue(ThermalHeaterBlock.LIT)) {
            int maxDistSq = (phase >= 5 && !sheltered) ? 18 : 49;
            return distSq <= maxDistSq ? 35.0f : 0.0f;
        }
        if (state.is(ModBlocks.IRON_THERMAL_HEATER.get()) && state.getValue(ThermalHeaterBlock.LIT)) {
            int maxDistSq = (phase >= 5 && !sheltered) ? 29 : 81;
            return distSq <= maxDistSq ? 50.0f : 0.0f;
        }
        if (state.is(ModBlocks.GOLD_THERMAL_HEATER.get()) && state.getValue(ThermalHeaterBlock.LIT)) {
            int maxDistSq = (phase >= 5 && !sheltered) ? 44 : 121;
            return distSq <= maxDistSq ? 65.0f : 0.0f;
        }
        if (state.is(ModBlocks.DIAMOND_THERMAL_HEATER.get()) && state.getValue(ThermalHeaterBlock.LIT)) {
            int maxDistSq = (phase >= 5 && !sheltered) ? 71 : 196;
            return distSq <= maxDistSq ? 80.0f : 0.0f;
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
