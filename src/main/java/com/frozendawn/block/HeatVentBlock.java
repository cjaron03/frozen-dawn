package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

/** The shutter exposes a radiator, never a hole through the pressure wall. FACING points outdoors. */
public final class HeatVentBlock extends Block implements EntityBlock {
    public static final IntegerProperty INDICATOR=IntegerProperty.create("indicator",0,2);
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    public static final BooleanProperty OPEN=BlockStateProperties.OPEN, POWERED=BlockStateProperties.POWERED;
    public HeatVentBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(OPEN,false).setValue(POWERED,false).setValue(INDICATOR,0));}
    public static boolean active(BlockState s){return s.getValue(OPEN)||s.getValue(POWERED);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,OPEN,POWERED,INDICATOR);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getNearestLookingDirection()).setValue(POWERED,c.getLevel().hasNeighborSignal(c.getClickedPos()));}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,Block block,BlockPos from,boolean moving){
        if(!l.isClientSide()){boolean powered=l.hasNeighborSignal(p);if(powered!=s.getValue(POWERED))l.setBlock(p,s.setValue(POWERED,powered),3);}
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        if(!l.isClientSide()){
            var next=s.cycle(OPEN);l.setBlock(p,next,3);
            l.playSound(null,p,SoundEvents.IRON_TRAPDOOR_OPEN,SoundSource.BLOCKS,.5f,active(next)?1.1f:.8f);
            player.displayClientMessage(Component.translatable(next.getValue(POWERED)?"message.frozendawn.heat_vent.powered":active(next)?"message.frozendawn.heat_vent.open":"message.frozendawn.heat_vent.closed"),true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide());
    }
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new HeatVentBlockEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return !l.isClientSide()&&t==ModBlockEntities.HEAT_VENT.get()?(world,pos,state,be)->((HeatVentBlockEntity)be).serverTick():null;}
}
