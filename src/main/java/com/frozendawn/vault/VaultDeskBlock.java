package com.frozendawn.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** The steel desk in the vault, with the manifest and the name review lying on it until someone picks them up. */
public final class VaultDeskBlock extends Block {
    public static final BooleanProperty PAPERS = BooleanProperty.create("papers");

    public VaultDeskBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PAPERS, true));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(PAPERS); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(PAPERS)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(PAPERS, false), 3);
            for (var paper : VaultDocuments.onTheDesk()) if (!player.getInventory().add(paper)) player.drop(paper, false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
