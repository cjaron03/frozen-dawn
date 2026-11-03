package com.frozendawn.block;

import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/**
 * ORSA Uplink Dish: needs open sky directly above and a console within eight blocks. The block is a 1x1 base and
 * mast; its block entity renders the two-block reflector. MODE is written by the linked console: 0 idle,
 * 1 listening (slow sweep), 2 transmitting (spin), 3 boosted (fast spin). Gameplay checks stay in the console.
 */
public final class UplinkDishBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty MODE=IntegerProperty.create("mode",0,3);
    private static final VoxelShape SHAPE=Shapes.or(box(3,0,3,13,1,13),box(6,1,6,10,16,10));
    public UplinkDishBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(MODE,0));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,MODE);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new UplinkDishBlockEntity(p,s);}
}
