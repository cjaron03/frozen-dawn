package com.frozendawn.block;

import com.frozendawn.world.HeldLanternLights;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A distinct light identity prevents overlap cleanup from erasing vanilla/alarm lights. */
public final class HeldLanternLightBlock extends LightBlock {
    public static final MapCodec<LightBlock> CODEC=simpleCodec(HeldLanternLightBlock::new);
    public HeldLanternLightBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(LEVEL,14));}
    @Override public MapCodec<LightBlock> codec(){return CODEC;}
    // Unlike a physical lantern, a transient light must yield to flowing water normally.
    @Override public boolean canPlaceLiquid(@org.jetbrains.annotations.Nullable Player player,BlockGetter level,BlockPos pos,BlockState state,net.minecraft.world.level.material.Fluid fluid){
        return !state.getValue(WATERLOGGED)&&fluid.isSame(net.minecraft.world.level.material.Fluids.WATER);
    }
    @Override public boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,net.minecraft.world.level.material.FluidState incoming){
        if(state.getValue(WATERLOGGED)||!incoming.is(net.minecraft.tags.FluidTags.WATER))return false;
        if(!level.isClientSide()){
            level.setBlock(pos,incoming.createLegacyBlock(),3);
            level.scheduleTick(pos,incoming.getType(),incoming.getType().getTickDelay(level));
        }
        return true;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return InteractionResult.PASS;}
    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state){return ItemStack.EMPTY;}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){if(!level.isClientSide())level.scheduleTick(pos,this,2);}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        if(HeldLanternLights.retains(level,pos))level.scheduleTick(pos,this,2);
        else HeldLanternLights.remove(level,pos);
    }
}
