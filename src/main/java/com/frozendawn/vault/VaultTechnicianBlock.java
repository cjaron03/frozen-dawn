package com.frozendawn.vault;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The dead technician, seated against the wall. A block, not an entity: nothing here is alive, and nothing moves the body
 * except the one change on waking. Drawn by {@code VaultTechnicianRenderer}. */
public final class VaultTechnicianBlock extends BaseEntityBlock {
    public static final MapCodec<VaultTechnicianBlock> CODEC = simpleCodec(VaultTechnicianBlock::new);
    private static final VoxelShape SHAPE = box(2, 0, 0, 14, 16, 16);

    public VaultTechnicianBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new VaultTechnicianBlockEntity(pos, state); }
}
