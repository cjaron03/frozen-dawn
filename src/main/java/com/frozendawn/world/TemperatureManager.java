package com.frozendawn.world;

import com.frozendawn.block.ThermalHeaterBlock;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Calculates temperature at any world position.
 *
 * finalTemp = phaseModifier + depthModifier + shelterModifier + heatSourceModifier
 *
 * Used by PlayerTickHandler for exposure damage and TaN integration.
 */
public final class TemperatureManager {

    private TemperatureManager() {}

    private static final int MAX_HEAT_RADIUS = 12;    // Geothermal Core needs 12
    private static final int MOB_HEAT_RADIUS = 3;     // Reduced scan for mobs (7^3=343)

    /**
     * Full-precision temperature check (used for players and TaN integration).
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
     * Heat source modifier: find the strongest heat source within range.
     * Returns the warmth value of the best source (not additive).
     *
     * @param quickScan  Reduced radius + early exit on first heat found (for mobs)
     */
    public static float getHeatSourceModifier(Level level, BlockPos pos, int currentDay, int totalDays, boolean quickScan) {
        float bestWarmth = 0.0f;
        int phase = PhaseManager.getPhase(currentDay, totalDays);
        int radius = quickScan ? MOB_HEAT_RADIUS : MAX_HEAT_RADIUS;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int distSq = dx * dx + dy * dy + dz * dz;

                    BlockPos checkPos = pos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(checkPos);
                    float warmth = getHeatForBlock(state, distSq, phase, checkPos);
                    if (warmth > bestWarmth) {
                        bestWarmth = warmth;
                        if (quickScan) return bestWarmth; // Mobs: any heat = safe, done
                    }
                }
            }
        }

        return bestWarmth;
    }

    /**
     * Returns the warmth provided by a block at the given distance-squared, or 0 if out of range.
     * Uses distSq to avoid Vec3 allocation and sqrt() per block.
     */
    private static float getHeatForBlock(BlockState state, int distSq, int phase, BlockPos checkPos) {
        // Geothermal Core: Y-dependent behavior
        if (state.is(ModBlocks.GEOTHERMAL_CORE.get())) {
            if (checkPos.getY() < 0) {
                // Below Y=0: radius 12 (distSq <= 144), +50C
                return distSq <= 144 ? 50.0f : 0.0f;
            } else {
                // Above Y=0: radius 6 (distSq <= 36), +15C
                return distSq <= 36 ? 15.0f : 0.0f;
            }
        }

        // Thermal Heater (lit): radius 7 (distSq <= 49), +35C
        if (state.is(ModBlocks.THERMAL_HEATER.get()) && state.getValue(ThermalHeaterBlock.LIT)) {
            return distSq <= 49 ? 35.0f : 0.0f;
        }

        // Campfire (lit): radius 5 (distSq <= 25), +25C
        if (state.is(Blocks.CAMPFIRE) && state.getValue(BlockStateProperties.LIT)) {
            return distSq <= 25 ? 25.0f : 0.0f;
        }

        // Soul campfire (lit): radius 6 (distSq <= 36), +28C
        if (state.is(Blocks.SOUL_CAMPFIRE) && state.getValue(BlockStateProperties.LIT)) {
            return distSq <= 36 ? 28.0f : 0.0f;
        }

        // Furnace / Blast Furnace / Smoker (lit): radius 3 (distSq <= 9), +15C
        if ((state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER))
                && state.getValue(BlockStateProperties.LIT)) {
            return distSq <= 9 ? 15.0f : 0.0f;
        }

        // Lava: radius 4 (distSq <= 16), +30C (until phase 4, when lava starts freezing)
        if (state.is(Blocks.LAVA) && phase < 4) {
            return distSq <= 16 ? 30.0f : 0.0f;
        }

        // Magma block: radius 2 (distSq <= 4), +10C
        if (state.is(Blocks.MAGMA_BLOCK)) {
            return distSq <= 4 ? 10.0f : 0.0f;
        }

        // Fire / Soul fire: radius 3 (distSq <= 9), +20C
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            return distSq <= 9 ? 20.0f : 0.0f;
        }

        return 0.0f;
    }
}
