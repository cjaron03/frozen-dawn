package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One stack of Deuterium Canisters, fed by hand or hopper. The controller takes one canister per burned unit. */
public final class SfrFuelPortBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final int[] SLOTS = { 0 };
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    public SfrFuelPortBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.SFR_FUEL_PORT.get(), p, s); }
    public NonNullList<ItemStack> items() { return items; }
    public int deuterium() { return items.get(0).is(ModItems.DEUTERIUM_CANISTER.get()) ? items.get(0).getCount() : 0; }
    public void use(int canisters) { if (canisters > 0) { items.get(0).shrink(canisters); setChanged(); } }

    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return items.get(0).isEmpty(); }
    @Override public ItemStack getItem(int i) { return items.get(i); }
    @Override public ItemStack removeItem(int i, int n) { var s = ContainerHelper.removeItem(items, i, n); if (!s.isEmpty()) setChanged(); return s; }
    @Override public ItemStack removeItemNoUpdate(int i) { return ContainerHelper.takeItem(items, i); }
    @Override public void setItem(int i, ItemStack s) { s.limitSize(getMaxStackSize(s)); items.set(i, s); setChanged(); }
    @Override public boolean canPlaceItem(int i, ItemStack s) { return s.is(ModItems.DEUTERIUM_CANISTER.get()); }
    @Override public boolean stillValid(Player p) { return level != null && level.getBlockEntity(worldPosition) == this && p.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public int[] getSlotsForFace(Direction d) { return SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int i, ItemStack s, Direction d) { return canPlaceItem(i, s); }
    @Override public boolean canTakeItemThroughFace(int i, ItemStack s, Direction d) { return false; }
    @Override protected void saveAdditional(CompoundTag t, HolderLookup.Provider r) { super.saveAdditional(t, r); ContainerHelper.saveAllItems(t, items, r); }
    @Override protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) { super.loadAdditional(t, r); items.clear(); ContainerHelper.loadAllItems(t, items, r); }
}
