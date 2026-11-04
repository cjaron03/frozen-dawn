package com.frozendawn.vault;

import com.frozendawn.init.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
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
import org.jetbrains.annotations.Nullable;

/** A block of the shell that drives one of the vault's mechanisms from inside the wall. */
public final class VaultMechanismBlock extends BaseEntityBlock {
    public enum Kind implements StringRepresentable {
        DOOR, BRIDGE, LAST_DOOR;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final MapCodec<VaultMechanismBlock> CODEC = simpleCodec(VaultMechanismBlock::new);

    public VaultMechanismBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, Kind.DOOR));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(KIND); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new VaultMechanismBlockEntity(pos, state); }
    @Override public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.VAULT_MECHANISM.get(), level.isClientSide ? VaultMechanismBlockEntity::clientTick : VaultMechanismBlockEntity::serverTick);
    }
}
