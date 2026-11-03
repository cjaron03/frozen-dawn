package com.frozendawn.block;

import com.frozendawn.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** One ice slot and three readings. No production knobs or upgrade tabs. */
public final class PixieMenu extends AbstractContainerMenu {
    private final Container contents;private final BlockPos pos;private final ContainerData data=new SimpleContainerData(9);
    public PixieMenu(int id,Inventory inv,FriendlyByteBuf buf){this(id,inv,new SimpleContainer(1),buf.readBlockPos());}
    public PixieMenu(int id,Inventory inv,Container c,BlockPos p){
        super(ModMenuTypes.PIXIE.get(),id);contents=c;pos=p;addDataSlots(data);
        addSlot(new Slot(c,0,12,54){@Override public boolean mayPlace(ItemStack s){return PixieBlockEntity.iceWater(s)>0;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,24+col*18,164+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,24+col*18,222));refresh();
    }
    public ContainerData data(){return data;}
    public long water(){long v=0;for(int i=0;i<4;i++)v|=(long)(data.get(i+2)&65535)<<(i*16);return v;}
    private void refresh(){if(contents instanceof PixieBlockEntity p){data.set(0,p.status());data.set(1,p.outputWatts());data.set(6,p.activity());data.set(7,temperatureReading(p.machineTemperature()));data.set(8,temperatureReading(p.roomFeel()));for(int i=0;i<4;i++)data.set(i+2,(int)(p.waterMicro()>>>(i*16))&65535);}}
    private static int temperatureReading(double value){return Double.isFinite(value)?(int)Math.clamp(Math.round(value*10),Short.MIN_VALUE+1,Short.MAX_VALUE):Short.MIN_VALUE;}
    @Override public void broadcastChanges(){refresh();super.broadcastChanges();}
    @Override public boolean stillValid(Player p){return contents instanceof PixieBlockEntity pixie?pixie.stillValid(p):!p.isSpectator()&&p.distanceToSqr(pos.getCenter())<=64&&p.level().getBlockState(pos).getBlock() instanceof PixieBlock;}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var s=slot.getItem();var copy=s.copy();
        if(index==0){if(!moveItemStackTo(s,1,slots.size(),true))return ItemStack.EMPTY;}
        else if(PixieBlockEntity.iceWater(s)>0){if(!moveItemStackTo(s,0,1,false))return ItemStack.EMPTY;}
        else if(index<28){if(!moveItemStackTo(s,28,37,false))return ItemStack.EMPTY;}else if(!moveItemStackTo(s,1,28,false))return ItemStack.EMPTY;
        if(s.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,s);return copy;
    }
}
