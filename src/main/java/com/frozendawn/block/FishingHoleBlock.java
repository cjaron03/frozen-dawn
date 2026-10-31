package com.frozendawn.block;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.world.TemperatureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A hole drilled through thick ice with the Ice Auger. It reports a still water
 * source so a vanilla fishing rod bobs in it, but it is not a fluid block and
 * never flows. The water below is abstract: world freezing is unchanged.
 *
 * <p>An unused hole skins over in {@link #FREEZE} steps and turns back into ice.
 * How fast depends on the temperature at the hole, so phase 6 cold closes it in
 * seconds while a heated hut keeps it open. A bobber in the hole keeps it open.
 */
public class FishingHoleBlock extends Block {

    public static final IntegerProperty FREEZE = IntegerProperty.create("freeze", 0, 3);

    /** How often a hole above freezing rechecks its temperature. */
    private static final int WARM_RECHECK_TICKS = 200;
    private static final int MIN_CLOSE_TICKS = 100;
    private static final int MAX_CLOSE_TICKS = 12000;

    public FishingHoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FREEZE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FREEZE);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.WATER;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, stepTicks(level, pos));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (hasBobber(level, pos)) {
            if (state.getValue(FREEZE) != 0) {
                level.setBlock(pos, state.setValue(FREEZE, 0), Block.UPDATE_CLIENTS);
            }
            level.scheduleTick(pos, this, stepTicks(level, pos));
            return;
        }

        int close = closeTicks(level, pos);
        if (close < 0) {
            level.scheduleTick(pos, this, WARM_RECHECK_TICKS);
            return;
        }

        int step = Math.max(1, close / 4);
        int freeze = state.getValue(FREEZE);
        if (freeze >= 3) {
            level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
            level.playSound(null, pos, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.6f, 1.4f);
            return;
        }
        level.setBlock(pos, state.setValue(FREEZE, freeze + 1), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, step);
    }

    /** Turns the hole back into ice, for the auger plugging it. */
    public static void plug(Level level, BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
        level.playSound(null, pos, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    private static boolean hasBobber(Level level, BlockPos pos) {
        return !level.getEntitiesOfClass(FishingHook.class, new AABB(pos).inflate(0.25, 0.5, 0.25)).isEmpty();
    }

    private static int stepTicks(Level level, BlockPos pos) {
        int close = closeTicks(level, pos);
        return close < 0 ? WARM_RECHECK_TICKS : Math.max(1, close / 4);
    }

    /**
     * Ticks for an unused hole to close completely, or -1 above freezing.
     * About ten minutes near 0C, two minutes at -10C, five seconds at -200C.
     */
    static int closeTicks(Level level, BlockPos pos) {
        float temperature = temperatureAt(level, pos);
        return closeTicksFor(temperature);
    }

    static int closeTicksFor(float temperature) {
        if (temperature > 0f) return -1;
        int ticks = Math.round(24000f / (1f + Math.abs(temperature)));
        return Mth.clamp(ticks, MIN_CLOSE_TICKS, MAX_CLOSE_TICKS);
    }

    private static float temperatureAt(Level level, BlockPos pos) {
        if (level.getServer() == null) return 0f;
        ApocalypseState state = ApocalypseState.get(level.getServer());
        return TemperatureManager.getTemperatureAt(level, pos, state.getCurrentDay(), state.getTotalDays());
    }
}
