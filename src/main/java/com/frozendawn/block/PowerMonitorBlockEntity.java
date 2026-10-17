package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.power.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Passive wall terminal. State is rebuilt from the network, never saved as authoritative power. */
public final class PowerMonitorBlockEntity extends BlockEntity implements MenuProvider {
    private int comparator;
    public PowerMonitorBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.POWER_MONITOR.get(),p,s);}
    public PowerMonitorReading reading(){return level instanceof ServerLevel l?PowerNetworks.monitorReading(l,worldPosition):PowerMonitorReading.EMPTY;}
    public int comparator(){return comparator;}
    public void refresh(){
        if(!(level instanceof ServerLevel l))return;var reading=reading();int next=reading.comparator();
        if(comparator!=next){comparator=next;l.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());}
        int status=reading.status();if(getBlockState().getValue(PowerMonitorBlock.STATUS)!=status)l.setBlock(worldPosition,getBlockState().setValue(PowerMonitorBlock.STATUS,status),3);
    }
    public void serverTick(){if(level!=null&&level.getGameTime()%20==1)refresh();}
    public boolean stillValid(Player p){return !p.isSpectator()&&level!=null&&level.getBlockEntity(worldPosition)==this&&p.distanceToSqr(worldPosition.getCenter())<=64;}
    @Override public Component getDisplayName(){return Component.translatable("block.frozendawn.power_monitor");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new PowerMonitorMenu(id,inv,this);}
}
