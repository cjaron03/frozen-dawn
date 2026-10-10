package com.frozendawn.block;
import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
public final class ThermostatBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public ThermostatBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){var face=c.getClickedFace();return defaultBlockState().setValue(FACING,face.getAxis().isHorizontal()?face:c.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return switch(s.getValue(FACING)) {
        case NORTH -> box(3,3,13,13,13,16);case SOUTH -> box(3,3,0,13,13,3);
        case EAST -> box(0,3,3,3,13,13);case WEST -> box(13,3,3,16,13,13);default -> Shapes.empty();};}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new ThermostatBlockEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return !l.isClientSide()&&t==ModBlockEntities.THERMOSTAT.get()?(world,pos,state,be)->((ThermostatBlockEntity)be).serverTick():null;}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){if(player instanceof ServerPlayer server&&l.getBlockEntity(p) instanceof ThermostatBlockEntity thermostat)server.openMenu(thermostat,p);return InteractionResult.sidedSuccess(l.isClientSide());}
    @Override protected boolean hasAnalogOutputSignal(BlockState s){return true;}
    @Override protected int getAnalogOutputSignal(BlockState s,Level l,BlockPos p){return l.getBlockEntity(p) instanceof ThermostatBlockEntity thermostat?thermostat.comparator():0;}
}
