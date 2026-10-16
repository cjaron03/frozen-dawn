package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.*;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

/** Compact surface-mounted reserve lamp. Facing points away from its mounting surface. */
public final class OrsaEmergencyLightBlock extends Block implements EntityBlock {
    public static final BooleanProperty LIT=BlockStateProperties.LIT;
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    private static final Map<Direction,VoxelShape> SHAPES=lampShapes();
    public OrsaEmergencyLightBlock(Properties properties) {
        super(properties);registerDefaultState(stateDefinition.any().setValue(LIT,false).setValue(FACING,Direction.UP));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(LIT,FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        return defaultBlockState().setValue(FACING,context.getClickedFace());
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPES.get(state.getValue(FACING));}
    private static Map<Direction,VoxelShape> lampShapes(){
        var shapes=new EnumMap<Direction,VoxelShape>(Direction.class);
        for(var face:Direction.values())shapes.put(face,Shapes.or(
                orientedBox(face,4,0,4,12,1.5,12),
                orientedBox(face,5,1.5,6,11,4.5,10),
                orientedBox(face,6,1.5,5,10,4.5,11),
                orientedBox(face,6,4.5,6,10,5.5,10)));
        return Map.copyOf(shapes);
    }
    /** Same centre rotations as the six model variants; legacy palettes default UP. */
    private static VoxelShape orientedBox(Direction face,double x1,double y1,double z1,double x2,double y2,double z2){
        return switch(face){
            case UP->box(x1,y1,z1,x2,y2,z2);
            case DOWN->box(x1,16-y2,16-z2,x2,16-y1,16-z1);
            case NORTH->box(x1,z1,16-y2,x2,z2,16-y1);
            case SOUTH->box(16-x2,z1,y1,16-x1,z2,y2);
            case WEST->box(16-y2,z1,16-x2,16-y1,z2,16-x1);
            case EAST->box(y1,z1,x1,y2,z2,x2);
        };
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new OrsaEmergencyLightBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return !level.isClientSide()&&type==ModBlockEntities.ORSA_EMERGENCY_LIGHT.get()
                ?(world,pos,current,entity)->((OrsaEmergencyLightBlockEntity)entity).serverTick():null;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player.getMainHandItem().is(ModItems.CONDUIT_BINDER)||player.getOffhandItem().is(ModItems.CONDUIT_BINDER))return InteractionResult.PASS;
        if(player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof OrsaEmergencyLightBlockEntity lamp){
            int seconds=(lamp.backupTicks()+19)/20;
            server.displayClientMessage(Component.translatable(state.getValue(LIT)?"message.frozendawn.emergency_light.backup"
                    :seconds>0?"message.frozendawn.emergency_light.ready":"message.frozendawn.emergency_light.empty",
                    String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60)),true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
