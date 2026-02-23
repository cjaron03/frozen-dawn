package com.frozendawn.world;

import com.frozendawn.config.FrozenDawnConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Accumulates snow on sky-visible surfaces based on apocalypse phase.
 *
 * Air (sky access) → Snow Layer 1 → 2 → ... → 8 → Snow Block
 * Rate: 1 layer per 200 ticks (phase 2), 100 (phase 3), 50 (phase 4+)
 */
public final class SnowAccumulator {

    private SnowAccumulator() {}

    private static final int CHECKS_PER_PLAYER = 32;
    private static final int RADIUS = 64;

    public static void tick(ServerLevel level, int phase) {
        if (phase < 2) return;

        // Rate control: only accumulate every N ticks
        int baseInterval = switch (phase) {
            case 2 -> 200;
            case 3 -> 100;
            default -> 50; // phase 4+
        };
        double rate = FrozenDawnConfig.SNOW_ACCUMULATION_RATE.get();
        int interval = rate > 0 ? Math.max(1, (int) (baseInterval / rate)) : baseInterval;

        if (level.getServer().getTickCount() % interval != 0) return;

        RandomSource random = level.getRandom();

        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();

            for (int i = 0; i < CHECKS_PER_PLAYER; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;

                // Find the surface: first air/non-blocking block above ground
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos snowPos = new BlockPos(x, surfaceY, z);

                if (!level.isLoaded(snowPos)) continue;
                if (!level.canSeeSky(snowPos)) continue;

                BlockPos belowPos = snowPos.below();
                BlockState below = level.getBlockState(belowPos);
                BlockState at = level.getBlockState(snowPos);

                // Increment existing snow layer
                if (below.is(Blocks.SNOW)) {
                    int layers = below.getValue(SnowLayerBlock.LAYERS);
                    if (layers < 8) {
                        level.setBlock(belowPos, below.setValue(SnowLayerBlock.LAYERS, layers + 1), 3);
                    } else {
                        // Full 8 layers → snow block
                        level.setBlock(belowPos, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                    }
                    continue;
                }

                // Place new snow layer on solid surface
                if (at.isAir() && below.isFaceSturdy(level, belowPos, Direction.UP)) {
                    level.setBlock(snowPos, Blocks.SNOW.defaultBlockState()
                            .setValue(SnowLayerBlock.LAYERS, 1), 3);
                }
            }
        }
    }
}
