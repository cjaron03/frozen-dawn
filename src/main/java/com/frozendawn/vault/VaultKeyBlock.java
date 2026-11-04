package com.frozendawn.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** One of the drill hall's two keys. Which one it is finds the recorder. As with a fuse, a client repeats a held use every
 * four ticks, so a hold needs nothing from the client. */
public final class VaultKeyBlock extends Block {
    public static final IntegerProperty KEY = IntegerProperty.create("key", 0, TwoPersonRule.KEYS - 1);
    public static final BooleanProperty HELD = BooleanProperty.create("held");

    public VaultKeyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KEY, 0).setValue(HELD, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(KEY, HELD); }
    public static int light(BlockState state) { return state.getValue(HELD) ? 9 : 4; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int key = state.getValue(KEY);
        var recorder = pos.subtract(TerminusVaultBuilder.KEYS[key]).offset(TerminusVaultBuilder.RECORDER);
        if (player instanceof ServerPlayer holder && level.getBlockEntity(recorder) instanceof VaultRecorderBlockEntity be) be.holdKey(holder, key);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
