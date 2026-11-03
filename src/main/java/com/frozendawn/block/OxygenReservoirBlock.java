package com.frozendawn.block;
import com.frozendawn.init.ModBlockEntities;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.*;
public final class OxygenReservoirBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public OxygenReservoirBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return box(1,0,1,15,16,15);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new OxygenReservoirBlockEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return !l.isClientSide()&&t==ModBlockEntities.OXYGEN_RESERVOIR.get()?(world,pos,state,be)->((OxygenReservoirBlockEntity)be).serverTick():null;}
    @Override protected void onExplosionHit(BlockState state,Level level,BlockPos pos,Explosion explosion,java.util.function.BiConsumer<ItemStack,BlockPos> drops){if(explosion.getBlockInteraction()==Explosion.BlockInteraction.TRIGGER_BLOCK)super.onExplosionHit(state,level,pos,explosion,drops);else state.onBlockExploded(level,pos,explosion);}
    @Override protected List<ItemStack> getDrops(BlockState s,LootParams.Builder p){return p.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS)!=null?List.of():super.getDrops(s,p);}
}
