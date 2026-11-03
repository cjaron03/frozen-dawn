package com.frozendawn.block;

import com.frozendawn.airlock.AirlockManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

public final class ManualVentValveBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ManualVentValveBlock> CODEC=simpleCodec(ManualVentValveBlock::new);
    public ManualVentValveBlock(Properties p) {super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() {return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) {b.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState s,Rotation r) {return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m) {return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(level instanceof ServerLevel server)AirlockManager.notify(player,
                AirlockManager.emergencyVent(server,pos,state.getValue(FACING))
                        ?com.frozendawn.network.AirlockStatusPayload.Status.VENTED
                        :com.frozendawn.network.AirlockStatusPayload.Status.INVALID,null,0);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
