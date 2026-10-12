package com.frozendawn.block;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.oxygen.OxygenStatus;
import com.frozendawn.world.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class OxygenGaugeBlockEntity extends BlockEntity {
    private RoomOxygenManager.Reading reading=RoomOxygenManager.Reading.waiting();
    private RoomOxygenManager.StorageReading storage=RoomOxygenManager.StorageReading.empty();
    public RoomOxygenManager.StorageReading storage(){return storage;}
    public OxygenGaugeBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.OXYGEN_GAUGE.get(),p,s);}
    public RoomOxygenManager.Reading reading(){return reading;}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel s)RoomOxygenManager.register(s,worldPosition);}
    @Override public void setRemoved(){if(level instanceof ServerLevel s)RoomOxygenManager.remove(s,worldPosition);super.setRemoved();}
    public void serverTick(){if(level instanceof ServerLevel s&&s.getGameTime()%20==0)RoomAtmosphere.keepAlive(this);}
    public void publish(RoomOxygenManager.Reading next){publish(next,RoomOxygenManager.StorageReading.empty());}
    public void publish(RoomOxygenManager.Reading next,RoomOxygenManager.StorageReading reserve){if(reading.equals(next)&&storage.equals(reserve))return;int old=comparator();reading=next;storage=reserve;
        if(level!=null){level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);if(old!=comparator())level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());}}
    public int comparator(){return switch(reading.mode()){case NO_AIR,NO_SEAL,NO_OXYGEN->15;case LOW,STRAINED,BREATHING_SHORTAGE,ON_RESERVE->8;case TRAPPED_AIR->4;default->0;};}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){var tag=super.getUpdateTag(lookup);tag.putInt("Mode",reading.mode().ordinal());tag.putLong("Room",reading.room());tag.putLong("Pool",reading.pool());
        tag.putDouble("Supply",reading.supply());tag.putDouble("Demand",reading.demand());tag.putDouble("Local",reading.localDemand());tag.putInt("Sources",reading.sources());tag.putInt("Rooms",reading.rooms());tag.putDouble("Stored",storage.stored());tag.putDouble("Capacity",storage.capacity());tag.putDouble("Output",storage.output());tag.putDouble("Charging",storage.charging());tag.putInt("Tanks",storage.tanks());tag.putBoolean("Connected",storage.connected());tag.putDouble("ReserveSeconds",storage.secondsRemaining());return tag;}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);
        reading=tag.contains("Mode")?new RoomOxygenManager.Reading(OxygenStatus.Mode.values()[Math.clamp(tag.getInt("Mode"),0,OxygenStatus.Mode.values().length-1)],tag.getLong("Room"),tag.getLong("Pool"),tag.getDouble("Supply"),tag.getDouble("Demand"),tag.getDouble("Local"),tag.getInt("Sources"),tag.getInt("Rooms")):RoomOxygenManager.Reading.waiting();
        storage=tag.contains("Tanks")?new RoomOxygenManager.StorageReading(tag.getDouble("Stored"),tag.getDouble("Capacity"),tag.getDouble("Output"),tag.getDouble("Charging"),tag.getInt("Tanks"),tag.getBoolean("Connected"),tag.getDouble("ReserveSeconds")):RoomOxygenManager.StorageReading.empty();}
}
