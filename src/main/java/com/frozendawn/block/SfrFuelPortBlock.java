package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.frozendawn.init.ModItems;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Block;

/** Takes Deuterium Canisters into the torus. */
public final class SfrFuelPortBlock extends Block implements EntityBlock {
    public SfrFuelPortBlock(Properties p) { super(p); }
    /** Using the port with Deuterium Canisters in hand loads the whole held stack that fits. */
    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState s, Level l, BlockPos p, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!held.is(ModItems.DEUTERIUM_CANISTER.get()) || !(l.getBlockEntity(p) instanceof SfrFuelPortBlockEntity port)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!l.isClientSide()) {
            var slot = port.getItem(0);
            int room = slot.isEmpty() ? held.getMaxStackSize() : slot.getMaxStackSize() - slot.getCount(), moved = Math.min(room, held.getCount());
            if (moved > 0) { if (slot.isEmpty()) port.setItem(0, held.copyWithCount(moved)); else { slot.grow(moved); port.setChanged(); } held.consume(moved, player); }
        }
        return ItemInteractionResult.sidedSuccess(l.isClientSide());
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        if (!s.is(next.getBlock()) && l.getBlockEntity(p) instanceof SfrFuelPortBlockEntity port) Containers.dropContents(l, p, port.items());
        super.onRemove(s, l, p, next, moving);
    }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new SfrFuelPortBlockEntity(p, s); }
}
