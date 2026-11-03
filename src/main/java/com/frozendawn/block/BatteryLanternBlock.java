package com.frozendawn.block;

import com.frozendawn.init.*;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.Nullable;

public final class BatteryLanternBlock extends Block implements EntityBlock,SimpleWaterloggedBlock {
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    public static final BooleanProperty LIT=BlockStateProperties.LIT,ENABLED=BlockStateProperties.ENABLED,WATERLOGGED=BlockStateProperties.WATERLOGGED;
    private static final Map<Direction,VoxelShape> SHAPES=shapes();
    public BatteryLanternBlock(Properties properties) {
        super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.UP).setValue(LIT,false).setValue(ENABLED,true).setValue(WATERLOGGED,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {builder.add(FACING,LIT,ENABLED,WATERLOGGED);}
    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state=defaultBlockState().setValue(FACING,context.getClickedFace()).setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).getType()==Fluids.WATER);
        return state.canSurvive(context.getLevel(),context.getClickedPos())?state:null;
    }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        var face=state.getValue(FACING);var support=pos.relative(face.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level,support,face);
    }
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos) {
        if(state.getValue(WATERLOGGED))level.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));
        return direction==state.getValue(FACING).getOpposite()&&!state.canSurvive(level,pos)
                ?Blocks.AIR.defaultBlockState()
                :super.updateShape(state,direction,neighbor,level,pos,neighborPos);
    }
    @Override protected FluidState getFluidState(BlockState state) {return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation) {return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror) {return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {return SHAPES.get(state.getValue(FACING));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) {return new BatteryLanternBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return !level.isClientSide()&&type==ModBlockEntities.BATTERY_LANTERN.get()?(l,p,s,be)->((BatteryLanternBlockEntity)be).serverTick():null;
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,@Nullable LivingEntity placer,ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        if(level.getBlockEntity(pos) instanceof BatteryLanternBlockEntity lantern)lantern.refreshLight(false);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!stack.is(ModItems.POWER_CELL))return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(!level.isClientSide()&&!player.isSpectator()&&level.getBlockEntity(pos) instanceof BatteryLanternBlockEntity lantern) {
            player.setItemInHand(hand,lantern.swapCell(stack));feedback(player,lantern);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(!player.getMainHandItem().isEmpty()||player.getOffhandItem().is(ModItems.POWER_CELL)||player.getOffhandItem().is(ModItems.CONDUIT_BINDER))return InteractionResult.PASS;
        if(!level.isClientSide()&&!player.isSpectator()&&level.getBlockEntity(pos) instanceof BatteryLanternBlockEntity lantern) {
            if(player.isShiftKeyDown())player.setItemInHand(InteractionHand.MAIN_HAND,lantern.swapCell(ItemStack.EMPTY));else lantern.toggle();
            feedback(player,lantern);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    private static void feedback(Player player,BatteryLanternBlockEntity lantern) {
        player.displayClientMessage(Component.translatable("message.frozendawn.battery_lantern.status",
                Component.translatable(lantern.enabled()?"tooltip.frozendawn.battery_lantern.on":"tooltip.frozendawn.battery_lantern.off"),
                lantern.storedJoules()),true);
    }
    private static Map<Direction,VoxelShape> shapes() {
        var shapes=new EnumMap<Direction,VoxelShape>(Direction.class);
        shapes.put(Direction.UP,box(4,0,4,12,14,12));shapes.put(Direction.DOWN,box(4,2,4,12,16,12));
        shapes.put(Direction.NORTH,Shapes.or(box(4,0,3,12,14,11),box(6,10,11,10,14,16)));
        shapes.put(Direction.SOUTH,Shapes.or(box(4,0,5,12,14,13),box(6,10,0,10,14,5)));
        shapes.put(Direction.WEST,Shapes.or(box(3,0,4,11,14,12),box(11,10,6,16,14,10)));
        shapes.put(Direction.EAST,Shapes.or(box(5,0,4,13,14,12),box(0,10,6,5,14,10)));
        return Map.copyOf(shapes);
    }
}
