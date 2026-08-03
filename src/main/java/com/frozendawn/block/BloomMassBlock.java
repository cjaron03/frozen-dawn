package com.frozendawn.block;

import com.frozendawn.bloom.BloomBand;
import com.frozendawn.bloom.BloomGrowthPolicy;
import com.frozendawn.init.ModItems;
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
}
