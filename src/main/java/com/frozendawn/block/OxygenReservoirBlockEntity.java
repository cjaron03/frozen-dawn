package com.frozendawn.block;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.*;
import com.frozendawn.oxygen.OxygenAllocation;
import com.frozendawn.world.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
/** Contents belong to this physical vessel, never a transient room ID. */
public final class OxygenReservoirBlockEntity extends BlockEntity {
    private long stored;
    private boolean connected;
    private double output, charging;
    public OxygenReservoirBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.OXYGEN_RESERVOIR.get(),p,s);}
    public long storedMicro(){return stored;}
    public double storedSeconds(){return (double)stored/OxygenAllocation.MICRO_PER_SECOND;}
    public static long capacityMicro(){return (long)FrozenDawnConfig.O2_RESERVOIR_CAPACITY.get()*OxygenAllocation.MICRO_PER_SECOND;}
    public boolean connected(){return connected;}
    public double output(){return output;}
    public double charging(){return charging;}
    public long draw(long offered){long amount=Math.clamp(offered,0,stored);stored-=amount;if(amount>0)setChanged();return amount;}
    public int charge(int offered){int amount=(int)Math.clamp((long)offered,0,(capacityMicro()-stored)/OxygenAllocation.MICRO_PER_ITEM);stored+=amount*OxygenAllocation.MICRO_PER_ITEM;if(amount>0)setChanged();return amount;}
    public void publish(boolean ready,double out,double fill){connected=ready;output=out;charging=fill;if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l)RoomOxygenManager.registerReservoir(l,worldPosition);}
    @Override public void setRemoved(){if(level instanceof ServerLevel l)RoomOxygenManager.removeReservoir(l,worldPosition);super.setRemoved();}
    public void serverTick(){if(level instanceof ServerLevel l&&l.getGameTime()%20==0){RoomOxygenManager.registerReservoir(l,worldPosition);RoomAtmosphere.keepAlive(this);}}
    @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider registries){super.saveAdditional(t,registries);t.putLong("StoredOxygen",stored);}
    @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider registries){super.loadAdditional(t,registries);stored=Math.clamp(t.getLong("StoredOxygen"),0,capacityMicro());connected=t.getBoolean("Connected");output=t.getDouble("Output");charging=t.getDouble("Charging");}
    @Override protected void applyImplicitComponents(DataComponentInput in){super.applyImplicitComponents(in);stored=Math.clamp(in.getOrDefault(ModDataComponents.RESERVOIR_OXYGEN.get(),0L),0,capacityMicro());}
    @Override protected void collectImplicitComponents(DataComponentMap.Builder out){super.collectImplicitComponents(out);out.set(ModDataComponents.RESERVOIR_OXYGEN.get(),stored);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider r){var t=super.getUpdateTag(r);t.putLong("StoredOxygen",stored);t.putBoolean("Connected",connected);t.putDouble("Output",output);t.putDouble("Charging",charging);return t;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
