package com.frozendawn.block;

import com.frozendawn.power.PowerNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** ORSA Panel Light: 100 W, light level 15. No fuel, oxygen, heat or sensory emissions. */
public final class PanelLightBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final int DEMAND_WATTS = 100;
    public PanelLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide()) level.scheduleTick(pos, this, 20);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // A saved lit block must go dark even when no saved link survives or its chunks just reloaded.
        setPowered(level, pos, PowerNetworks.powered(level, pos));
        level.scheduleTick(pos, this, 20);
    }
    public static void setPowered(ServerLevel level, BlockPos pos, boolean powered) {
        if (!level.isLoaded(pos)) return;
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof PanelLightBlock && state.getValue(LIT) != powered)
            level.setBlock(pos, state.setValue(LIT, powered), 3);
    }
}
