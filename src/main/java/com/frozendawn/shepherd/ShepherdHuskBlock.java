package com.frozendawn.shepherd;

import com.frozendawn.init.ModItems;
import com.frozendawn.item.PowerCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Nine: a CS-1 that sat down at the Blast Pit and did not get up. Sealed, so it stays. What it does is {@link ShepherdNine}'s. */
public final class ShepherdHuskBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Its lamp strip is still in the socket. */
    public static final BooleanProperty LAMP = BooleanProperty.create("lamp");

    /** Square, so it need not turn with the body: most of a block wide, and as tall as the shell. */
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 11, 15);

    public ShepherdHuskBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(LAMP, true));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LAMP);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.POWER_CELL) || PowerCellItem.energy(stack) <= 0) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (player instanceof ServerPlayer holder) ShepherdNine.offeredACell(holder, pos);
        return ItemInteractionResult.SUCCESS;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer taker) ShepherdNine.touched(taker, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
