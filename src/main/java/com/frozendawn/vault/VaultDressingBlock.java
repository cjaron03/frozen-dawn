package com.frozendawn.vault;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** What was dropped on the way out: dressing with no loot in it. The one exception is the Seal Order, loose on the catwalk,
 * which comes away as a document and leaves plain paper. */
public final class VaultDressingBlock extends HorizontalDirectionalBlock {
    public enum Kind implements StringRepresentable {
        CRATE, CASE, HARD_HAT, PAPER, SEAL_ORDER;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final MapCodec<VaultDressingBlock> CODEC = simpleCodec(VaultDressingBlock::new);
    private static final VoxelShape CASE = box(1, 0, 3, 15, 6, 13), HAT = box(3, 0, 3, 13, 5, 13), FLAT = box(1, 0, 1, 15, 1, 15);

    public VaultDressingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(KIND, Kind.PAPER));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, KIND); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(KIND)) { case CRATE -> Shapes.block(); case CASE -> CASE; case HARD_HAT -> HAT; default -> FLAT; };
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(KIND) == Kind.CRATE ? Shapes.block() : Shapes.empty();
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(KIND) != Kind.SEAL_ORDER) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(KIND, Kind.PAPER), 3);
            var order = VaultDocuments.sealOrder();
            if (!player.getInventory().add(order)) player.drop(order, false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
