package com.frozendawn.world;

import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Handles Frozen Atmosphere deposit formation on the surface during phase 6 late.
 * Also handles block sublimation: removes deposits if temperature rises above -150C.
 *
 * Formation requires: phase 6, progress > 0.85, direct sky access, temp below -150C.
 */
public final class FrozenAtmosphereFormation {

    private FrozenAtmosphereFormation() {}

    private static final int FORMATION_RADIUS = 48;
    private static final int SUBLIMATION_RADIUS = 32;
    private static final float SUBLIMATION_TEMP = -150f;

    private static final int FORMATION_CHECKS = 16;
    private static final float FORMATION_CHANCE = 0.10f;

    private static final int SUBLIMATION_CHECKS = 8;

    public static void tick(ServerLevel level, int phase, float progress, int currentDay, int totalDays) {
        RandomSource random = level.getRandom();
        boolean canForm = phase >= 6 && progress >= 0.85f;

        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

            // Surface formation (phase 6 late only)
            if (canForm) {
                for (int i = 0; i < FORMATION_CHECKS; i++) {
                    int x = origin.getX() + random.nextInt(FORMATION_RADIUS * 2 + 1) - FORMATION_RADIUS;
                    int z = origin.getZ() + random.nextInt(FORMATION_RADIUS * 2 + 1) - FORMATION_RADIUS;
                    int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    mutable.set(x, surfaceY, z);
                    if (!level.isLoaded(mutable)) continue;

                    // Must have sky access
                    if (!level.canSeeSky(mutable)) continue;

                    // Scan down through snow/air to find solid ground
                    for (int dy = 0; dy <= 6; dy++) {
                        mutable.set(x, surfaceY - dy, z);
                        BlockState at = level.getBlockState(mutable);
                        if (at.isAir() || at.is(Blocks.SNOW) || at.is(Blocks.SNOW_BLOCK)) continue;

                        // Already a frozen atmosphere deposit here
                        if (at.is(ModBlocks.FROZEN_ATMOSPHERE.get())) break;

                        // Need a sturdy surface block below
                        if (!at.isFaceSturdy(level, mutable, Direction.UP)) break;

                        // Check the position above for placement
                        BlockPos placePos = mutable.above();
                        BlockState aboveState = level.getBlockState(placePos);
                        if (!aboveState.isAir() && !aboveState.is(Blocks.SNOW)) break;

                        float temp = TemperatureManager.getTemperatureAt(level, placePos, currentDay, totalDays);
                        if (temp > SUBLIMATION_TEMP) break;
                        if (random.nextFloat() >= FORMATION_CHANCE) break;

                        // Clear snow and place deposit
                        if (aboveState.is(Blocks.SNOW)) {
                            level.destroyBlock(placePos, false);
                        }
                        level.setBlock(placePos,
                                ModBlocks.FROZEN_ATMOSPHERE.get().defaultBlockState(), 3);
                        clearSnowAround(level, placePos, 2);
                        break;
                    }
                }

                // Snow clearing: keep existing deposits visible
                for (int i = 0; i < 4; i++) {
                    int x = origin.getX() + random.nextInt(SUBLIMATION_RADIUS * 2 + 1) - SUBLIMATION_RADIUS;
                    int z = origin.getZ() + random.nextInt(SUBLIMATION_RADIUS * 2 + 1) - SUBLIMATION_RADIUS;
                    int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    for (int dy = 0; dy <= 4; dy++) {
                        mutable.set(x, surfaceY - dy, z);
                        if (!level.isLoaded(mutable)) break;
                        BlockState state = level.getBlockState(mutable);
                        if (state.is(ModBlocks.FROZEN_ATMOSPHERE.get())) {
                            clearSnowAround(level, mutable.immutable(), 1);
                            break;
                        }
                        if (!state.isAir() && !state.is(Blocks.SNOW) && !state.is(Blocks.SNOW_BLOCK)) break;
                    }
                }
            }

            // Sublimation check: always runs — removes deposits if temperature is too high
            for (int i = 0; i < SUBLIMATION_CHECKS; i++) {
                int x = origin.getX() + random.nextInt(SUBLIMATION_RADIUS * 2 + 1) - SUBLIMATION_RADIUS;
                int z = origin.getZ() + random.nextInt(SUBLIMATION_RADIUS * 2 + 1) - SUBLIMATION_RADIUS;
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);

                for (int dy = 0; dy <= 6; dy++) {
                    mutable.set(x, surfaceY - dy, z);
                    if (!level.isLoaded(mutable)) break;

                    BlockState state = level.getBlockState(mutable);
                    if (!state.is(ModBlocks.FROZEN_ATMOSPHERE.get())) {
                        if (!state.isAir() && !state.is(Blocks.SNOW) && !state.is(Blocks.SNOW_BLOCK)) break;
                        continue;
                    }

                    float temp = TemperatureManager.getTemperatureAt(level, mutable, currentDay, totalDays);
                    if (temp > SUBLIMATION_TEMP) {
                        level.destroyBlock(mutable, false);
                    }
                    break;
                }
            }
        }
    }

    private static void clearSnowAround(ServerLevel level, BlockPos center, int radius) {
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    check.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState s = level.getBlockState(check);
                    if (s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK)) {
                        level.destroyBlock(check.immutable(), false);
                    }
                }
            }
        }
    }
}
