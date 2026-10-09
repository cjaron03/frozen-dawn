package com.frozendawn.block;

import com.frozendawn.airlock.*;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.item.O2TankItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

public final class AirlockControllerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty INDICATOR=IntegerProperty.create("indicator",0,2);
    public AirlockControllerBlock(Properties properties) {super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(INDICATOR,0));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) {b.add(FACING,INDICATOR);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState s,Rotation r) {return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m) {return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) {return new AirlockControllerBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return !level.isClientSide()&&type==ModBlockEntities.AIRLOCK_CONTROLLER.get()?(l,p,s,b)->((AirlockControllerBlockEntity)b).serverTick():null;
    }
    private static AirlockSavedState.Chamber chamber(ServerLevel level,BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AirlockControllerBlockEntity panel?AirlockManager.resolve(level,panel):null;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!(stack.getItem() instanceof O2TankItem))return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(level instanceof ServerLevel server) {
            var c=chamber(server,pos);int amount=c==null?0:AirlockManager.charge(server,c,stack);
            AirlockManager.notify(player,c==null?com.frozendawn.network.AirlockStatusPayload.Status.INVALID:com.frozendawn.network.AirlockStatusPayload.Status.CHARGED,c,amount);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(level instanceof ServerLevel server) {
            var c=chamber(server,pos);
            String result=c==null?"invalid":player.isShiftKeyDown()?"status":AirlockManager.start(server,c);
            AirlockManager.notify(player,com.frozendawn.network.AirlockStatusPayload.Status.valueOf(result.toUpperCase(java.util.Locale.ROOT)),c,0);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState after,boolean moved) {
        if(!state.is(after.getBlock())&&level instanceof ServerLevel server&&level.getBlockEntity(pos) instanceof AirlockControllerBlockEntity panel&&panel.assigned())
            AirlockManager.removePanel(server,pos,panel.chamberId());
        super.onRemove(state,level,pos,after,moved);
    }
}
