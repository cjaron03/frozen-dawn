package com.frozendawn.world;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
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

    // --- Heat source definitions: block -> {radius, warmth} ---

    private static final int MAX_HEAT_RADIUS = 6; // Soul campfire has the largest radius

    /**
     * Get the effective temperature at a position, accounting for all modifiers.
     *
     * @param level       The world
     * @param pos         Block position to check
     * @param currentDay  Current apocalypse day
     * @param totalDays   Total configured days
     * @return Temperature in degrees Celsius
     */
    public static float getTemperatureAt(Level level, BlockPos pos, int currentDay, int totalDays) {
        float phaseTemp = PhaseManager.getTemperatureOffset(currentDay, totalDays);
        float depthTemp = PhaseManager.getDepthModifier(pos.getY())
                * FrozenDawnConfig.GEOTHERMAL_STRENGTH.get().floatValue();
        float shelterTemp = getShelterModifier(level, pos);
        float heatTemp = getHeatSourceModifier(level, pos, currentDay, totalDays)
                * FrozenDawnConfig.HEAT_SOURCE_MULTIPLIER.get().floatValue();

        return phaseTemp + depthTemp + shelterTemp + heatTemp;
    }

    /**
     * Shelter modifier: +5C if there's a solid block overhead (roof).
     * Simple check: scan upward up to 4 blocks for a solid block.
     */
    public static float getShelterModifier(Level level, BlockPos pos) {
        for (int dy = 1; dy <= 4; dy++) {
            BlockState above = level.getBlockState(pos.above(dy));
            if (above.isSolidRender(level, pos.above(dy))) {
                return 5.0f;
            }
        }
        return 0.0f;
    }

    /**
     * Heat source modifier: find the strongest heat source within range.
     * Returns the warmth value of the best source (not additive).
     */
    public static float getHeatSourceModifier(Level level, BlockPos pos, int currentDay, int totalDays) {
        float bestWarmth = 0.0f;
        int phase = PhaseManager.getPhase(currentDay, totalDays);

        // Scan cube around position for heat sources
        for (int dx = -MAX_HEAT_RADIUS; dx <= MAX_HEAT_RADIUS; dx++) {
            for (int dy = -MAX_HEAT_RADIUS; dy <= MAX_HEAT_RADIUS; dy++) {
                for (int dz = -MAX_HEAT_RADIUS; dz <= MAX_HEAT_RADIUS; dz++) {
                    BlockPos checkPos = pos.offset(dx, dy, dz);
                    double distance = pos.getCenter().distanceTo(checkPos.getCenter());

                    BlockState state = level.getBlockState(checkPos);
                    float warmth = getHeatForBlock(state, distance, phase);
                    if (warmth > bestWarmth) {
                        bestWarmth = warmth;
                    }
                }
            }
        }

        return bestWarmth;
    }

    /**
     * Returns the warmth provided by a block at the given distance, or 0 if out of range.
     */
    private static float getHeatForBlock(BlockState state, double distance, int phase) {
        // Campfire (lit): radius 5, +25C
        if (state.is(Blocks.CAMPFIRE) && state.getValue(BlockStateProperties.LIT)) {
            return distance <= 5.0 ? 25.0f : 0.0f;
        }

        // Soul campfire (lit): radius 6, +28C
        if (state.is(Blocks.SOUL_CAMPFIRE) && state.getValue(BlockStateProperties.LIT)) {
            return distance <= 6.0 ? 28.0f : 0.0f;
        }

        // Furnace / Blast Furnace / Smoker (lit): radius 3, +15C
        if ((state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER))
                && state.getValue(BlockStateProperties.LIT)) {
            return distance <= 3.0 ? 15.0f : 0.0f;
        }

        // Lava: radius 4, +30C (until phase 4, when lava starts freezing)
        if (state.is(Blocks.LAVA) && phase < 4) {
            return distance <= 4.0 ? 30.0f : 0.0f;
        }

        // Magma block: radius 2, +10C
        if (state.is(Blocks.MAGMA_BLOCK)) {
            return distance <= 2.0 ? 10.0f : 0.0f;
        }

        // Fire / Soul fire: radius 3, +20C
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            return distance <= 3.0 ? 20.0f : 0.0f;
        }

        return 0.0f;
    }
}
