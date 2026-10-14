package com.frozendawn.block;

import com.frozendawn.init.ModMenuTypes;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class PowerStorageMenu extends AbstractContainerMenu {
    private final Container cells;
    private final BlockPos pos;
    private final ContainerData data;
    public PowerStorageMenu(int id, Inventory inv, FriendlyByteBuf buf) { this(id,inv,buf.readBlockPos(),buf.readBoolean()); }
    private PowerStorageMenu(int id, Inventory inv, BlockPos pos, boolean bank) { this(id,inv,new SimpleContainer(bank?4:1),new SimpleContainerData(3),pos); }
    public PowerStorageMenu(int id, Inventory inv, Container cells, ContainerData data, BlockPos pos) {
        super(ModMenuTypes.POWER_STORAGE.get(),id); this.cells=cells;this.data=data;this.pos=pos;addDataSlots(data);
        for(int i=0;i<cells.getContainerSize();i++) addSlot(new Slot(cells,i,88-cells.getContainerSize()*9+i*18,54) {
            @Override public boolean mayPlace(ItemStack s) { return s.is(ModItems.POWER_CELL); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,8+col*18,100+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,8+col*18,158));
    }
    public int cellSlots() { return cells.getContainerSize(); }
    public ContainerData data() { return data; }
    @Override public boolean stillValid(Player p) { return cells instanceof PowerStorageBlockEntity storage ? storage.stillValid(p) : p.level().getBlockState(pos).getBlock() instanceof PowerStorageBlock; }
    @Override public ItemStack quickMoveStack(Player p,int index) {
        if(index<0||index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var item=slot.getItem();var copy=item.copy();int n=cellSlots();
        if(index<n) { if(!moveItemStackTo(item,n,slots.size(),true))return ItemStack.EMPTY; }
        else if(!item.is(ModItems.POWER_CELL)||!moveItemStackTo(item,0,n,false))return ItemStack.EMPTY;
        if(item.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,item);return copy;
    }
}
