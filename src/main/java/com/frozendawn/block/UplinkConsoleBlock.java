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
import net.minecraft.world.phys.BlockHitResult;

/** ORSA Uplink Console: grid-powered transceiver. ACTIVE is 0 idle/unpowered, 1 listening, 2 transmitting. */
public final class UplinkConsoleBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty ACTIVE=IntegerProperty.create("active",0,2);
    public UplinkConsoleBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ACTIVE,0));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,ACTIVE);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new UplinkConsoleBlockEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return !l.isClientSide()&&t==ModBlockEntities.UPLINK_CONSOLE.get()?(world,pos,state,be)->((UplinkConsoleBlockEntity)be).serverTick():null;}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        if(player.getMainHandItem().is(ModItems.CONDUIT_BINDER)||player.getOffhandItem().is(ModItems.CONDUIT_BINDER))return InteractionResult.PASS;
        if(player instanceof ServerPlayer server&&l.getBlockEntity(p) instanceof UplinkConsoleBlockEntity console){console.deliverHeld(server);server.openMenu(console,buf->buf.writeBlockPos(p));}
        return InteractionResult.sidedSuccess(l.isClientSide());
    }
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moved){
        if(s.getBlock()!=next.getBlock()&&l.getBlockEntity(p) instanceof UplinkConsoleBlockEntity console){console.dropHeld();console.releaseDish();}
        super.onRemove(s,l,p,next,moved);
    }
}
