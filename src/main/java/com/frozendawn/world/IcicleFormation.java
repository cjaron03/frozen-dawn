package com.frozendawn.world;

import com.frozendawn.init.ModBlocks;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Grows hanging icicles under frozen overhangs and snow-heavy ledges.
 */
public final class IcicleFormation {

    private static final int RADIUS = 56;

    private IcicleFormation() {}

    public static void tick(ServerLevel level, int phase, float progress) {
        if (phase < 4) return;
        if (PhaseManager.isPhase6MidOrLater(phase, progress)) return;

        int interval = switch (phase) {
            case 4 -> 80;
            case 5 -> 20;
            default -> 40;
        };
        if (level.getGameTime() % interval != 0) return;

        int checksPerPlayer = switch (phase) {
            case 4 -> 8;
            case 5 -> 18;
            default -> 10;
        };

        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();

            for (int i = 0; i < checksPerPlayer; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;

                BlockPos anchor = findIcicleAnchor(level, x, z, mutable);
                if (anchor == null) continue;

                BlockPos iciclePos = anchor.below();
                BlockState icicleState = level.getBlockState(iciclePos);

                if (icicleState.isAir()) {
                    level.setBlock(iciclePos, ModBlocks.ICICLE.get().defaultBlockState(), 3);
                    continue;
                }

                if (icicleState.is(ModBlocks.ICICLE.get())
                        && icicleState.getValue(com.frozendawn.block.IcicleBlock.AGE) < 3
                        && random.nextFloat() < 0.45f) {
                    level.setBlock(iciclePos, icicleState.setValue(
                            com.frozendawn.block.IcicleBlock.AGE,
                            icicleState.getValue(com.frozendawn.block.IcicleBlock.AGE) + 1), 3);
                }
            }
        }
    }

    private static BlockPos findIcicleAnchor(ServerLevel level, int x, int z, BlockPos.MutableBlockPos mutable) {
        int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int floorY = Math.max(level.getMinBuildHeight(), topY - 24);

        for (int y = topY; y >= floorY; y--) {
            mutable.set(x, y, z);
            if (!level.isLoaded(mutable)) return null;

            BlockState state = level.getBlockState(mutable);
            if (state.isAir()) {
                continue;
            }

            if (isIcicleSupport(state) && level.getBlockState(mutable.below()).isAir()) {
                return mutable.immutable();
            }

            return null;
        }

        return null;
    }

    private static boolean isIcicleSupport(BlockState state) {
        return state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE)
                || state.is(ModBlocks.FROZEN_DIRT.get())
                || state.is(ModBlocks.FROZEN_SAND.get())
                || state.is(ModBlocks.FROZEN_LOG.get())
                || state.is(ModBlocks.FROZEN_LEAVES.get())
                || state.is(ModBlocks.FROZEN_OBSIDIAN.get());
    }
}
