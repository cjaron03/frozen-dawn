package com.frozendawn.integration;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import toughasnails.api.temperature.IPositionalTemperatureModifier;
import toughasnails.api.temperature.IProximityBlockModifier;
import toughasnails.api.temperature.TemperatureHelper;
import toughasnails.api.temperature.TemperatureLevel;

/**
 * Optional integration with Tough As Nails.
 *
 * This class is ONLY loaded when TaN is present (checked via ModList before any reference).
 * Java's lazy class loading ensures no ClassNotFoundException when TaN is absent.
 *
 * Registers:
 * - Positional modifier: applies apocalypse phase cooling + geothermal depth warmth
 * - Proximity modifier:  marks frozen blocks as COOLING sources
 */
public final class TaNCompat {

    private TaNCompat() {}

    public static void init() {
        TemperatureHelper.registerPositionalTemperatureModifier(new ApocalypseTemperatureModifier());
        TemperatureHelper.registerProximityBlockModifier(new FrozenBlockProximityModifier());
        FrozenDawn.LOGGER.info("Tough As Nails integration registered");
    }

    // -----------------------------------------------------------------------
    // Positional modifier: phase cooling + depth warmth
    // -----------------------------------------------------------------------

    private static class ApocalypseTemperatureModifier implements IPositionalTemperatureModifier {

        @Override
        public TemperatureLevel modify(Level level, BlockPos pos, TemperatureLevel current) {
            // Only calculate on server (TaN syncs result to client)
            if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
                return current;
            }

            ApocalypseState state = ApocalypseState.get(serverLevel.getServer());

            // Phase temperature offset: 0 to -120
            float phaseTemp = state.getTemperatureOffset();

            // Depth modifier: -10 (sky) to +35 (deep underground)
            float depthTemp = PhaseManager.getDepthModifier(pos.getY())
                    * FrozenDawnConfig.GEOTHERMAL_STRENGTH.get().floatValue();

            float totalCooling = phaseTemp + depthTemp;

            // If net temperature is warm or neutral, no change needed
            if (totalCooling >= 0) {
                return current;
            }

            // Map Celsius cooling to TaN level decrements:
            // Every 18C of cooling shifts one level toward ICY
            // -18 → 1 level, -36 → 2, -54 → 3, -72+ → 4 (max)
            int decrement = Math.min(4, (int) (-totalCooling / 18f));
            return current.decrement(decrement);
        }
    }

    // -----------------------------------------------------------------------
    // Proximity modifier: frozen blocks emit cold
    // -----------------------------------------------------------------------

    private static class FrozenBlockProximityModifier implements IProximityBlockModifier {

        @Override
        public Type getProximityType(Level level, BlockPos pos, BlockState state) {
            Block block = state.getBlock();

            if (block == ModBlocks.FROZEN_DIRT.get()
                    || block == ModBlocks.FROZEN_SAND.get()
                    || block == ModBlocks.FROZEN_LOG.get()
                    || block == ModBlocks.FROZEN_LEAVES.get()
                    || block == ModBlocks.FROZEN_OBSIDIAN.get()) {
                return Type.COOLING;
            }

            return Type.NONE;
        }
    }
}
