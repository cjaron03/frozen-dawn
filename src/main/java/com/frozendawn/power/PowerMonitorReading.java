package com.frozendawn.power;

import java.util.List;
import net.minecraft.core.BlockPos;

/** Immutable last-allocation display state. Inspecting it never transfers energy. */
public record PowerMonitorReading(boolean connected, int supplyWatts, int generationWatts,
        int demandWatts, long storedJoules, long capacityJoules, List<ShedLoad> shed) {
    public static final int UNLINKED=0, NORMAL=1, BACKUP=2, BROWNOUT=3, NO_POWER=4;
    public static final PowerMonitorReading EMPTY=new PowerMonitorReading(false,0,0,0,0,0,List.of());
    public record ShedLoad(int kind, BlockPos pos) {}
    public PowerMonitorReading { shed=List.copyOf(shed); }
    public int status() {
        if(!connected)return UNLINKED;
        if(supplyWatts==0&&storedJoules==0)return NO_POWER;
        if(!shed.isEmpty())return BROWNOUT;
        return generationWatts==0&&demandWatts>0?BACKUP:NORMAL;
    }
    public int comparator() {return storedJoules<=0||capacityJoules<=0?0:1+(int)(14*Math.min(storedJoules,capacityJoules)/capacityJoules);}
}
