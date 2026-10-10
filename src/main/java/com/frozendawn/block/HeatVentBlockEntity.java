package com.frozendawn.block;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.world.RoomAtmosphere;
import com.frozendawn.world.RoomThermalManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
/** Loaded open exchangers renew the existing bounded room lease. No chunk tickets, fuel or gas. */
public final class HeatVentBlockEntity extends BlockEntity {
    public HeatVentBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.HEAT_VENT.get(),p,s);}
    public void serverTick(){
        if(!(level instanceof ServerLevel server)||server.getGameTime()%20!=0)return;
        var state=getBlockState();
        if(HeatVentBlock.active(state))RoomAtmosphere.keepAlive(this);
        int indicator=RoomThermalManager.ventIndicator(server,worldPosition);
        if(state.getValue(HeatVentBlock.INDICATOR)!=indicator)server.setBlock(worldPosition,state.setValue(HeatVentBlock.INDICATOR,indicator),3);
    }
}
