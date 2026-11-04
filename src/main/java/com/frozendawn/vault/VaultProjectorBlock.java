package com.frozendawn.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The drill hall's projector, over the door on its west wall, two blocks wide in the drawing. Its lens is the state of the take. */
public final class VaultProjectorBlock extends Block {
    private static final VoxelShape SHAPE = box(0, 0, 0, 8, 16, 16);

    public VaultProjectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(VaultRecorderBlock.MODE, VaultRecorderBlock.Mode.IDLE));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(VaultRecorderBlock.MODE); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
}
