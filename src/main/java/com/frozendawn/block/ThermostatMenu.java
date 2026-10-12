package com.frozendawn.block;
import com.frozendawn.init.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
public final class ThermostatMenu extends AbstractContainerMenu {
    private final BlockPos pos;private final ContainerData data;private final ThermostatBlockEntity entity;
    public ThermostatMenu(int id,Inventory inv,FriendlyByteBuf buf){super(ModMenuTypes.THERMOSTAT.get(),id);pos=buf.readBlockPos();entity=null;data=new SimpleContainerData(6);addDataSlots(data);}
    public ThermostatMenu(int id,Inventory inv,ThermostatBlockEntity thermostat){super(ModMenuTypes.THERMOSTAT.get(),id);entity=thermostat;pos=entity.getBlockPos();data=entity.data();addDataSlots(data);}
    public ContainerData data(){return data;}
    @Override public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player p){return p.level().isLoaded(pos)&&p.level().getBlockState(pos).is(ModBlocks.THERMOSTAT.get())&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    @Override public boolean clickMenuButton(Player p,int id){if(entity==null||!stillValid(p)||p.level().getBlockEntity(pos)!=entity||(id!=0&&id!=1))return false;entity.setTarget(entity.target()+(id==0?-5:5));return true;}
}
