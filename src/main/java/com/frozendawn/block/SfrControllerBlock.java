package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.frozendawn.init.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** SFR-1 Controller. FACING points away from the torus center, four cells behind it. */
public final class SfrControllerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public SfrControllerBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (player.getMainHandItem().is(ModItems.CONDUIT_BINDER) || player.getOffhandItem().is(ModItems.CONDUIT_BINDER)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && l.getBlockEntity(p) instanceof SfrControllerBlockEntity c) {
            var level = server.serverLevel();
            // A broken torus shows its ghost outline instead of the screen; sneaking opens the screen anyway.
            if (!player.isShiftKeyDown() && !SfrMultiblock.validate(level, p, s.getValue(FACING)).valid()) {
                var first = c.showGhost(level);
                server.displayClientMessage(first.map(w -> w.expected() == null
                        ? Component.translatable("message.frozendawn.sfr.clear_cell", w.pos().getX(), w.pos().getY(), w.pos().getZ())
                        : Component.translatable("message.frozendawn.sfr.wrong_cell", w.pos().getX(), w.pos().getY(), w.pos().getZ(), w.expected().getName()))
                        .orElse(Component.translatable("message.frozendawn.sfr.missing_ports")), true);
            } else server.openMenu(c, buf -> buf.writeBlockPos(p));
        }
        return InteractionResult.sidedSuccess(l.isClientSide());
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        if (!s.is(next.getBlock()) && l.getBlockEntity(p) instanceof SfrControllerBlockEntity c) Containers.dropContents(l, p, c.items());
        super.onRemove(s, l, p, next, moving);
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState s) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState s, Level l, BlockPos p) { return l.getBlockEntity(p) instanceof SfrControllerBlockEntity c ? c.comparator() : 0; }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new SfrControllerBlockEntity(p, s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return !l.isClientSide() && t == ModBlockEntities.SFR_CONTROLLER.get() ? (w, pos, state, be) -> ((SfrControllerBlockEntity) be).serverTick() : null;
    }
}
