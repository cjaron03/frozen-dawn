package com.frozendawn.block;
import com.frozendawn.data.ThermostatOrderState;
import com.frozendawn.init.*;
import com.frozendawn.thermal.HeaterDemandDisplay;
import com.frozendawn.world.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class ThermostatBlockEntity extends BlockEntity implements MenuProvider {
    private int target=20;
    private long order;
    private boolean wasSealed;
    private ThermostatManager.Reading reading=ThermostatManager.Reading.waiting();
    private int signal;
    public ThermostatBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.THERMOSTAT.get(),p,s);}
    public int target(){return target;}
    public long order(){return order;}
    public boolean wasSealed(){return wasSealed;}
    public void markSealed(){if(!wasSealed){wasSealed=true;setChanged();}}
    public ThermostatManager.Reading reading(){return reading;}
    public void setTarget(int value){int next=Math.clamp(value,10,30);next=10+Math.round((next-10)/5f)*5;if(next!=target){target=next;setChanged();if(level instanceof ServerLevel server)ThermostatManager.refresh(server);}}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel server){var sequence=ThermostatOrderState.get(server);if(order<=0){order=sequence.allocate();setChanged();}else sequence.observe(order);ThermostatManager.register(server,worldPosition);}}
    @Override public void setRemoved(){if(level instanceof ServerLevel server)ThermostatManager.remove(server,worldPosition);super.setRemoved();}
    public void serverTick(){if(level instanceof ServerLevel server&&server.getGameTime()%20==0){RoomAtmosphere.keepAlive(this);}}
    public void publish(ThermostatManager.Reading value){reading=value;int next=comparator();if(next!=signal&&level!=null){signal=next;level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());}}
    public int comparator(){return Double.isFinite(reading.sensed())?Math.clamp((int)Math.round(reading.sensed()/30.0*15),0,15):0;}
    public ContainerData data(){return new ContainerData(){
        private final HeaterDemandDisplay display=new HeaterDemandDisplay();
        public int get(int i){double fraction=display.sample(level==null?0:level.getGameTime(),reading.duty(),reading.mode()==ThermostatManager.SEALED||reading.mode()==ThermostatManager.OPEN,reading.mode());return switch(i){
            case 0->target;case 1->tenths(reading.sensed());case 2->tenths(reading.base());case 3->reading.heaters();case 4->(int)Math.round(fraction*100);case 5->reading.mode();default->0;};}
        public void set(int i,int v){}public int getCount(){return 6;}};}
    private static int tenths(double x){return Double.isFinite(x)?(int)Math.clamp(Math.round(x*10),-32767,32767):-32768;}
    @Override public Component getDisplayName(){return Component.translatable("block.frozendawn.thermostat");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new ThermostatMenu(id,inv,this);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putInt("Target",target);tag.putLong("PlacementOrder",order);tag.putBoolean("WasSealed",wasSealed);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);target=tag.contains("Target")?10+Math.clamp(Math.round((tag.getInt("Target")-10)/5f),0,4)*5:20;order=Math.max(0,tag.getLong("PlacementOrder"));wasSealed=tag.getBoolean("WasSealed");}
}
