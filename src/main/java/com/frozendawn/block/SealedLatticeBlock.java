package com.frozendawn.block;

import com.frozendawn.bloom.BloomGrowthManager;
import com.frozendawn.data.BloomSavedData;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public final class SealedLatticeBlock extends Block {
    public static final IntegerProperty WEAR = IntegerProperty.create("wear", 0, 3);

    public SealedLatticeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WEAR, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WEAR);
    }

    @Override
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level,
                           BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, 20);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BloomGrowthManager.tickSealedLattice(level, pos, state);
        if (level.getBlockState(pos).is(this)) {
            level.scheduleTick(pos, this, 20);
        }
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos,
                                   ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        int wear = state.getValue(WEAR);
        if (wear == 0) {
            popResource(level, pos, new ItemStack(ModItems.SEALED_LATTICE.get()));
        } else {
            popResource(level, pos, new ItemStack(ModItems.SPENT_LATTICE.get(), 4 - wear));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide() && !newState.is(this) && level instanceof ServerLevel server) {
            BloomSavedData.get(server.getServer()).removeSealedContact(pos);
            BloomGrowthManager.reactivateAround(server, pos);
        }
    }
}
