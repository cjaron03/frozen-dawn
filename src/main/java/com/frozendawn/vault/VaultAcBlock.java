package com.frozendawn.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One cell of the cold store's A/C, three blocks by two, standing out from the Morgue's north wall. Its dial sets all six, and the
 * setting shows as frost on the slats. Parts run from the top left as seen from the south. */
public final class VaultAcBlock extends Block {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 5);
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 12);

    public VaultAcBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, 0).setValue(VaultAcDialBlock.SETTING, VaultAcDialBlock.LOW));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(PART, VaultAcDialBlock.SETTING); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
}
