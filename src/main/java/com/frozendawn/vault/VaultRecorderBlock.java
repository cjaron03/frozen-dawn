package com.frozendawn.vault;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.vault.TwoPersonRule.Phase;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** The recorder's console, on the drill hall's west wall, three blocks north to south; the middle one runs the room. Any of them
 * takes a press. */
public final class VaultRecorderBlock extends BaseEntityBlock {
    public enum Mode implements StringRepresentable {
        IDLE, RECORDING, PLAYING;
        static Mode of(Phase phase) { return values()[phase.ordinal()]; }
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final int MIDDLE = 1;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);
    public static final MapCodec<VaultRecorderBlock> CODEC = simpleCodec(VaultRecorderBlock::new);
    private static final VoxelShape SHAPE = box(0, 0, 0, 4, 16, 16);

    public VaultRecorderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, MIDDLE).setValue(MODE, Mode.IDLE));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(PART, MODE); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == MIDDLE ? new VaultRecorderBlockEntity(pos, state) : null;
    }
    @Override public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.VAULT_RECORDER.get(), VaultRecorderBlockEntity::serverTick);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var middle = pos.offset(0, 0, MIDDLE - state.getValue(PART));
        if (player instanceof ServerPlayer presser && level.getBlockEntity(middle) instanceof VaultRecorderBlockEntity be) be.press(presser);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
