package com.frozendawn.block;

import com.frozendawn.bloom.BloomBand;
import com.frozendawn.bloom.BloomGrowthPolicy;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModParticles;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public final class BloomMassBlock extends Block {
    public static final EnumProperty<BloomBand> BAND = EnumProperty.create("band", BloomBand.class);

    public BloomMassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BAND, BloomBand.FRONTIER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BAND);
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos,
                                   ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        RandomSource random = level.random;
        int count = BloomGrowthPolicy.spentLatticeDrops(
                state.getValue(BAND), random.nextFloat());
        if (count > 0) {
            popResource(level, pos, new ItemStack(ModItems.SPENT_LATTICE.get(), count));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide() && !newState.is(this) && level instanceof ServerLevel server) {
            com.frozendawn.bloom.BloomGrowthManager.reactivateAround(server, pos);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(18) != 0) {
            return;
        }
        Direction direction = Direction.getRandom(random);
        BlockPos adjacent = pos.relative(direction);
        if (!com.frozendawn.bloom.BloomGrowthManager.isBloomState(
                level.getBlockState(adjacent))) {
            return;
        }
        double sx = pos.getX() + 0.5D;
        double sy = pos.getY() + 0.5D;
        double sz = pos.getZ() + 0.5D;
        double dx = direction.getStepX() * 0.16D;
        double dy = direction.getStepY() * 0.16D;
        double dz = direction.getStepZ() * 0.16D;
        for (int step = 0; step < 4; step++) {
            level.addParticle(ModParticles.BLOOM_SPORE_ROOTING.get(),
                    sx + dx * step, sy + dy * step, sz + dz * step,
                    dx * 0.025D, dy * 0.025D, dz * 0.025D);
        }
    }
}
