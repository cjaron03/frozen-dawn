package com.frozendawn.item;

import com.frozendawn.block.FishingHoleBlock;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Drills a Fishing Hole in ice with at least {@link #MIN_ICE_DEPTH} more ice
 * blocks straight below, which marks a frozen body of water. Using it on an
 * open hole plugs it back to ice.
 */
public class IceAugerItem extends Item {

    public static final int MIN_ICE_DEPTH = 3;

    public IceAugerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        // Needing open space or loose snow above keeps the auger from drilling the ice under an existing hole.
        BlockState above = level.getBlockState(pos.above());
        if (!state.is(BlockTags.ICE) || !(above.isAir() || above.canBeReplaced())) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (iceDepth(level, pos.below()) < MIN_ICE_DEPTH) {
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.frozendawn.ice_auger.too_thin"), true);
            }
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            level.levelEvent(2001, pos, Block.getId(state));
            if (!above.isAir()) {
                level.destroyBlock(pos.above(), false);
            }
            level.setBlockAndUpdate(pos, ModBlocks.FISHING_HOLE.get().defaultBlockState());
            level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8f, 0.7f);
            if (player != null) {
                EquipmentSlot slot = context.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                context.getItemInHand().hurtAndBreak(1, player, slot);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // The hole has no outline, so it is found by tracing to its water surface.
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);
        BlockPos pos = hit.getBlockPos();
        if (!level.getBlockState(pos).is(ModBlocks.FISHING_HOLE.get())) return InteractionResultHolder.pass(stack);
        // Same guards as a bucket: spawn protection and adventure mode.
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), stack)) {
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            FishingHoleBlock.plug(level, pos);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Counts ice-family blocks from {@code top} straight down, including it. */
    static int iceDepth(Level level, BlockPos top) {
        int depth = 0;
        BlockPos.MutableBlockPos cursor = top.mutable();
        while (depth < MIN_ICE_DEPTH && level.getBlockState(cursor).is(BlockTags.ICE)) {
            depth++;
            cursor.move(0, -1, 0);
        }
        return depth;
    }
}
