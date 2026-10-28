package com.frozendawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The Icarus matter input port. Using it with plain blocks in hand loads as much of the held stack as fits. */
public final class IcarusMatterPortBlock extends IcarusPartBlock implements EntityBlock {
    public IcarusMatterPortBlock(Properties p) { super(p); }
    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState s, Level l, BlockPos p, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!IcarusMatterPortBlockEntity.accepts(held) || !(l.getBlockEntity(p) instanceof IcarusMatterPortBlockEntity port)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!l.isClientSide()) load(port, held, player);
        return ItemInteractionResult.sidedSuccess(l.isClientSide());
    }
    /** Moves as much of held as the container's slots accept, merging first. Shared with the capsule dock. */
    static void load(Container c, ItemStack held, Player player) {
        int before = held.getCount(), left = before;
        for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
            var slot = c.getItem(i);
            if (slot.isEmpty()) { int n = Math.min(left, held.getMaxStackSize()); c.setItem(i, held.copyWithCount(n)); left -= n; }
            else if (ItemStack.isSameItemSameComponents(slot, held)) { int n = Math.min(left, slot.getMaxStackSize() - slot.getCount()); slot.grow(n); left -= n; }
        }
        if (left < before) { c.setChanged(); held.consume(before - left, player); }
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        if (!s.is(next.getBlock()) && l.getBlockEntity(p) instanceof IcarusMatterPortBlockEntity port) Containers.dropContents(l, p, port.items());
        super.onRemove(s, l, p, next, moving);
    }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new IcarusMatterPortBlockEntity(p, s); }
}
