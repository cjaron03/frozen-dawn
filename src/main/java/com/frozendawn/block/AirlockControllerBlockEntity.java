package com.frozendawn.block;

import com.frozendawn.airlock.AirlockManager;
import com.frozendawn.airlock.AirlockSavedState;
import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class AirlockControllerBlockEntity extends BlockEntity {
    private boolean assigned;
    private long chamberId;
    public AirlockControllerBlockEntity(BlockPos pos,BlockState state) {super(ModBlockEntities.AIRLOCK_CONTROLLER.get(),pos,state);}
    public boolean assigned() {return assigned;}
    public long chamberId() {return chamberId;}
    public void assign(long id) {assigned=true;chamberId=id;setChanged();}
    public void serverTick() {
        if(!(level instanceof ServerLevel server))return;
        var saved=AirlockSavedState.get(server);
        var c=assigned?saved.byId(chamberId):null;
        if(c==null||!c.panels.contains(worldPosition)) {
            if(server.getGameTime()%20!=0)return;
            c=AirlockManager.resolve(server,this);
        }
        if(c!=null)AirlockManager.tick(server,c);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);tag.putBoolean("assigned",assigned);tag.putLong("chamber",chamberId);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);assigned=tag.getBoolean("assigned");chamberId=tag.getLong("chamber");
    }
}
