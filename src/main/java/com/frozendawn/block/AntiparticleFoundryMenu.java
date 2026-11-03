package com.frozendawn.block;

import com.frozendawn.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Empty capsules in, sealed capsules out. */
public final class AntiparticleFoundryMenu extends AbstractContainerMenu {
    private final Container contents;private final BlockPos pos;private final ContainerData data;
    public AntiparticleFoundryMenu(int id,Inventory inv,FriendlyByteBuf buf){this(id,inv,new SimpleContainer(2),new SimpleContainerData(4),buf.readBlockPos());}
    public AntiparticleFoundryMenu(int id,Inventory inv,Container c,ContainerData d,BlockPos p){
        super(ModMenuTypes.ANTIPARTICLE_FOUNDRY.get(),id);contents=c;pos=p;data=d;addDataSlots(d);
        addSlot(new Slot(c,AntiparticleFoundryBlockEntity.INPUT,50,36){@Override public boolean mayPlace(ItemStack s){return AntiparticleFoundryBlockEntity.accepts(s);}});
        addSlot(new Slot(c,AntiparticleFoundryBlockEntity.OUTPUT,110,36){@Override public boolean mayPlace(ItemStack s){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,8+col*18,84+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,8+col*18,142));
    }
    public ContainerData data(){return data;}
    @Override public boolean stillValid(Player p){return contents instanceof AntiparticleFoundryBlockEntity f?f.stillValid(p):p.level().getBlockState(pos).getBlock() instanceof AntiparticleFoundryBlock;}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var s=slot.getItem();var copy=s.copy();
        if(index<2){if(!moveItemStackTo(s,2,slots.size(),true))return ItemStack.EMPTY;}
        else if(AntiparticleFoundryBlockEntity.accepts(s)){if(!moveItemStackTo(s,0,1,false))return ItemStack.EMPTY;}
        else if(index<29){if(!moveItemStackTo(s,29,38,false))return ItemStack.EMPTY;}else if(!moveItemStackTo(s,2,29,false))return ItemStack.EMPTY;
        if(s.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,s);return copy;
    }
}
