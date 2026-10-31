package com.frozendawn.block;

import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import com.frozendawn.init.ModBlockEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** The Hyperion's brain and charge store; its window faces outward. */
public final class HyperionControllerBlock extends HyperionFacingPartBlock implements EntityBlock {
    /** Lit during a quench warning, on the Controller and every Port. */
    public static final BooleanProperty ALERT = BooleanProperty.create("alert");
    public HyperionControllerBlock(Properties p) { super(p.lightLevel(s -> s.getValue(ALERT) ? 6 : 0)); registerDefaultState(defaultBlockState().setValue(ALERT, false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(ALERT); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new HyperionControllerBlockEntity(p, s); }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (player.getMainHandItem().is(ModItems.CONDUIT_BINDER) || player.getOffhandItem().is(ModItems.CONDUIT_BINDER)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && l.getBlockEntity(p) instanceof HyperionControllerBlockEntity c) server.openMenu(c, buf -> buf.writeBlockPos(p));
        return InteractionResult.sidedSuccess(l.isClientSide());
    }
    /** Breaking a charged Controller vents before its store is gone. */
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level instanceof ServerLevel l && l.getBlockEntity(pos) instanceof HyperionControllerBlockEntity c && c.stored() > 0) c.broken(l);
        super.onRemove(state, level, pos, next, moved);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return !l.isClientSide() && t == ModBlockEntities.HYPERION_CONTROLLER.get()
                ? (w, pos, state, be) -> { if (w.getGameTime() % 20 == 0) ((HyperionControllerBlockEntity) be).second((ServerLevel) w); } : null;
    }
}
