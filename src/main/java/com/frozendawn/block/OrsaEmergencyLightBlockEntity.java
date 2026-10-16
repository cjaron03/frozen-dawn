package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.power.PowerNetworks;
import com.frozendawn.world.PowerAirAdvancements;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Ten minutes of loaded backup ticks. No network energy, oxygen, heat or sound output. */
public final class OrsaEmergencyLightBlockEntity extends BlockEntity {
    public static final int BACKUP_TICKS=20*60*10;
    private int backupTicks;
    private long lastTick=Long.MIN_VALUE;
    public OrsaEmergencyLightBlockEntity(BlockPos pos,BlockState state){super(ModBlockEntities.ORSA_EMERGENCY_LIGHT.get(),pos,state);}
    public int backupTicks(){return backupTicks;}
    public void serverTick(){
        if(!(level instanceof ServerLevel server)||lastTick==server.getGameTime())return;
        lastTick=server.getGameTime();
        var reading=PowerNetworks.monitorReading(server,worldPosition);
        boolean shedLighting=reading.shed().stream().anyMatch(load->load.kind()==1
                &&server.isLoaded(load.pos())&&server.getBlockState(load.pos()).getBlock() instanceof PanelLightBlock);
        boolean external=reading.connected()&&(reading.supplyWatts()>0||reading.storedJoules()>0)&&!shedLighting;
        int before=backupTicks;
        if(external)backupTicks=BACKUP_TICKS;
        boolean lit=!external&&backupTicks>0;
        if(lit)backupTicks--;
        if(backupTicks==0)lit=false;
        boolean changed=getBlockState().getValue(OrsaEmergencyLightBlock.LIT)!=lit;
        if(changed)
            server.setBlock(worldPosition,getBlockState().setValue(OrsaEmergencyLightBlock.LIT,lit),3);
        if(before!=backupTicks)setChanged();
        if(lit&&shedLighting&&(changed||server.getGameTime()%20==0))PowerAirAdvancements.emergencyLighting(server,worldPosition);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);tag.putInt("BackupTicks",backupTicks);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);backupTicks=Math.clamp(tag.getInt("BackupTicks"),0,BACKUP_TICKS);lastTick=Long.MIN_VALUE;
    }
}
