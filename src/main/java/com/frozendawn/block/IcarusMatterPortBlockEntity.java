package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Nine slots of plain blocks, fed by hand or hopper; the controller annihilates them for power. A block that carries
 * a block entity is refused, so no inventory or machine is ever fed in. */
public final class IcarusMatterPortBlockEntity extends BlockEntity implements WorldlyContainer {
    public static final int SLOTS = 9;
    private static final int[] FACE_SLOTS = { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public IcarusMatterPortBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.ICARUS_MATTER_PORT.get(), p, s); }
    public static boolean accepts(ItemStack s) { return s.getItem() instanceof BlockItem b && !(b.getBlock() instanceof EntityBlock); }
    public NonNullList<ItemStack> items() { return items; }
    public int count() { return items.stream().filter(IcarusMatterPortBlockEntity::accepts).mapToInt(ItemStack::getCount).sum(); }
    /** Takes blocks in slot order. */
    public void use(int blocks) {
        for (int i = 0; i < SLOTS && blocks > 0; i++) {
            var s = items.get(i); if (!accepts(s)) continue;
            int n = Math.min(blocks, s.getCount()); s.shrink(n); blocks -= n;
        }
        setChanged();
    }

    @Override public int getContainerSize() { return SLOTS; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int i) { return items.get(i); }
    @Override public ItemStack removeItem(int i, int n) { var s = ContainerHelper.removeItem(items, i, n); if (!s.isEmpty()) setChanged(); return s; }
    @Override public ItemStack removeItemNoUpdate(int i) { return ContainerHelper.takeItem(items, i); }
    @Override public void setItem(int i, ItemStack s) { s.limitSize(getMaxStackSize(s)); items.set(i, s); setChanged(); }
    @Override public boolean canPlaceItem(int i, ItemStack s) { return accepts(s); }
    @Override public boolean stillValid(Player p) { return level != null && level.getBlockEntity(worldPosition) == this && p.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public int[] getSlotsForFace(Direction d) { return FACE_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int i, ItemStack s, Direction d) { return canPlaceItem(i, s); }
    @Override public boolean canTakeItemThroughFace(int i, ItemStack s, Direction d) { return false; }
    @Override protected void saveAdditional(CompoundTag t, HolderLookup.Provider r) { super.saveAdditional(t, r); ContainerHelper.saveAllItems(t, items, r); }
    @Override protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) { super.loadAdditional(t, r); items.clear(); ContainerHelper.loadAllItems(t, items, r); }
}
