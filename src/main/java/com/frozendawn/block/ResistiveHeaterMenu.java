package com.frozendawn.block;

import com.frozendawn.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Read-only status. Target uses the existing room thermostat. No fuel or inventory slots. */
public final class ResistiveHeaterMenu extends AbstractContainerMenu {
    private final ResistiveHeaterBlockEntity heater;
    private final BlockPos pos;
    private final ContainerData data=new SimpleContainerData(5);
    public ResistiveHeaterMenu(int id,Inventory inv,FriendlyByteBuf buf){super(ModMenuTypes.RESISTIVE_HEATER.get(),id);heater=null;pos=buf.readBlockPos();addDataSlots(data);}
    public ResistiveHeaterMenu(int id,Inventory inv,ResistiveHeaterBlockEntity h){super(ModMenuTypes.RESISTIVE_HEATER.get(),id);heater=h;pos=h.getBlockPos();addDataSlots(data);refresh();}
    public ContainerData data(){return data;}
    private void refresh(){if(heater!=null){data.set(0,heater.status());data.set(1,Double.isFinite(heater.temperature())?(int)Math.clamp(Math.round(heater.temperature()*10),-32767,32767):-32768);data.set(2,(int)Math.round(heater.target()*10));data.set(3,heater.drawWatts());data.set(4,heater.airPresent()?1:0);}}
    @Override public void broadcastChanges(){refresh();super.broadcastChanges();}
    @Override public boolean stillValid(Player p){return heater!=null?heater.stillValid(p):!p.isSpectator()&&p.distanceToSqr(pos.getCenter())<=64&&p.level().getBlockState(pos).getBlock() instanceof ResistiveHeaterBlock;}
    @Override public ItemStack quickMoveStack(Player p,int i){return ItemStack.EMPTY;}
}
