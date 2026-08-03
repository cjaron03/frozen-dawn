package com.frozendawn.block;

import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Acheronite Crystal with 4 growth stages (AGE 0-3).
 * Forms on frozen substrates in phase 5+ at temperatures below -80C.
 * Only stage 3 (full cluster) drops shards when mined.
 * Not player-placeable (no BlockItem registered).
 */
public class AcheroniteCrystalBlock extends Block {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final BooleanProperty BURIED = BooleanProperty.create("buried");
    public static final BooleanProperty DARK = BooleanProperty.create("dark");

    private static final VoxelShape SHAPE_0 = Block.box(5, 0, 5, 11, 6, 11);   // small bud
    private static final VoxelShape SHAPE_1 = Block.box(4, 0, 4, 12, 10, 12);  // medium bud
    private static final VoxelShape SHAPE_2 = Block.box(3, 0, 3, 13, 14, 13);  // large bud
    private static final VoxelShape SHAPE_3 = Block.box(3, 0, 3, 13, 15, 13);  // full cluster
    private static final VoxelShape SHAPE_BURIED_0 = Block.box(1, 0, 1, 15, 13, 15);
    private static final VoxelShape SHAPE_BURIED_1 = Block.box(1, 0, 1, 15, 14, 15);
    private static final VoxelShape SHAPE_BURIED_2 = Block.box(1, 0, 1, 15, 15, 15);
    private static final VoxelShape SHAPE_BURIED_3 = Block.box(1, 0, 1, 15, 16, 15);

    public AcheroniteCrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AGE, 0)
                .setValue(BURIED, false)
                .setValue(DARK, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, BURIED, DARK);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(BURIED)) {
            return switch (state.getValue(AGE)) {
                case 0 -> SHAPE_BURIED_0;
                case 1 -> SHAPE_BURIED_1;
                case 2 -> SHAPE_BURIED_2;
                default -> SHAPE_BURIED_3;
            };
        }

        return switch (state.getValue(AGE)) {
            case 0 -> SHAPE_0;
            case 1 -> SHAPE_1;
            case 2 -> SHAPE_2;
            default -> SHAPE_3;
        };
    }

    @Override
    public boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        return belowState.isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(
            BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(AGE) >= 3) {
            if (state.getValue(BURIED)) {
                state = state.setValue(BURIED, false);
            }
            return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        }

        boolean snowCovered = hasSnowCover(level, pos);
        if (state.getValue(BURIED) != snowCovered) {
            state = state.setValue(BURIED, snowCovered);
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(DARK)) {
            return;
        }
        int nearbyCrystals = countNearbyCrystals(level, pos, 4, 7);
        if (state.getValue(BURIED)) {
            int buriedDivisor = nearbyCrystals >= 6 ? 5 : nearbyCrystals >= 3 ? 3 : 2;
            if (random.nextInt(buriedDivisor) != 0) {
                return;
            }

            int supportDepth = getSnowSupportDepth(level, pos);
            double x = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.40D;
            double y = pos.getY() + 0.42D + Math.min(0.58D, supportDepth * 0.16D) + (random.nextDouble() * 0.18D);
            double z = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.40D;
            level.addParticle(ParticleTypes.SCULK_SOUL, x, y, z, 0.0D, 0.018D, 0.0D);
            if (random.nextBoolean()) {
                level.addParticle(ParticleTypes.SNOWFLAKE, x, y + 0.08D, z, 0.0D, 0.01D, 0.0D);
            }
            return;
        }

        if (state.getValue(AGE) == 3) {
            int exposedDivisor = nearbyCrystals >= 6 ? 4 : nearbyCrystals >= 3 ? 3 : 2;
            if (random.nextInt(exposedDivisor) != 0) {
                return;
            }
            double x = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.48D;
            double y = pos.getY() + 0.82D + random.nextDouble() * 0.52D;
            double z = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.48D;
            level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0D, 0.02D, 0.0D);
            level.addParticle(ParticleTypes.SCULK_SOUL, x, y - 0.18D, z, 0.0D, 0.012D, 0.0D);
            if (random.nextBoolean()) {
                level.addParticle(ParticleTypes.SNOWFLAKE, x, y + 0.04D, z, 0.0D, 0.01D, 0.0D);
            }
        }
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        ItemStack held = player.getMainHandItem();
        if (state.getValue(BURIED)) {
            return held.getItem() instanceof ShovelItem ? 0.18F : 0.03F;
        }

        if (!(held.getItem() instanceof PickaxeItem)) {
            return 0.015F;
        }

        return super.getDestroyProgress(state, player, level, pos);
    }

    public static boolean hasSnowCover(BlockGetter level, BlockPos pos) {
        if (getSnowSupportDepth(level, pos) > 0) {
            return true;
        }

        if (isSnow(level.getBlockState(pos.above()))) {
            return true;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos sidePos = pos.relative(direction);
            if (isSnow(level.getBlockState(sidePos)) || isSnow(level.getBlockState(sidePos.above()))) {
                return true;
            }
        }

        return false;
    }

    public static int getSnowSupportDepth(BlockGetter level, BlockPos pos) {
        int depth = 0;
        BlockPos.MutableBlockPos cursor = pos.mutable().move(Direction.DOWN);
        while (level.getBlockState(cursor).is(Blocks.SNOW_BLOCK)) {
            depth++;
            cursor.move(Direction.DOWN);
        }
        return depth;
    }

    private static int countNearbyCrystals(Level level, BlockPos pos, int radius, int cap) {
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!level.getBlockState(cursor).is(ModBlocks.ACHERONITE_CRYSTAL.get())) {
                        continue;
                    }
                    count++;
                    if (count >= cap) {
                        return count;
                    }
                }
            }
        }
        return count;
    }

    private static boolean isSnow(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
    }
}
