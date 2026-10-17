package com.frozendawn.block;

import com.frozendawn.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Slotless Plumb Line view: setting, pulse progress, cooldown, power and the last pulse's return counts. */
public final class PlumbLineMenu extends AbstractContainerMenu {
    public static final int SETTING=0, PULSING=1, ELAPSED=2, COOLDOWN=3, POWERED=4, AVAILABLE_KW=5, RESULT=6, ORES=7, VOIDS=8, STRUCTURES=9, METALS=10, DATA_SIZE=11;
    private final PlumbLineBlockEntity scanner;
    private final BlockPos pos;
    private final ContainerData data=new SimpleContainerData(DATA_SIZE);
    public PlumbLineMenu(int id,Inventory inv,FriendlyByteBuf buf){super(ModMenuTypes.PLUMB_LINE.get(),id);scanner=null;pos=buf.readBlockPos();addDataSlots(data);}
    public PlumbLineMenu(int id,Inventory inv,PlumbLineBlockEntity s){super(ModMenuTypes.PLUMB_LINE.get(),id);scanner=s;pos=s.getBlockPos();addDataSlots(data);refresh();}
    public ContainerData data(){return data;}
    public void refresh(){
        if(scanner==null)return;
        data.set(SETTING,scanner.setting().ordinal());data.set(PULSING,scanner.pulsing()?1:0);data.set(ELAPSED,scanner.elapsedTicks()/20);
        data.set(COOLDOWN,(int)Math.min(32000,(scanner.cooldownTicks()+19)/20));data.set(POWERED,scanner.powered()?1:0);
        data.set(AVAILABLE_KW,(int)Math.min(32000,scanner.availableWatts()/1000));data.set(RESULT,scanner.result());
        var c=scanner.counts();data.set(ORES,c[0]);data.set(VOIDS,c[1]);data.set(STRUCTURES,c[2]);data.set(METALS,c[3]);
    }
    @Override public void broadcastChanges(){refresh();super.broadcastChanges();}
    @Override public boolean stillValid(Player p){return scanner!=null?scanner.stillValid(p):!p.isSpectator()&&p.distanceToSqr(pos.getCenter())<=64&&p.level().getBlockState(pos).getBlock() instanceof PlumbLineBlock;}
    @Override public boolean clickMenuButton(Player p,int id){
        if(scanner==null||!stillValid(p)||!(p instanceof ServerPlayer sp))return false;
        boolean ok=scanner.press(sp,id);refresh();return ok;
    }
    @Override public ItemStack quickMoveStack(Player p,int i){return ItemStack.EMPTY;}
}
