package com.frozendawn.block;

import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class FabricatorMenu extends AbstractContainerMenu {
    private static final int SLOTS=FabricatorBlockEntity.INPUTS+1;
    private final Container contents;private final BlockPos pos;private final ContainerData data;
    public FabricatorMenu(int id,Inventory inv,FriendlyByteBuf buf){this(id,inv,new SimpleContainer(SLOTS),new SimpleContainerData(4),buf.readBlockPos());}
    public FabricatorMenu(int id,Inventory inv,Container c,ContainerData d,BlockPos p){
        super(ModMenuTypes.FABRICATOR.get(),id);contents=c;pos=p;data=d;addDataSlots(d);
        for(int row=0;row<2;row++)for(int col=0;col<3;col++)addSlot(new Slot(c,row*3+col,30+col*18,26+row*18){
            @Override public boolean mayPlace(ItemStack s){return !s.is(ModItems.DATA_SLATE);}
        });
        addSlot(new Slot(c,FabricatorBlockEntity.OUTPUT,124,35){
            @Override public boolean mayPlace(ItemStack s){return false;}
            @Override public void onTake(Player player,ItemStack s){super.onTake(player,s);if(player instanceof ServerPlayer server)WorldTickHandler.grantAdvancement(server,"printed_not_made");}
        });
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,8+col*18,84+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,8+col*18,142));
    }
    public ContainerData data(){return data;}
    @Override public boolean stillValid(Player p){return contents instanceof FabricatorBlockEntity f?f.stillValid(p):p.level().getBlockState(pos).getBlock() instanceof FabricatorBlock;}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var s=slot.getItem();var copy=s.copy();int main=SLOTS+27,end=slots.size();
        if(index<SLOTS){if(!moveItemStackTo(s,SLOTS,end,true))return ItemStack.EMPTY;if(index==FabricatorBlockEntity.OUTPUT)slot.onQuickCraft(s,copy);}
        else if(!s.is(ModItems.DATA_SLATE)){if(!moveItemStackTo(s,0,FabricatorBlockEntity.INPUTS,false))return ItemStack.EMPTY;}
        else if(index<main){if(!moveItemStackTo(s,main,end,false))return ItemStack.EMPTY;}else if(!moveItemStackTo(s,SLOTS,main,false))return ItemStack.EMPTY;
        if(s.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,s);return copy;
    }
}
