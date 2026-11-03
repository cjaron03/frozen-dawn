package com.frozendawn.block;

import com.frozendawn.radiation.RadiationSourceData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Settled fallout: a thin layer of grey-black radioactive ash and debris. It is the contamination itself:
 * a weak radiation source that contaminates anyone without a sealed suit who walks through it.
 * Shovel it up into Radioactive Ash to clean an area. */
public final class FalloutBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);
    /** Meltdown fallout: about five times a minor spill, and dense enough to hang in the air. */
    public static final BooleanProperty SEVERE = BooleanProperty.create("severe");
    public FalloutBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(SEVERE, false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(SEVERE); }
    public static boolean at(Level level, BlockPos pos) { return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof FalloutBlock; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }
    @Override protected boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
    @Override protected BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor level, BlockPos pos, BlockPos np) {
        return d == Direction.DOWN && !canSurvive(s, level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : s;
    }
    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (level instanceof ServerLevel l && (!old.is(this) || old.getValue(SEVERE) != s.getValue(SEVERE)))
            RadiationSourceData.get(l).put(pos, s.getValue(SEVERE) ? RadiationSourceData.Kind.FALLOUT_SEVERE : RadiationSourceData.Kind.FALLOUT);
    }
    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (level instanceof ServerLevel l && !next.is(this)) RadiationSourceData.get(l).remove(pos);
        super.onRemove(s, level, pos, next, moving);
    }
    @Override public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(s.getValue(SEVERE) ? 2 : 6) == 0) level.addParticle(random.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH,
                pos.getX() + random.nextDouble(), pos.getY() + 0.1 + random.nextDouble() * 1.5, pos.getZ() + random.nextDouble(), 0, 0, 0);
    }
}
