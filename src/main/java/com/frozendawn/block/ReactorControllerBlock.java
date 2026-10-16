package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** CFU-7 Controller. FACING points away from the core. ALARM: 0 offline, 1 running, 2 amber, 3 red.
 * A redstone signal trips a SCRAM; comparator output follows core temperature. */
public final class ReactorControllerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty ALARM = IntegerProperty.create("alarm", 0, 3);
    public ReactorControllerBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ALARM, 0)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, ALARM); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }
    @Override protected boolean hasAnalogOutputSignal(BlockState s) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState s, Level l, BlockPos p) {
        return l.getBlockEntity(p) instanceof ReactorControllerBlockEntity c ? c.comparator() : 0;
    }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (player.getMainHandItem().is(ModItems.CONDUIT_BINDER) || player.getOffhandItem().is(ModItems.CONDUIT_BINDER)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && l.getBlockEntity(p) instanceof ReactorControllerBlockEntity c) server.openMenu(c, buf -> buf.writeBlockPos(p));
        return InteractionResult.sidedSuccess(l.isClientSide());
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        if (!s.is(next.getBlock()) && l.getBlockEntity(p) instanceof ReactorControllerBlockEntity c) Containers.dropContents(l, p, c.items());
        super.onRemove(s, l, p, next, moving);
    }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new ReactorControllerBlockEntity(p, s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (t != ModBlockEntities.REACTOR_CONTROLLER.get()) return null;
        return l.isClientSide() ? (w, pos, state, be) -> { com.frozendawn.client.ReactorHumSound.tick((ReactorControllerBlockEntity) be); com.frozendawn.client.ReactorAlarmSound.tick((ReactorControllerBlockEntity) be); }
                : (w, pos, state, be) -> ((ReactorControllerBlockEntity) be).serverTick();
    }
}
