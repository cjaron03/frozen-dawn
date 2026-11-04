package com.frozendawn.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** One of the six fuses on the lower floor. Its slot is where it stands, which finds Argus; its glyph shuffles. A client
 * repeats a held use every four ticks, so a hold needs nothing from the client. */
public final class VaultFuseBlock extends VaultBoardBlock {
    public static final IntegerProperty SLOT = IntegerProperty.create("slot", 0, ArgusFight.GLYPHS - 1);

    public VaultFuseBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SLOT, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SLOT);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int slot = state.getValue(SLOT);
        var argus = pos.subtract(TerminusVaultBuilder.FUSES[slot]).offset(TerminusVaultBuilder.ARGUS);
        if (player instanceof ServerPlayer holder && level.getBlockEntity(argus) instanceof VaultArgusBlockEntity be) be.holdFuse(holder, slot);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
