package com.frozendawn.block;

import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The cells are the only store: no duplicated bank-level energy or charge on empty slots. */
public final class PowerStorageBlockEntity extends BlockEntity implements Container, MenuProvider {
    private final NonNullList<ItemStack> items;
    private int charging, feeding, machineFeeding;
    private long machineDrawTick=Long.MIN_VALUE;
    public PowerStorageBlockEntity(BlockPos p, BlockState s) {
        super(ModBlockEntities.POWER_STORAGE.get(), p, s);
        items = NonNullList.withSize(isBank() ? 4 : 1, ItemStack.EMPTY);
    }
    @Override public void onLoad() { super.onLoad(); if (level instanceof net.minecraft.server.level.ServerLevel server) com.frozendawn.power.PowerNetworks.storageLoaded(server,worldPosition); }
    @Override public void setRemoved() { if (level instanceof net.minecraft.server.level.ServerLevel server) com.frozendawn.power.PowerNetworks.storageRemoved(server,worldPosition); super.setRemoved(); }
    public boolean isBank() { return ((PowerStorageBlock)getBlockState().getBlock()).isBank(); }
    public int storedJoules() { return items.stream().mapToInt(PowerCellItem::energy).sum(); }
    public int capacityJoules() { return items.size() * PowerCellItem.CAPACITY_JOULES; }
    public int charge(int offered) { return transfer(offered, true); }
    public int draw(int requested) { return transfer(requested, false); }
    private int transfer(int requested, boolean fill) {
        int remaining = Math.max(0, requested);
        for (var cell : items) {
            if (!cell.is(ModItems.POWER_CELL)) continue;
            int before = PowerCellItem.energy(cell);
            int amount = Math.min(remaining, fill ? PowerCellItem.CAPACITY_JOULES - before : before);
            PowerCellItem.setEnergy(cell, before + (fill ? amount : -amount)); remaining -= amount;
            if (remaining == 0) break;
        }
        int moved = Math.max(0, requested) - remaining;
        if (moved > 0) setChanged();
        return moved;
    }
    public void publishMachineDraw(int joules) {
        if(joules<=0||level==null)return;
        if(machineDrawTick!=level.getGameTime())machineFeeding=0;
        machineDrawTick=level.getGameTime();machineFeeding+=joules*20;
    }
    public void publish(int charged, int drawn) { charging = charged; feeding = drawn; }
    public ContainerData data() { return new ContainerData() {
        public int get(int i) { return switch(i) { case 0 -> storedJoules()/100; case 1 -> charging/100; case 2 -> (feeding+(level!=null&&level.getGameTime()-machineDrawTick<=1&&level.getGameTime()>=machineDrawTick?machineFeeding:0))/100; default -> 0; }; }
        public void set(int i, int v) {} public int getCount() { return 3; }
    }; }
    @Override public Component getDisplayName() { return Component.translatable(isBank() ? "block.frozendawn.battery_bank" : "block.frozendawn.cell_dock"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { return new PowerStorageMenu(id, inv, this, data(), worldPosition); }
    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int i) { return items.get(i); }
    @Override public ItemStack removeItem(int i, int n) { var s = ContainerHelper.removeItem(items,i,n); if (!s.isEmpty()) setChanged(); return s; }
    @Override public ItemStack removeItemNoUpdate(int i) { return ContainerHelper.takeItem(items,i); }
    @Override public boolean canPlaceItem(int i, ItemStack s) { return s.is(ModItems.POWER_CELL); }
    @Override public int getMaxStackSize() { return 1; }
    @Override public void setItem(int i, ItemStack s) { if (!s.isEmpty() && !canPlaceItem(i,s)) return; s.setCount(Math.min(s.getCount(),1)); items.set(i,s); setChanged(); }
    @Override public boolean stillValid(Player p) { return !p.isSpectator() && level != null && level.getBlockEntity(worldPosition) == this && p.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public void setChanged() { super.setChanged(); if (level != null) level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock()); }
    @Override protected void saveAdditional(CompoundTag t, HolderLookup.Provider r) { super.saveAdditional(t,r); ContainerHelper.saveAllItems(t,items,r); }
    @Override protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) {
        super.loadAdditional(t,r); items.clear(); ContainerHelper.loadAllItems(t,items,r);
        for (int i=0;i<items.size();i++) { var s=items.get(i); if (!s.isEmpty() && !s.is(ModItems.POWER_CELL)) items.set(i,ItemStack.EMPTY); else if (!s.isEmpty()) { s.setCount(1); PowerCellItem.setEnergy(s,PowerCellItem.energy(s)); } }
    }
}
