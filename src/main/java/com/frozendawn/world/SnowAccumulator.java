package com.frozendawn.world;

import com.frozendawn.block.AcheroniteCrystalBlock;
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
    /** Acheronite should protrude through the drift instead of riding the full snow cap. */
    private static final int MAX_ACHERONITE_SNOW_SUPPORT_DEPTH = 2;

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
                if (BlastPitWarmZoneRegistry.isInsideWarmZone(level, baseSnowPos)) {
                    clearColdDepositionAt(level, baseSnowPos);
                    continue;
                }
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

                if (at.is(ModBlocks.ACHERONITE_CRYSTAL.get())) {
                    accumulateOnAcheronite(level, snowPos, at);
                    continue;
                }

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

        // Keep snow from replacing a crystal directly, but allow it to build around them.
        if (below.is(ModBlocks.ACHERONITE_CRYSTAL.get())) return false;

        return SurfaceColumnScanner.canSupportSnow(level, belowPos, below);
    }

    private static void accumulateOnAcheronite(ServerLevel level, BlockPos crystalPos, BlockState crystalState) {
        BlockPos currentPos = crystalPos;
        BlockState currentState = crystalState;

        int currentDepth = AcheroniteCrystalBlock.getSnowSupportDepth(level, currentPos);
        int maxSupportDepth = currentState.getValue(AcheroniteCrystalBlock.AGE) >= 3
                ? MAX_SNOW_BLOCK_DEPTH
                : MAX_ACHERONITE_SNOW_SUPPORT_DEPTH;
        int targetDepth = Math.min(maxSupportDepth, getLocalSnowDepthForCrystal(level, currentPos));
        while (currentDepth > targetDepth) {
            BlockPos loweredPos = currentPos.below();
            BlockState loweredState = level.getBlockState(loweredPos);
            if (!loweredState.is(Blocks.SNOW_BLOCK)) {
                break;
            }

            level.setBlock(currentPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(loweredPos, currentState, 3);
            currentPos = loweredPos;
            currentState = level.getBlockState(currentPos);
            currentDepth--;
        }

        while (currentDepth < targetDepth) {
            BlockPos nextPos = currentPos.above();
            BlockState aboveState = level.getBlockState(nextPos);
            if (!aboveState.isAir() && !aboveState.is(Blocks.SNOW) && !aboveState.is(Blocks.SNOW_BLOCK)) {
                break;
            }

            if (!aboveState.isAir()) {
                level.destroyBlock(nextPos, false);
            }

            level.setBlock(currentPos, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
            BlockState liftedState = currentState.setValue(
                    AcheroniteCrystalBlock.BURIED,
                    currentState.getValue(AcheroniteCrystalBlock.AGE) < 3
            );
            level.setBlock(nextPos, liftedState, 3);
            currentPos = nextPos;
            currentState = level.getBlockState(currentPos);
            currentDepth++;
        }

        boolean shouldBeBuried = currentState.getValue(AcheroniteCrystalBlock.AGE) < 3
                && (currentDepth > 0 || AcheroniteCrystalBlock.hasSnowCover(level, currentPos));
        if (currentState.getValue(AcheroniteCrystalBlock.BURIED) != shouldBeBuried) {
            level.setBlock(currentPos, currentState.setValue(AcheroniteCrystalBlock.BURIED, shouldBeBuried), 3);
        }
    }

    static int getLocalSnowDepthForCrystal(ServerLevel level, BlockPos crystalPos) {
        int maxDepth = AcheroniteCrystalBlock.getSnowSupportDepth(level, crystalPos);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = crystalPos.relative(direction);
            maxDepth = Math.max(maxDepth, getSnowDepthAtColumn(level, neighborPos.getX(), neighborPos.getZ()));
        }
        return maxDepth;
    }

    private static int getSnowDepthAtColumn(ServerLevel level, int x, int z) {
        BlockPos supportPos = SurfaceColumnScanner.findSnowSupportBelowCover(
                level, x, z, SurfaceColumnScanner.DEFAULT_MAX_SCAN_DEPTH);
        if (supportPos == null) {
            return 0;
        }

        int depth = 0;
        BlockPos.MutableBlockPos cursor = supportPos.above().mutable();
        while (level.getBlockState(cursor).is(Blocks.SNOW_BLOCK) && depth < MAX_SNOW_BLOCK_DEPTH) {
            depth++;
            cursor.move(Direction.UP);
        }
        return depth;
    }

    private static void clearColdDepositionAt(ServerLevel level, BlockPos baseSnowPos) {
        BlockPos.MutableBlockPos cursor = baseSnowPos.mutable();
        for (int dy = 0; dy <= MAX_SNOW_BLOCK_DEPTH + 3; dy++) {
            BlockState state = level.getBlockState(cursor);
            if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
                    || state.is(Blocks.POWDER_SNOW) || state.is(ModBlocks.FROZEN_ATMOSPHERE.get())) {
                level.destroyBlock(cursor.immutable(), false);
                cursor.move(Direction.UP);
                continue;
            }
            if (!state.isAir()) {
                break;
            }
            cursor.move(Direction.UP);
        }
    }
}
