package com.frozendawn.debug.architect;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.world.BlockFreezer;
import com.frozendawn.world.SnowAccumulator;
import com.frozendawn.world.StructureStressTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;

/** Actual production snowfall/freezing, using a local phase and one fixed observer-sized workload. */
final class ArchitectWildernessWeather {
    private final ServerLevel level;
    private final RandomSource random;
    private final long seed;
    private final Map<BlockPos, String> observed = new HashMap<>();
    private final ArrayDeque<String> changes = new ArrayDeque<>();
    private long dropped, samples, snowCalls, freezeCalls, snowMutations;
    private int cursor;
    private float rain, thunder;
    private boolean active;

    ArchitectWildernessWeather(ServerLevel level, long seed) { this.level=level; this.seed=seed; random=RandomSource.create(seed); }

    void begin() {
        rain=level.getRainLevel(1); thunder=level.getThunderLevel(1); active=true;
        // Establish the audit baseline before mutations. Later scans are incremental.
        for (int i=0;i<128*128;i++) sampleColumn(0);
        changes.clear();
    }

    void tick(long tick) {
        if(!active)return;
        // Do not change shared level data / ApocalypseState. These are per-level rain strengths.
        level.setRainLevel(1); level.setThunderLevel(1);
        if(tick%20==0)sendWeather(1,1);
        BlockPos centre=new BlockPos(64,64,64);
        snowMutations+=SnowAccumulator.tickRegion(level,5,0.5f,tick,random,centre,63); snowCalls++;
        if(tick%2==0){BlockFreezer.tickRegion(level,5,0.5f,random,centre,63);freezeCalls++;}
        StructureStressTracker.prune(level);
        for(int i=0;i<64;i++)sampleColumn(tick);
    }

    private void sampleColumn(long tick) {
        int x=cursor%128,z=cursor/128; cursor=(cursor+1)%(128*128);
        int top=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;
        // Observe upper surface/cover changes; this is sampled evidence, not a full mutation journal.
        BlockPos key=new BlockPos(x,0,z);
        String state=top+":"+level.getBlockState(new BlockPos(x,top,z));
        String previous=observed.put(key,state); samples++;
        if(previous!=null&&!previous.equals(state)){
            if(changes.size()==4096){changes.removeFirst();dropped++;}
            changes.add(tick+"\t"+x+"\t"+z+"\t"+previous+"\t"+state+"\n");
        }
    }

    void close() {
        if(!active)return;
        active=false;level.setRainLevel(rain);level.setThunderLevel(thunder);sendWeather(rain,thunder);
    }

    private void sendWeather(float rain,float thunder) {
        for(var player:level.players()){
            player.connection.send(new ClientboundGameEventPacket(rain>0?ClientboundGameEventPacket.START_RAINING:ClientboundGameEventPacket.STOP_RAINING,0));
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE,rain));
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE,thunder));
        }
    }

    boolean observedChange(){return snowMutations>0;}
    Map<String,Object> summary(){
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("phase",5);out.put("progress",0.5);out.put("seed",seed);
        out.put("systems",List.of("SnowAccumulator","BlockFreezer","structural snow stress"));
        out.put("samplingCentre",List.of(64,64,64));out.put("radius",63);
        out.put("snowAccumulationRate",FrozenDawnConfig.SNOW_ACCUMULATION_RATE.get());
        out.put("snowMutations",snowMutations);out.put("snowDispatches",snowCalls);out.put("freezerDispatches",freezeCalls);
        out.put("surfaceSamples",samples);out.put("retainedChanges",changes.size());out.put("droppedChanges",dropped);
        out.put("audit","64 surface columns per tick; net changes include AI and natural updates; not exhaustive mutation attribution");
        return out;
    }
    String table(){return "runTick\tx\tz\tpreviousSurface\tcurrentSurface\n"+String.join("",changes);}
}
