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
    public OxygenGaugeBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.OXYGEN_GAUGE.get(),p,s);}
    public RoomOxygenManager.Reading reading(){return reading;}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel s)RoomOxygenManager.register(s,worldPosition);}
    @Override public void setRemoved(){if(level instanceof ServerLevel s)RoomOxygenManager.remove(s,worldPosition);super.setRemoved();}
    public void serverTick(){if(level instanceof ServerLevel s&&s.getGameTime()%20==0)RoomAtmosphere.keepAlive(this);}
    public void publish(RoomOxygenManager.Reading next){if(reading.equals(next))return;int old=comparator();reading=next;
        if(level!=null){level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);if(old!=comparator())level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());}}
    public int comparator(){return switch(reading.mode()){case NO_AIR,NO_SEAL,NO_OXYGEN->15;case LOW,STRAINED->8;case TRAPPED_AIR->4;default->0;};}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){var tag=super.getUpdateTag(lookup);tag.putInt("Mode",reading.mode().ordinal());tag.putLong("Room",reading.room());tag.putLong("Pool",reading.pool());
        tag.putDouble("Supply",reading.supply());tag.putDouble("Demand",reading.demand());tag.putDouble("Local",reading.localDemand());tag.putInt("Sources",reading.sources());tag.putInt("Rooms",reading.rooms());return tag;}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);
        reading=tag.contains("Mode")?new RoomOxygenManager.Reading(OxygenStatus.Mode.values()[Math.clamp(tag.getInt("Mode"),0,OxygenStatus.Mode.values().length-1)],tag.getLong("Room"),tag.getLong("Pool"),tag.getDouble("Supply"),tag.getDouble("Demand"),tag.getDouble("Local"),tag.getInt("Sources"),tag.getInt("Rooms")):RoomOxygenManager.Reading.waiting();}
}
