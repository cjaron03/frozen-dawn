package com.frozendawn.shepherd;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModSounds;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The CS-1 dock. While she sleeps she rests on it as part of the block; a charged Power Cell wakes her, and from then
 * on every dock in the world is furniture. The world's truth is {@link ShepherdSavedData}, never the block.
 */
public final class ShepherdDockBlock extends Block implements EntityBlock {
    public enum DockState implements StringRepresentable {
        /** Red light, empty slot, her at rest. */
        FOUND,
        /** Green light, the cell in, her recording playing. */
        BOOT,
        /** The bare dock she lifted off from. */
        EMPTY,
        /** A dock with no claim on her: she is awake somewhere else. */
        DARK;

        @Override public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The states that draw her body on the dock. */
        public boolean holdsHer() {
            return this == FOUND || this == BOOT;
        }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DockState> STATE = EnumProperty.create("state", DockState.class);

    private static final VoxelShape BARE = box(0, 0, 0, 16, 9, 16);
    private static final VoxelShape OCCUPIED = Shapes.or(BARE, box(2, 9, 3, 14, 16, 13));

    public ShepherdDockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(STATE, DockState.FOUND));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATE);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        boolean awake = context.getLevel() instanceof ServerLevel level && ShepherdSavedData.get(level.getServer()).stage() == Stage.AWAKE;
        return awake ? state.setValue(STATE, DockState.DARK) : state;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(STATE).holdsHer() ? OCCUPIED : BARE;
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.POWER_CELL)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer waker)) return ItemInteractionResult.SUCCESS;
        boolean sleeping = ShepherdSavedData.get(server.getServer()).stage() == Stage.DORMANT;
        if (sleeping && state.getValue(STATE) == DockState.FOUND && PowerCellItem.energy(stack) > 0
                && level.getBlockEntity(pos) instanceof ShepherdDockBlockEntity dock) {
            stack.shrink(1);
            dock.wake(waker);
        } else {
            level.playSound(null, pos, ModSounds.CS1_DOCK_REFUSE.get(), SoundSource.BLOCKS, 1, 1);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShepherdDockBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.CS1_DOCK.get()) return null;
        return (l, p, s, entity) -> ((ShepherdDockBlockEntity) entity).serverTick();
    }
}
