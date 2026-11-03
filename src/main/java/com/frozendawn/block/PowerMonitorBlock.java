package com.frozendawn.block;

import com.frozendawn.init.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class PowerMonitorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty STATUS=IntegerProperty.create("status",0,4);
    public PowerMonitorBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(STATUS,0));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,STATUS);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        var face=c.getClickedFace();if(!face.getAxis().isHorizontal())return null;
        var state=defaultBlockState().setValue(FACING,face);return state.canSurvive(c.getLevel(),c.getClickedPos())?state:null;
    }
    @Override protected boolean canSurvive(BlockState s,LevelReader l,BlockPos p){var face=s.getValue(FACING);var wall=p.relative(face.getOpposite());return l.getBlockState(wall).isFaceSturdy(l,wall,face);}
    @Override protected BlockState updateShape(BlockState s,Direction d,BlockState neighbor,LevelAccessor l,BlockPos p,BlockPos np){return d==s.getValue(FACING).getOpposite()&&!canSurvive(s,l,p)?Blocks.AIR.defaultBlockState():super.updateShape(s,d,neighbor,l,p,np);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return switch(s.getValue(FACING)){case NORTH->box(2,3,13,14,13,16);case SOUTH->box(2,3,0,14,13,3);case EAST->box(0,3,2,3,13,14);default->box(13,3,2,16,13,14);};}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new PowerMonitorBlockEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return !l.isClientSide()&&t==ModBlockEntities.POWER_MONITOR.get()?(world,pos,state,be)->((PowerMonitorBlockEntity)be).serverTick():null;}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        if(player.getMainHandItem().is(ModItems.CONDUIT_BINDER)||player.getOffhandItem().is(ModItems.CONDUIT_BINDER))return InteractionResult.PASS;
        if(player instanceof ServerPlayer server&&l.getBlockEntity(p) instanceof PowerMonitorBlockEntity monitor)server.openMenu(monitor,b->b.writeBlockPos(p));
        return InteractionResult.sidedSuccess(l.isClientSide());
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState s){return true;}
    @Override protected int getAnalogOutputSignal(BlockState s,Level l,BlockPos p){return l.getBlockEntity(p) instanceof PowerMonitorBlockEntity m?m.comparator():0;}
}
