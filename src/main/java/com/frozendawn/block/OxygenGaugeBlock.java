package com.frozendawn.block;
import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
public final class OxygenGaugeBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public OxygenGaugeBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){var face=c.getClickedFace();return defaultBlockState().setValue(FACING,face.getAxis().isHorizontal()?face:c.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return switch(s.getValue(FACING)){
        case NORTH->box(2,2,13,14,14,16);case SOUTH->box(2,2,0,14,14,3);case EAST->box(0,2,2,3,14,14);case WEST->box(13,2,2,16,14,14);default->Shapes.empty();};}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new OxygenGaugeBlockEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return !l.isClientSide()&&t==ModBlockEntities.OXYGEN_GAUGE.get()?(world,pos,state,be)->((OxygenGaugeBlockEntity)be).serverTick():null;}
    @Override protected boolean hasAnalogOutputSignal(BlockState s){return true;}
    @Override protected int getAnalogOutputSignal(BlockState s,Level l,BlockPos p){return l.getBlockEntity(p) instanceof OxygenGaugeBlockEntity g?g.comparator():0;}
}
