package com.frozendawn.block;

import com.frozendawn.init.ModItems;
import com.frozendawn.power.GridLight;
import com.frozendawn.power.PowerNetworks;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Wall/ceiling work light: 50 W, light 12, no heat, fuel, O2 or sensory emissions. */
public final class WorkLampBlock extends Block implements GridLight {
    public static final int DEMAND_WATTS = 50;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;
    /** Points away from the mounting face. UP exists for state compatibility but is not placeable. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final Map<Direction,VoxelShape> SHAPES = shapes();

    public WorkLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ENABLED,true).setValue(LIT,false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        builder.add(FACING,ENABLED,LIT);
    }
    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        if (facing == Direction.UP) return null;
        var state = defaultBlockState().setValue(FACING,facing);
        return state.canSurvive(context.getLevel(),context.getClickedPos()) ? state : null;
    }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return facing != Direction.UP && level.getBlockState(support).isFaceSturdy(level,support,facing);
    }
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,
            LevelAccessor level,BlockPos pos,BlockPos neighborPos) {
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level,pos)
                ? Blocks.AIR.defaultBlockState() : super.updateShape(state,direction,neighbor,level,pos,neighborPos);
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) {
        return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));
    }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moved) {
        super.onPlace(state,level,pos,previous,moved);
        if (!level.isClientSide()) level.scheduleTick(pos,this,20);
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        setGridPowered(level,pos,PowerNetworks.powered(level,pos));
        level.scheduleTick(pos,this,20);
    }
    @Override public int demandWatts(BlockState state) { return state.getValue(ENABLED) ? DEMAND_WATTS : 0; }
    @Override public void setGridPowered(ServerLevel level,BlockPos pos,boolean powered) {
        if (!level.isLoaded(pos)) return;
        var state = level.getBlockState(pos);
        if (state.getBlock() != this) return;
        boolean lit = powered && state.getValue(ENABLED);
        if (state.getValue(LIT) != lit) level.setBlock(pos,state.setValue(LIT,lit),3);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if (player.getMainHandItem().is(ModItems.CONDUIT_BINDER) || player.getOffhandItem().is(ModItems.CONDUIT_BINDER)
                || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide() && !player.isSpectator()) {
            boolean enabled = !state.getValue(ENABLED);
            level.setBlock(pos,state.setValue(ENABLED,enabled).setValue(LIT,false),3);
            player.displayClientMessage(Component.translatable(enabled
                    ? "message.frozendawn.work_lamp.on" : "message.frozendawn.work_lamp.off"),true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> text,TooltipFlag flag) {
        text.add(Component.translatable("tooltip.frozendawn.work_lamp.power").withStyle(ChatFormatting.AQUA));
        text.add(Component.translatable("tooltip.frozendawn.work_lamp.controls").withStyle(ChatFormatting.GRAY));
    }
    private static Map<Direction,VoxelShape> shapes() {
        var result = new EnumMap<Direction,VoxelShape>(Direction.class);
        for (var face : Direction.values()) result.put(face,Shapes.or(
                oriented(face,4,0,5,12,1,11), oriented(face,6,1,6,10,4,10), oriented(face,2,4,4,14,8.5,12)));
        return Map.copyOf(result);
    }
    private static VoxelShape oriented(Direction face,double x1,double y1,double z1,double x2,double y2,double z2) {
        return switch (face) {
            case UP -> box(x1,y1,z1,x2,y2,z2);
            case DOWN -> box(x1,16-y2,16-z2,x2,16-y1,16-z1);
            case NORTH -> box(x1,z1,16-y2,x2,z2,16-y1);
            case SOUTH -> box(16-x2,z1,y1,16-x1,z2,y2);
            case WEST -> box(16-y2,z1,16-x2,16-y1,z2,16-x1);
            case EAST -> box(y1,z1,x1,y2,z2,x2);
        };
    }
}
