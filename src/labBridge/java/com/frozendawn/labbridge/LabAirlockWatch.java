package com.frozendawn.labbridge;

import com.frozendawn.airlock.AirlockSavedState;
import com.frozendawn.block.AirlockDoorBlock;
import com.frozendawn.data.RoomAirState;
import com.frozendawn.data.RoomIdentityState;
import com.frozendawn.world.RoomAtmosphere;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;

/** Passive cache evidence. Never calls inspect/view/hasAir/doorStatus, which could heal the failure. */
@EventBusSubscriber(modid="frozendawn", value=Dist.CLIENT)
public final class LabAirlockWatch {
    private static final int MAX_CHAMBERS=8, MAX_GAS_CELLS=4096;
    private static final Map<ServerLevel,String> previous=new WeakHashMap<>();
    private static final Map<ServerLevel,Long> lastTick=new WeakHashMap<>();
    private static boolean failed;
    private static long omittedEvents;
    private static int eventsThisTick;
    private static long eventTick=Long.MIN_VALUE;
    private LabAirlockWatch() {}
    private static boolean enabled(){return !failed&&!FMLEnvironment.production&&Boolean.getBoolean("frozendawn.labBridge")&&Boolean.getBoolean("frozendawn.labAirlockWatch");}
    static void reset(){previous.clear();lastTick.clear();eventsThisTick=0;eventTick=Long.MIN_VALUE;failed=false;omittedEvents=0;}
    static void tick(MinecraftServer server,LabPerformanceTrace trace){safely(()->tickUnchecked(server,trace),trace);}
    static void event(ServerLevel level,BlockPos pos,String kind,LabPerformanceTrace trace){safely(()->eventUnchecked(level,pos,kind,trace),trace);}
    private static void safely(Runnable action,LabPerformanceTrace trace){
        if(!enabled()||trace==null)return;
        try{action.run();}catch(RuntimeException error){
            failed=true;var row=new JsonObject();row.addProperty("type","airlock_watch_disabled");row.addProperty("error",error.toString());trace.record(row);
            System.err.println("[LabAirlockWatch] Disabled after diagnostic error: "+error);
        }
    }
    private static void tickUnchecked(MinecraftServer server,LabPerformanceTrace trace){
        if(!enabled()||trace==null)return;
        for(var level:server.getAllLevels()){
            if(level.players().isEmpty())continue;
            var state=capture(level);
            String signature=state.toString();long tick=level.getGameTime();
            if(signature.equals(previous.get(level))&&tick-lastTick.getOrDefault(level,Long.MIN_VALUE)<20)continue;
            previous.put(level,signature);lastTick.put(level,tick);
            trace.record(row(level,"airlock_frame",state,null));
        }
    }
    private static void eventUnchecked(ServerLevel level,BlockPos pos,String kind,LabPerformanceTrace trace){
        if(!enabled()||trace==null||!nearChamber(level,pos))return;
        long tick=level.getGameTime();if(eventTick!=tick){eventTick=tick;eventsThisTick=0;}
        if(eventsThisTick++>=32){omittedEvents++;return;} // Input spam must never overwhelm the bounded queue.
        trace.record(row(level,kind,capture(level),pos));
    }
    private static boolean nearChamber(ServerLevel level,BlockPos pos){
        return AirlockSavedState.get(level).chambers().stream().anyMatch(c->c.base.distSqr(pos)<=64||c.cells.stream().anyMatch(p->p.distSqr(pos)<=4));
    }
    private static JsonObject row(ServerLevel level,String kind,JsonObject state,BlockPos pos){
        var row=new JsonObject();row.addProperty("type",kind);row.addProperty("gameTime",level.getGameTime());
        row.addProperty("sampledAtMillis",System.currentTimeMillis());row.addProperty("dimension",level.dimension().location().toString());
        if(pos!=null)row.addProperty("position",pos.asLong());row.add("state",state);return row;
    }
    /** Reads only existing membership and gas. Public for native noninterference regression. */
    public static JsonObject capture(ServerLevel level){
        var result=new JsonObject();var chambers=new JsonArray();var saved=AirlockSavedState.get(level);
        var nearby=saved.chambers().stream().filter(c->level.players().stream().anyMatch(p->p.distanceToSqr(c.base.getCenter())<=64*64))
                .sorted(Comparator.comparingLong(c->c.id)).toList();
        result.addProperty("omittedEvents",omittedEvents);
        result.addProperty("omittedChambers",Math.max(0,nearby.size()-MAX_CHAMBERS));
        for(var c:nearby.stream().limit(MAX_CHAMBERS).toList()){
            var entry=new JsonObject();entry.addProperty("id",c.id);entry.addProperty("cells",c.cells.size());
            entry.addProperty("air",c.gas.air());entry.addProperty("capacity",c.gas.capacity());entry.addProperty("reserve",c.gas.reserve());
            entry.addProperty("mode",c.gas.mode().name());entry.addProperty("elapsed",c.gas.elapsed());entry.addProperty("waitingForPower",c.waitingForPower);
            entry.addProperty("base",c.base.asLong());entry.addProperty("outside",c.outside.asLong());
            var doors=new JsonArray();
            for(var door:c.doors.stream().sorted().toList()){
                var d=new JsonObject();d.addProperty("position",door.asLong());d.addProperty("lower",state(level,door));d.addProperty("upper",state(level,door.above()));
                if(level.isLoaded(door)&&level.getBlockState(door).getBlock() instanceof AirlockDoorBlock){
                    var normal=level.getBlockState(door).getValue(DoorBlock.FACING);
                    d.add("forward",side(level,door.relative(normal)));d.add("backward",side(level,door.relative(normal.getOpposite())));
                }
                doors.add(d);
            }
            entry.add("doors",doors);
            var boundary=new TreeSet<BlockPos>();
            for(var cell:c.cells)for(var dir:Direction.values())if(!c.cells.contains(cell.relative(dir)))boundary.add(cell.relative(dir));
            var shell=new JsonArray();for(var pos:boundary.stream().limit(256).toList()){
                var b=new JsonObject();b.addProperty("position",pos.asLong());b.addProperty("block",state(level,pos));shell.add(b);
            }
            entry.add("shell",shell);entry.addProperty("shellTruncated",boundary.size()>256);
            var occupants=new JsonArray();for(var player:level.players())if(player.distanceToSqr(c.base.getCenter())<=64*64){
                var p=new JsonObject();p.addProperty("player",player.getUUID().toString());p.addProperty("x",player.getX());p.addProperty("y",player.getY());p.addProperty("z",player.getZ());occupants.add(p);
            }
            entry.add("players",occupants);chambers.add(entry);
        }
        result.add("chambers",chambers);return result;
    }
    private static String state(ServerLevel level,BlockPos pos){return level.isLoaded(pos)?level.getBlockState(pos).toString():"UNLOADED";}
    private static JsonObject side(ServerLevel level,BlockPos pos){
        var out=new JsonObject();out.addProperty("position",pos.asLong());
        if(!level.isLoaded(pos)){out.addProperty("loaded",false);return out;}
        out.addProperty("loaded",true);out.addProperty("block",state(level,pos));
        var chamber=AirlockSavedState.get(level).at(pos);
        if(chamber!=null){out.addProperty("controlledChamber",chamber.id);out.addProperty("controlledDensity",chamber.gas.density());}
        var known=RoomIdentityState.get(level).at(pos);
        if(known==null){out.addProperty("membership","NONE");return out;}
        out.addProperty("membership",known.id());out.addProperty("membershipCells",known.cells().size());
        var cached=RoomAtmosphere.cachedRoomViews(level).stream().filter(v->v.id()==known.id()).findFirst().orElse(null);
        out.addProperty("inCache",cached!=null);if(cached!=null)out.addProperty("cachedSeal",cached.geometry().seal().name());
        if(known.cells().size()<=MAX_GAS_CELLS){
            int gas=AirlockSavedState.get(level).roomGas(level,known.cells());
            out.addProperty("cachedGas",gas);out.addProperty("cachedDensity",(double)gas/(known.cells().size()*100));
            out.addProperty("depleted",RoomAirState.get(level).isDepleted(known.cells()));
        }else out.addProperty("gasOmitted","membership exceeds passive-read size cap");
        return out;
    }
    @SubscribeEvent public static void click(PlayerInteractEvent.RightClickBlock e){
        if(e.getLevel() instanceof ServerLevel l)LabBridgeClient.airlockEvent(l,e.getPos(),"airlock_use_before");
    }
    @SubscribeEvent public static void broken(BlockEvent.BreakEvent e){
        if(e.getLevel() instanceof ServerLevel l)LabBridgeClient.airlockEvent(l,e.getPos(),"airlock_break_before");
    }
    @SubscribeEvent public static void placed(BlockEvent.EntityPlaceEvent e){
        if(e.getLevel() instanceof ServerLevel l)LabBridgeClient.airlockEvent(l,e.getPos(),"airlock_place_after");
    }
    @SubscribeEvent public static void movement(VanillaGameEvent e){
        if(e.getLevel() instanceof ServerLevel l&&(e.getVanillaEvent()==net.minecraft.world.level.gameevent.GameEvent.BLOCK_OPEN||e.getVanillaEvent()==net.minecraft.world.level.gameevent.GameEvent.BLOCK_CLOSE))
            LabBridgeClient.airlockEvent(l,BlockPos.containing(e.getEventPosition()),"airlock_door_moved");
    }
}
