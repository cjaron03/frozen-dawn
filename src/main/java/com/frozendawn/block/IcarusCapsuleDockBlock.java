package com.frozendawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The Icarus capsule dock, where sealed antimatter capsules wait for injection and empties wait for the Cole Protocol.
 * Using it with capsules in hand loads them; using it empty-handed while sneaking takes out the last stack. */
public final class IcarusCapsuleDockBlock extends IcarusPartBlock implements EntityBlock {
    public IcarusCapsuleDockBlock(Properties p) { super(p); }
    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState s, Level l, BlockPos p, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!IcarusCapsuleDockBlockEntity.accepts(held) || !(l.getBlockEntity(p) instanceof IcarusCapsuleDockBlockEntity dock)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!l.isClientSide()) IcarusMatterPortBlock.load(dock, held, player);
        return ItemInteractionResult.sidedSuccess(l.isClientSide());
    }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !(l.getBlockEntity(p) instanceof IcarusCapsuleDockBlockEntity dock)) return InteractionResult.PASS;
        if (!l.isClientSide()) for (int i = dock.getContainerSize() - 1; i >= 0; i--) {
            if (dock.getItem(i).isEmpty()) continue;
            player.getInventory().placeItemBackInInventory(dock.removeItemNoUpdate(i)); dock.setChanged(); break;
        }
        return InteractionResult.sidedSuccess(l.isClientSide());
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        if (!s.is(next.getBlock()) && l.getBlockEntity(p) instanceof IcarusCapsuleDockBlockEntity dock) Containers.dropContents(l, p, dock.items());
        super.onRemove(s, l, p, next, moving);
    }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new IcarusCapsuleDockBlockEntity(p, s); }
}
