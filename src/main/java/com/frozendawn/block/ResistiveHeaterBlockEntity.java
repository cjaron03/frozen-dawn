package com.frozendawn.block;

import com.frozendawn.init.*;
import com.frozendawn.power.PowerNetworks;
import com.frozendawn.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** RH-5. Allocation and readings are transient; electricity is paid before heat enters the solver. */
public final class ResistiveHeaterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_WATTS=5000;
    public static final double HEAT_PER_JOULE=RoomThermalManager.BASE_HEATER_POWER/3500.0;
    private int request,draw;
    private double temperature=Double.NaN,target=20,used;
    private boolean air;
    private long second=Long.MIN_VALUE;
    public ResistiveHeaterBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.RESISTIVE_HEATER.get(),p,s);}
    public int requestWatts(){return request;}
    public int drawWatts(){return draw;}
    public double temperature(){return temperature;}
    public double target(){return target;}
    public boolean airPresent(){return air;}
    public void prepare(long tick,int watts,double sensed,double target,boolean air){
        if(second!=tick){second=tick;used=0;draw=0;}
        request=Math.clamp(watts,0,MAX_WATTS);temperature=sensed;this.target=target;this.air=air;
    }
    public void reading(double sensed,double target,boolean air){temperature=sensed;this.target=target;this.air=air;}
    public void paid(double joules){used+=joules;draw=(int)Math.ceil(used-1e-9);}
    public int status(){return !Double.isFinite(temperature)?0:draw>0?(air?2:3):request>0?1:4;}
    public void publish(){if(level!=null){boolean active=draw>0;var s=getBlockState();if(s.getValue(ResistiveHeaterBlock.LIT)!=active)level.setBlock(worldPosition,s.setValue(ResistiveHeaterBlock.LIT,active),3);}}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l)PowerNetworks.heaterLoaded(l,worldPosition);}
    @Override public void setRemoved(){if(level instanceof ServerLevel l)PowerNetworks.heaterRemoved(l,worldPosition);super.setRemoved();}
    public void serverTick(){if(level instanceof ServerLevel l){
        if(draw>0)com.frozendawn.event.BlindArchitectEvents.machine(l,worldPosition);
        if(l.getGameTime()%20==0)RoomAtmosphere.keepAlive(this);
    }}
    @Override protected void loadAdditional(net.minecraft.nbt.CompoundTag t,net.minecraft.core.HolderLookup.Provider r){super.loadAdditional(t,r);request=draw=0;used=0;temperature=Double.NaN;target=20;air=false;second=Long.MIN_VALUE;}
    public boolean stillValid(Player p){return !p.isSpectator()&&level!=null&&level.getBlockEntity(worldPosition)==this&&p.distanceToSqr(worldPosition.getCenter())<=64;}
    @Override public Component getDisplayName(){return Component.translatable("block.frozendawn.resistive_heater");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new ResistiveHeaterMenu(id,inv,this);}
}
