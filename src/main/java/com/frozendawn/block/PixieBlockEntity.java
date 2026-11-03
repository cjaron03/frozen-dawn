package com.frozendawn.block;

import com.frozendawn.init.*;
import com.frozendawn.world.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** Water is stored in oxygen micro-units: one ice supplies 1,200 breathing seconds.
 * Each loaded second is an atomic paid production batch. Display/requests/grants are transient. */
public final class PixieBlockEntity extends BlockEntity implements WorldlyContainer,MenuProvider {
    public static final int MAX_WATTS=4000;
    public static final long BLUE_ICE_WATER=972_000_000L; //81 days at one unit.
    public static final long WATER_CAPACITY=BLUE_ICE_WATER+MAX_WATTS*10L; //One batch of refill headroom.
    public static final int PRODUCING=0,IDLE=1,NO_POWER=2,NO_WATER=3,FROZEN=4,UNSEALED=5,WAITING=6;
    private final NonNullList<ItemStack> items=NonNullList.withSize(1,ItemStack.EMPTY);
    private long water,lastSecond=Long.MIN_VALUE;
    public static final int INACTIVE=0,ROOM=1,STORAGE=2,ROOM_AND_STORAGE=3;
    private int request,output,activity,status=WAITING;
    private boolean unsealed;
    private double machineTemperature=Double.NaN,roomFeel=Double.NaN;
    public PixieBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.PIXIE.get(),p,s);}
    public static long iceWater(ItemStack s){if(s.is(Items.SNOW_BLOCK))return 3_000_000;if(s.is(Items.ICE))return 12_000_000;if(s.is(Items.PACKED_ICE))return 108_000_000;if(s.is(Items.BLUE_ICE))return BLUE_ICE_WATER;return 0;}
    public long waterMicro(){return water;}
    public long availableWater(){long fill=iceWater(items.getFirst());return water+(fill>0&&fill<=WATER_CAPACITY-water?fill:0);}
    public int requestWatts(){return request;}
    public int powerQuantum(){return unsealed?100:1;}
    public int outputWatts(){return output;}
    public int status(){return status;}
    public int activity(){return activity;}
    public double machineTemperature(){return machineTemperature;}
    public double roomFeel(){return roomFeel;}
    public void roomFeel(double temperature){roomFeel=temperature;}
    /** Actual shared-pool activity after charging has been committed; never a requested fill. */
    public void publishActivity(boolean room,boolean storage){activity=output>0?(room?ROOM:0)|(storage?STORAGE:0):INACTIVE;}
    public boolean tankOnly(){return unsealed;}
    public boolean newSecond(){return level!=null&&Math.floorDiv(level.getGameTime(),20)>lastSecond;}
    public void prepare(boolean ready,boolean open,double temperature,int needed){
        request=0;output=0;activity=INACTIVE;unsealed=open;machineTemperature=temperature;
        if(!ready||!Double.isFinite(temperature)){status=WAITING;return;}
        if(temperature<1){status=FROZEN;return;}
        if(availableWater()==0){status=NO_WATER;return;}
        request=(int)Math.min(Math.clamp(needed,0,MAX_WATTS),availableWater()/10);
        request=request/powerQuantum()*powerQuantum();
        status=request>0?NO_POWER:open?UNSEALED:IDLE;
    }
    /** Called only after the allocator has committed this batch's actual energy. */
    public void produce(int paidWatts){
        if(!newSecond())return;lastSecond=Math.floorDiv(level.getGameTime(),20);
        output=Math.clamp(paidWatts,0,request);long used=output*10L;
        if(used>water){long fill=iceWater(items.getFirst());if(fill>0&&fill<=WATER_CAPACITY-water){water+=fill;items.getFirst().shrink(1);}}
        if(used>water)throw new IllegalStateException("PIXIE allocation exceeds available water");
        water-=used;if(unsealed)publishActivity(false,output>0);if(output>0){status=unsealed?UNSEALED:PRODUCING;RoomThermalManager.addMachineHeat((ServerLevel)level,worldPosition,output*.25);}
        boolean lit=output>0;if(getBlockState().getValue(PixieBlock.LIT)!=lit)level.setBlock(worldPosition,getBlockState().setValue(PixieBlock.LIT,lit),3);
        if(lit&&level.getGameTime()%80==0){level.playSound(null,worldPosition,SoundEvents.BEACON_AMBIENT,SoundSource.BLOCKS,.18f,1.55f);level.gameEvent(null,GameEvent.BLOCK_ACTIVATE,worldPosition);}
        setChanged();
    }
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l){RoomOxygenManager.registerPixie(l,worldPosition);com.frozendawn.power.PowerNetworks.pixieLoaded(l,worldPosition);}}
    @Override public void setRemoved(){if(level instanceof ServerLevel l){RoomOxygenManager.removePixie(l,worldPosition);com.frozendawn.power.PowerNetworks.pixieRemoved(l,worldPosition);}super.setRemoved();}
    public void serverTick(){if(level instanceof ServerLevel l&&l.getGameTime()%20==0){RoomOxygenManager.registerPixie(l,worldPosition);RoomAtmosphere.keepAlive(this);}}
    @Override public Component getDisplayName(){return Component.translatable("block.frozendawn.pixie");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new PixieMenu(id,inv,this,worldPosition);}
    @Override public int getContainerSize(){return 1;}
    @Override public boolean isEmpty(){return items.getFirst().isEmpty();}
    @Override public ItemStack getItem(int i){return items.get(i);}
    @Override public ItemStack removeItem(int i,int count){var s=ContainerHelper.removeItem(items,i,count);if(!s.isEmpty())setChanged();return s;}
    @Override public ItemStack removeItemNoUpdate(int i){return ContainerHelper.takeItem(items,i);}
    @Override public void setItem(int i,ItemStack s){s.setCount(Math.min(s.getCount(),s.getMaxStackSize()));items.set(i,s);setChanged();}
    @Override public boolean canPlaceItem(int i,ItemStack s){return i==0&&iceWater(s)>0;}
    @Override public boolean stillValid(Player p){return !p.isSpectator()&&level!=null&&level.getBlockEntity(worldPosition)==this&&p.distanceToSqr(worldPosition.getCenter())<=64;}
    @Override public void clearContent(){items.clear();setChanged();}
    @Override public int[] getSlotsForFace(Direction d){return new int[]{0};}
    @Override public boolean canPlaceItemThroughFace(int i,ItemStack s,Direction d){return canPlaceItem(i,s);}
    @Override public boolean canTakeItemThroughFace(int i,ItemStack s,Direction d){return false;}
    @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider r){super.saveAdditional(t,r);ContainerHelper.saveAllItems(t,items,r);t.putLong("Water",water);t.putLong("LastSecond",lastSecond);}
    @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider r){super.loadAdditional(t,r);items.clear();ContainerHelper.loadAllItems(t,items,r);water=Math.clamp(t.getLong("Water"),0,WATER_CAPACITY);lastSecond=t.contains("LastSecond")?t.getLong("LastSecond"):Long.MIN_VALUE;request=output=activity=0;status=WAITING;machineTemperature=roomFeel=Double.NaN;}
    @Override protected void applyImplicitComponents(DataComponentInput in){super.applyImplicitComponents(in);water=Math.clamp(in.getOrDefault(ModDataComponents.PIXIE_WATER.get(),0L),0,WATER_CAPACITY);}
    @Override protected void collectImplicitComponents(DataComponentMap.Builder out){super.collectImplicitComponents(out);out.set(ModDataComponents.PIXIE_WATER.get(),water);}
}
