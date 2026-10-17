package com.frozendawn.block;

import com.frozendawn.init.ModMenuTypes;
import com.frozendawn.uplink.UplinkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Slotless console view. Numbers ride vanilla data slots; the log rides the block entity update packet. */
public final class UplinkConsoleMenu extends AbstractContainerMenu {
    public static final int MODE=0, STATE=1, ELAPSED=2, TOTAL=3, COOLDOWN=4, DISH=5, POWERED=6, DEMAND_KW=7, ARCHIVE=8, BOOST=9, DATA_SIZE=10;
    private final UplinkConsoleBlockEntity console;
    private final BlockPos pos;
    private final ContainerData data=new SimpleContainerData(DATA_SIZE);
    public UplinkConsoleMenu(int id,Inventory inv,FriendlyByteBuf buf){super(ModMenuTypes.UPLINK_CONSOLE.get(),id);console=null;pos=buf.readBlockPos();addDataSlots(data);}
    public UplinkConsoleMenu(int id,Inventory inv,UplinkConsoleBlockEntity c){super(ModMenuTypes.UPLINK_CONSOLE.get(),id);console=c;pos=c.getBlockPos();addDataSlots(data);refresh();}
    public ContainerData data(){return data;}
    public BlockPos pos(){return pos;}
    public void refresh(){
        if(console==null)return;
        data.set(MODE,console.mode().ordinal());data.set(STATE,console.state().ordinal());
        data.set(ELAPSED,console.elapsedTicks()/20);data.set(TOTAL,console.totalTicks()/20);
        data.set(COOLDOWN,(int)Math.min(32000,(console.cooldownTicks()+19)/20));
        data.set(DISH,console.dishStatus());data.set(POWERED,console.powered()?1:0);data.set(DEMAND_KW,console.demandWatts()/1000);
        data.set(ARCHIVE,console.getLevel() instanceof ServerLevel l?UplinkSavedData.get(l.getServer()).archiveDelivered():0);
        data.set(BOOST,console.boost()?1:0);
    }
    @Override public void broadcastChanges(){refresh();super.broadcastChanges();}
    @Override public boolean stillValid(Player p){return console!=null?console.stillValid(p):!p.isSpectator()&&p.distanceToSqr(pos.getCenter())<=64&&p.level().getBlockState(pos).getBlock() instanceof UplinkConsoleBlock;}
    @Override public boolean clickMenuButton(Player p,int id){
        if(console==null||!stillValid(p)||!(p instanceof ServerPlayer sp))return false;
        boolean ok=console.press(sp,id);refresh();return ok;
    }
    @Override public ItemStack quickMoveStack(Player p,int i){return ItemStack.EMPTY;}
}
