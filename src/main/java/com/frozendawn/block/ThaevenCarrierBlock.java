package com.frozendawn.block;

import com.frozendawn.lore.ThaevenLoreManager;
import com.frozendawn.lore.ThaevenRecordId;
import com.frozendawn.init.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Permanent world-shared record vessel with per-player server-side scans. */
public final class ThaevenCarrierBlock extends Block {
    public enum Form { RELIC, WALL, RESIDUE }

    private static final VoxelShape RELIC_SHAPE = Block.box(
            3.0D, 0.0D, 3.0D, 13.0D, 11.0D, 13.0D);
    private static final VoxelShape WALL_SHAPE = Block.box(
            1.0D, 0.0D, 5.0D, 15.0D, 16.0D, 11.0D);
    private static final VoxelShape RESIDUE_SHAPE = Block.box(
            2.0D, 0.0D, 2.0D, 14.0D, 28.0D, 14.0D);

    private final ThaevenRecordId record;
    private final Form form;

    public ThaevenCarrierBlock(BlockBehaviour.Properties properties,
                               ThaevenRecordId record, Form form) {
        super(properties);
        this.record = record;
        this.form = form;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand,
            BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ThaevenLoreManager.examineCarrier(serverPlayer, record,
                    Vec3.atCenterOf(pos).add(0.0D, 0.35D, 0.0D));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ThaevenLoreManager.examineCarrier(serverPlayer, record,
                    Vec3.atCenterOf(pos).add(0.0D, 0.35D, 0.0D));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level,
                                   BlockPos pos, CollisionContext context) {
        return switch (form) {
            case RELIC -> RELIC_SHAPE;
            case WALL -> WALL_SHAPE;
            case RESIDUE -> RESIDUE_SHAPE;
        };
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
                            RandomSource random) {
        if (record != ThaevenRecordId.THE_UNTHREADING) {
            return;
        }
        double angle = level.getGameTime() * 0.075D
                + random.nextDouble() * Math.PI * 2.0D;
        double radius = 0.75D + random.nextDouble() * 0.55D;
        level.addParticle(ModParticles.UNTHREADING_MEMORY.get(),
                pos.getX() + 0.5D + Math.cos(angle) * radius,
                pos.getY() + 0.45D + random.nextDouble() * 2.0D,
                pos.getZ() + 0.5D + Math.sin(angle) * radius,
                -Math.sin(angle) * 0.018D, 0.025D,
                Math.cos(angle) * 0.018D);
        if (random.nextBoolean()) {
            level.addParticle(ModParticles.UNTHREADING_RESIDUE.get(),
                    pos.getX() + 0.5D + random.nextGaussian() * 0.65D,
                    pos.getY() + 0.3D + random.nextDouble() * 2.25D,
                    pos.getZ() + 0.5D + random.nextGaussian() * 0.65D,
                    random.nextGaussian() * 0.008D,
                    0.006D + random.nextDouble() * 0.012D,
                    random.nextGaussian() * 0.008D);
        }
    }
}
