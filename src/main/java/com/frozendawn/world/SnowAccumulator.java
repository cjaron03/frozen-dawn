package com.frozendawn.world;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.ModBlocks;
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
 * Snow layers sit AT the heightmap position (snow has noCollission).
 * Air → Snow Layer 1 → 2 → ... → 7 → Snow Block (phase 5 only).
 * Max snow block depth: 3 (player height).
 */
public final class SnowAccumulator {

    private SnowAccumulator() {}

    private static final int BASE_CHECKS_PER_PLAYER = 32;
    private static final int RADIUS = 64;
    /** Max snow block stacking depth (3 blocks = player height). */
    private static final int MAX_SNOW_BLOCK_DEPTH = 3;

    public static void tick(ServerLevel level, int phase, float progress) {
        if (phase < 2) return;

        // Phase 6 mid+: no more snow — atmosphere too thin for precipitation
        if (phase >= 6 && progress > 0.72f) return;

        int baseInterval = switch (phase) {
            case 2 -> 200;
            case 3 -> 60;
            case 4 -> 15;
            default -> 5; // phase 5+: every 5 ticks
        };
        double rate = FrozenDawnConfig.SNOW_ACCUMULATION_RATE.get();
        int interval = rate > 0 ? Math.max(1, (int) (baseInterval / rate)) : baseInterval;

        if (level.getServer().getTickCount() % interval != 0) return;

        int checksPerPlayer = switch (phase) {
            case 2 -> BASE_CHECKS_PER_PLAYER;
            case 3 -> BASE_CHECKS_PER_PLAYER * 2;    // 64
            case 4 -> BASE_CHECKS_PER_PLAYER * 4;    // 128
            default -> BASE_CHECKS_PER_PLAYER * 8;    // 256
        };

        RandomSource random = level.getRandom();

        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

            for (int i = 0; i < checksPerPlayer; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;

                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                mutable.set(x, surfaceY, z);

                if (!level.isLoaded(mutable)) continue;
                if (!level.canSeeSky(mutable)) continue;

                BlockPos snowPos = mutable.immutable();
                BlockState at = level.getBlockState(snowPos);

                // Increment existing snow layer — snow sits AT snowPos (not below)
                // because snow layers have noCollission and don't affect MOTION_BLOCKING heightmap
                if (at.is(Blocks.SNOW)) {
                    int layers = at.getValue(SnowLayerBlock.LAYERS);
                    int maxLayers = switch (phase) {
                        case 2, 3 -> 2;
                        case 4 -> 4;
                        default -> 7; // phase 5: grows to 7 then converts
                    };
                    if (layers < maxLayers) {
                        level.setBlock(snowPos, at.setValue(SnowLayerBlock.LAYERS, layers + 1), 3);
                    } else if (phase >= 5) {
                        // Convert to snow block, cap at MAX_SNOW_BLOCK_DEPTH
                        int snowDepth = countSnowBlocksBelow(level, snowPos);
                        if (snowDepth < MAX_SNOW_BLOCK_DEPTH) {
                            level.setBlock(snowPos, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                        }
                    }
                    continue;
                }

                // Place new snow layer on a suitable surface
                BlockPos belowPos = snowPos.below();
                if (at.isAir() && canPlaceSnowOn(level, belowPos)) {
                    // Dirt path reverts to dirt when covered (vanilla behavior)
                    if (level.getBlockState(belowPos).is(Blocks.DIRT_PATH)) {
                        level.setBlock(belowPos, Blocks.DIRT.defaultBlockState(), 3);
                    }
                    level.setBlock(snowPos, Blocks.SNOW.defaultBlockState()
                            .setValue(SnowLayerBlock.LAYERS, 1), 3);
                }
            }
        }
    }

    /** Count consecutive snow blocks below this position. */
    private static int countSnowBlocksBelow(ServerLevel level, BlockPos pos) {
        int depth = 0;
        BlockPos check = pos.below();
        while (depth < MAX_SNOW_BLOCK_DEPTH && level.getBlockState(check).is(Blocks.SNOW_BLOCK)) {
            depth++;
            check = check.below();
        }
        return depth;
    }

    /** Check if snow can be placed on the block at belowPos. */
    private static boolean canPlaceSnowOn(ServerLevel level, BlockPos belowPos) {
        BlockState below = level.getBlockState(belowPos);

        // Skip ice — snow breaks on it
        if (below.is(Blocks.ICE) || below.is(Blocks.PACKED_ICE)
                || below.is(Blocks.BLUE_ICE) || below.is(Blocks.FROSTED_ICE)) {
            return false;
        }

        // Don't bury acheronite crystals — check block below and nearby
        if (below.is(ModBlocks.ACHERONITE_CRYSTAL.get())) return false;
        if (hasCrystalNearby(level, belowPos, 2)) return false;

        // Dirt path has a lowered top face (15/16) so isFaceSturdy returns false,
        // but snow should still accumulate on it
        if (below.is(Blocks.DIRT_PATH)) return true;

        return below.isFaceSturdy(level, belowPos, Direction.UP);
    }

    /** Check if there's an acheronite crystal within the given horizontal radius. */
    private static boolean hasCrystalNearby(ServerLevel level, BlockPos pos, int radius) {
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    check.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.getBlockState(check).is(ModBlocks.ACHERONITE_CRYSTAL.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
