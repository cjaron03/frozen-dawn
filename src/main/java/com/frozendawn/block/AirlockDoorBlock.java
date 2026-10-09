package com.frozendawn.block;

import com.frozendawn.airlock.AirlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

public final class AirlockDoorBlock extends DoorBlock {
    public static final com.mojang.serialization.MapCodec<AirlockDoorBlock> CODEC=simpleCodec(AirlockDoorBlock::new);
    @Override public com.mojang.serialization.MapCodec<? extends DoorBlock> codec() {return CODEC;}
    public AirlockDoorBlock(Properties properties) { super(BlockSetType.IRON,properties); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state=super.getStateForPlacement(context);
        return state==null?null:state.setValue(OPEN,false).setValue(POWERED,false);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(level instanceof ServerLevel server) {
            if(!state.getValue(OPEN)&&!AirlockManager.canOpen(server,pos))AirlockManager.refuse(server,pos,player);
            else setOpen(player,level,state,pos,!state.getValue(OPEN));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void setOpen(Entity entity,Level level,BlockState state,BlockPos pos,boolean open) {
        if(open&&level instanceof ServerLevel server&&!AirlockManager.canOpen(server,pos))return;
        super.setOpen(entity,level,state,pos,open);
    }
}
