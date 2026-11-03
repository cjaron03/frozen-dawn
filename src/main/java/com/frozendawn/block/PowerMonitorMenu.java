package com.frozendawn.block;

import com.frozendawn.init.ModMenuTypes;
import com.frozendawn.power.PowerMonitorReading;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Bounded six-row pages. Values use unsigned 16-bit words for vanilla menu synchronization. */
public final class PowerMonitorMenu extends AbstractContainerMenu {
    public static final int ROWS=6, STRIDE=7, START=17, DATA_SIZE=START+ROWS*STRIDE;
    private final PowerMonitorBlockEntity monitor;
    private final BlockPos pos;
    private final ContainerData data;
    private int page;
    public PowerMonitorMenu(int id,Inventory inv,FriendlyByteBuf buf){super(ModMenuTypes.POWER_MONITOR.get(),id);monitor=null;pos=buf.readBlockPos();data=new SimpleContainerData(DATA_SIZE);addDataSlots(data);}
    public PowerMonitorMenu(int id,Inventory inv,PowerMonitorBlockEntity m){
        super(ModMenuTypes.POWER_MONITOR.get(),id);monitor=m;pos=m.getBlockPos();data=new SimpleContainerData(DATA_SIZE);addDataSlots(data);refresh();
    }
    public ContainerData data(){return data;}
    public long value(int offset,int words){long value=0;for(int i=0;i<words;i++)value|=(long)(data.get(offset+i)&65535)<<(i*16);return value;}
    public int rowKind(int row){return data.get(START+row*STRIDE);}
    public BlockPos rowPos(int row){int i=START+row*STRIDE;return new BlockPos((int)value(i+1,2),(int)value(i+3,2),(int)value(i+5,2));}
    private void put(int offset,long value,int words){for(int i=0;i<words;i++)data.set(offset+i,(int)(value>>>(16*i))&65535);}
    public void refresh(){
        if(monitor==null)return;var r=monitor.reading();int pages=Math.max(1,(r.shed().size()+ROWS-1)/ROWS);page=Math.clamp(page,0,pages-1);
        put(0,r.supplyWatts(),2);put(2,r.demandWatts(),2);put(4,r.storedJoules(),4);put(8,r.capacityJoules(),4);
        data.set(12,r.status());data.set(13,r.shed().size());data.set(14,page);data.set(15,pages);
        int count=Math.min(ROWS,r.shed().size()-page*ROWS);data.set(16,count);
        for(int row=0;row<ROWS;row++){
            int i=START+row*STRIDE;var load=row<count?r.shed().get(page*ROWS+row):null;
            data.set(i,load==null?0:load.kind());var p=load==null?BlockPos.ZERO:load.pos();put(i+1,p.getX(),2);put(i+3,p.getY(),2);put(i+5,p.getZ(),2);
        }
    }
    @Override public void broadcastChanges(){refresh();super.broadcastChanges();}
    @Override public boolean stillValid(Player p){return monitor!=null?monitor.stillValid(p):!p.isSpectator()&&p.distanceToSqr(pos.getCenter())<=64&&p.level().getBlockState(pos).getBlock() instanceof PowerMonitorBlock;}
    @Override public boolean clickMenuButton(Player p,int id){if(monitor==null||!stillValid(p)||(id!=0&&id!=1))return false;page+=id==0?-1:1;refresh();return true;}
    @Override public ItemStack quickMoveStack(Player p,int i){return ItemStack.EMPTY;}
}
