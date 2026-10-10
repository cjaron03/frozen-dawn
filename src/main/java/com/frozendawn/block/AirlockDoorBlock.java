package com.frozendawn.block;

import com.frozendawn.airlock.AirlockManager;
import com.frozendawn.init.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.block.Block;
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
        if(!(level instanceof ServerLevel server)||!state.is(this))return;
        var lower=AirlockManager.lower(pos,state);var current=level.getBlockState(lower);
        if(!current.is(this)||current.getValue(OPEN)==open||open&&!AirlockManager.canOpen(server,lower))return;
        if(!level.setBlock(lower,current.setValue(OPEN,open),10)||level.getBlockState(lower).getValue(OPEN)!=open)return;
        // The click is server-authoritative: include its actor in the broadcast, once.
        level.playSound(null,lower,open?ModSounds.AIRLOCK_DOOR_OPEN.get():ModSounds.AIRLOCK_DOOR_CLOSE.get(),SoundSource.BLOCKS,.8f,1f);
        level.gameEvent(entity,open?GameEvent.BLOCK_OPEN:GameEvent.BLOCK_CLOSE,lower);
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving) {
        if(!(level instanceof ServerLevel server)||block==this)return;
        var lower=AirlockManager.lower(pos,state);var current=level.getBlockState(lower);
        if(!current.is(this))return;
        boolean powered=level.hasNeighborSignal(lower)||level.hasNeighborSignal(lower.above());
        if(powered==current.getValue(POWERED))return;
        boolean open=powered&&AirlockManager.canOpen(server,lower);
        // Publish power and movement together: neighbor callbacks must not see stale power.
        if(!level.setBlock(lower,current.setValue(POWERED,powered).setValue(OPEN,open),10))return;
        boolean actual=level.getBlockState(lower).getValue(OPEN);
        if(actual==current.getValue(OPEN))return;
        level.playSound(null,lower,actual?ModSounds.AIRLOCK_DOOR_OPEN.get():ModSounds.AIRLOCK_DOOR_CLOSE.get(),SoundSource.BLOCKS,.8f,1f);
        level.gameEvent(null,actual?GameEvent.BLOCK_OPEN:GameEvent.BLOCK_CLOSE,lower);
    }
}
