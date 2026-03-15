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

                BlockPos groundPos = SurfaceColumnScanner.findSnowSupportBelowCover(
                        level, x, z, SurfaceColumnScanner.DEFAULT_MAX_SCAN_DEPTH);
                if (groundPos == null) continue;

                BlockPos baseSnowPos = groundPos.above();
                if (!isOpenToSnow(level, baseSnowPos)) {
                    continue;
                }

                mutable.set(baseSnowPos);
                int snowBlockDepth = 0;
                while (level.getBlockState(mutable).is(Blocks.SNOW_BLOCK)) {
                    snowBlockDepth++;
                    if (snowBlockDepth >= MAX_SNOW_BLOCK_DEPTH) {
                        break;
                    }
                    mutable.move(Direction.UP);
                }

                if (snowBlockDepth >= MAX_SNOW_BLOCK_DEPTH) {
                    continue;
                }

                BlockPos snowPos = mutable.immutable();
                BlockState at = level.getBlockState(snowPos);
                BlockFreezer.refreshStructuralStress(level, groundPos, phase, progress);

                if (at.is(Blocks.SNOW)) {
                    int layers = at.getValue(SnowLayerBlock.LAYERS);
                    int maxLayers = switch (phase) {
                        case 2, 3 -> 2;
                        case 4 -> 4;
                        default -> 7;
                    };
                    if (layers < maxLayers) {
                        level.setBlock(snowPos, at.setValue(SnowLayerBlock.LAYERS, layers + 1), 3);
                    } else if (phase >= 5) {
                        level.setBlock(snowPos, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                    }
                    continue;
                }

                BlockPos belowPos = snowPos.below();
                if (at.isAir() && canPlaceSnowOn(level, belowPos)) {
                    if (level.getBlockState(belowPos).is(Blocks.DIRT_PATH)) {
                        level.setBlock(belowPos, Blocks.DIRT.defaultBlockState(), 3);
                    }
                    level.setBlock(snowPos, Blocks.SNOW.defaultBlockState()
                            .setValue(SnowLayerBlock.LAYERS, 1), 3);
                }
            }
        }
    }

    private static boolean isOpenToSnow(ServerLevel level, BlockPos snowPos) {
        BlockPos.MutableBlockPos cursor = snowPos.mutable();
        while (true) {
            BlockState state = level.getBlockState(cursor);
            if (!state.is(Blocks.SNOW) && !state.is(Blocks.SNOW_BLOCK)) {
                break;
            }
            cursor.move(Direction.UP);
        }

        BlockPos exposurePos = cursor.immutable();
        if (level.canSeeSky(exposurePos)) {
            return true;
        }

        BlockState above = level.getBlockState(exposurePos);
        if (!above.isAir() && SurfaceColumnScanner.canSupportSnow(level, exposurePos, above)) {
            return false;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = snowPos.relative(direction);
            if (!level.getBlockState(neighbor).isAir()) {
                continue;
            }
            if (level.canSeeSky(neighbor)) {
                return true;
            }
        }

        return false;
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

        return SurfaceColumnScanner.canSupportSnow(level, belowPos, below);
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
