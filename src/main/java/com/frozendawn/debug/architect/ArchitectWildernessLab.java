package com.frozendawn.debug.architect;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.util.*;

/** Explicit operator controls. Preparation advances even while simulation ticks are frozen. */
@EventBusSubscriber(modid=FrozenDawn.MOD_ID)
public final class ArchitectWildernessLab {
    private static final ResourceKey<Level> DIMENSION=ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,"architect_wilderness_lab"));
    private static final Map<MinecraftServer,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,ReturnPoint> RETURNS=new HashMap<>();
    private record ReturnPoint(ResourceKey<Level> dimension,Vec3 pos,float yaw,float pitch,GameType mode){}
    private static final class Session {
        final ServerLevel level;
        CommandSourceStack owner;
        long seed=1337;
        ArchitectWildernessRun.Scenario scenario=ArchitectWildernessRun.Scenario.SURFACE;
        ArchitectWildernessTerrain terrain;
        ArchitectWildernessRun run;
        boolean preparing;
        long nextNotice;
        Session(ServerLevel level,CommandSourceStack owner){this.level=level;this.owner=owner;}
    }
    private ArchitectWildernessLab(){}
    public static boolean isActive(MinecraftServer server){Session s=SESSIONS.get(server);return s!=null&&(s.preparing||(s.run!=null&&s.run.running()));}
    public static List<String> scenarios(){return Arrays.stream(ArchitectWildernessRun.Scenario.values()).map(ArchitectWildernessRun.Scenario::id).toList();}

    public static int command(CommandSourceStack source,String operation,String argument){
        try{
            MinecraftServer server=source.getServer();
            if(operation.equals("leave"))return leave(source);
            ServerLevel level=server.getLevel(DIMENSION);
            if(level==null)throw new IllegalStateException("Restart the client/server to load the wilderness lab dimension");
            Session session=SESSIONS.get(server);
            if(operation.equals("setup")){
                if(session==null){session=new Session(level,source);SESSIONS.put(server,session);}
                session.owner=source;
                if(!argument.isEmpty())session.seed=Long.parseLong(argument);
                reset(session);
                return 1;
            }
            if(session==null)throw new IllegalStateException("Use /fd architect wilderness setup first");
            session.owner=source;
            if(operation.equals("inspect")){
                ArchitectLab.reply(source,session.preparing?session.terrain.progress():session.run==null?"Preparation failed; use reset":session.run.status()+": "+session.run.reason()+" ("+session.run.elapsed()+" ticks)");return 1;
            }
            if(session.preparing)throw new IllegalStateException("Preparation is in progress; use wilderness inspect");
            if(operation.equals("seed")){session.seed=Long.parseLong(argument);reset(session);return 1;}
            if(operation.equals("scenario")){session.scenario=ArchitectWildernessRun.Scenario.valueOf(argument.toUpperCase(Locale.ROOT));reset(session);return 1;}
            if(operation.equals("reset")){reset(session);return 1;}
            if(session.run==null)throw new IllegalStateException("Use wilderness reset to prepare the terrain");
            switch(operation){
                case "run"->{
                    if(source.getLevel()!=level)throw new IllegalStateException("Use wilderness tp before starting");
                    freeze(server);
                    session.run.begin(source.getEntity() instanceof ServerPlayer p?p:null);
                    ArchitectLab.reply(source,"Wilderness "+session.scenario.id()+" running; /tick unfreeze to watch, /tick freeze then wilderness dump to inspect. Duration "+session.scenario.duration/1200+" minutes.");
                }
                case "dump"->ArchitectLab.reply(source,"Wilderness report: "+session.run.export());
                case "stop"->{
                    if(session.run.status().equals("PREPARED"))throw new IllegalStateException("The wilderness run has not started");
                    session.run.finish("ABORTED","Stopped by operator");freeze(server);ArchitectLab.reply(source,"Stopped; report: "+session.run.export());
                }
                case "tp"->visit(source,session.run.observation());
                default->throw new IllegalArgumentException("Unknown wilderness operation "+operation);
            }
            return 1;
        }catch(IOException|IllegalArgumentException|IllegalStateException error){ArchitectLab.reply(source,"WILDERNESS FAILED: "+error.getMessage());return 0;}
    }

    private static void reset(Session session)throws IOException{
        if(session.preparing)throw new IllegalStateException("Preparation is already in progress");
        if(ArchitectLab.isRunning(session.level.getServer()))throw new IllegalStateException("Finish or reset the small lab's active run before preparing wilderness");
        freeze(session.level.getServer());
        if(session.run!=null){
            if(session.run.running()){session.run.finish("ABORTED","Wilderness reset");session.run.export();}
            session.run.dispose();session.run=null;
        }
        if(session.terrain!=null)session.terrain.release();
        // This dimension is reserved for the lab. Clean only explicitly tagged actors.
        List<net.minecraft.world.entity.Entity> leftovers=new ArrayList<>();
        for(var entity:session.level.getAllEntities())if(entity.getTags().contains("fd_wilderness"))leftovers.add(entity);
        for(var entity:leftovers){if(entity instanceof ArchitectEntity actor)actor.discardLabActor();else entity.discard();}
        session.terrain=new ArchitectWildernessTerrain(session.level,session.seed);
        session.preparing=true;session.nextNotice=0;
        ArchitectDebugReports.invalidate(ArchitectLab.reports(session.level).resolve("wilderness"),"pending","PREPARING","Restoring native terrain and building wilderness recipe");
        ArchitectLab.reply(session.owner,"Preparing 128×128 wilderness, seed "+session.seed+", scenario "+session.scenario.id()+". Native terrain snapshots are preserved for replay. Building proceeds while ticks are frozen.");
    }

    private static void visit(CommandSourceStack source,Vec3 position){
        if(!(source.getEntity() instanceof ServerPlayer p))return;
        RETURNS.putIfAbsent(p.getUUID(),new ReturnPoint(p.level().dimension(),p.position(),p.getYRot(),p.getXRot(),p.gameMode.getGameModeForPlayer()));
        p.setGameMode(GameType.SPECTATOR);
        p.teleportTo(source.getServer().getLevel(DIMENSION),position.x,position.y,position.z,Set.of(),p.getYRot(),p.getXRot());
        ArchitectLab.reply(source,"Wilderness observation point. Use /fd architect wilderness leave to return. Player scenario requires switching to Survival or Adventure before run.");
    }
    private static int leave(CommandSourceStack source)throws IOException{
        if(!(source.getEntity() instanceof ServerPlayer p))throw new IllegalStateException("A player must use leave");
        ReturnPoint saved=RETURNS.get(p.getUUID());
        if(saved==null)throw new IllegalStateException("No return position for this session");
        ServerLevel destination=source.getServer().getLevel(saved.dimension);
        if(destination==null)throw new IllegalStateException("Return dimension is unavailable");
        Session session=SESSIONS.get(source.getServer());
        if(session!=null){
            if(session.run!=null&&session.run.running()){session.run.finish("ABORTED","Operator left the lab");ArchitectLab.reply(source,"Report: "+session.run.export());}
            if(session.run!=null)session.run.dispose();
            if(session.terrain!=null)session.terrain.release();
            SESSIONS.remove(source.getServer());
        }
        RETURNS.remove(p.getUUID());
        p.teleportTo(destination,saved.pos.x,saved.pos.y,saved.pos.z,Set.of(),saved.yaw,saved.pitch);p.setGameMode(saved.mode);
        return 1;
    }
    private static void freeze(MinecraftServer server){
        var clock=server.tickRateManager();if(clock.isSprinting())clock.stopSprinting();if(clock.isSteppingForward())clock.stopStepping();clock.setFrozen(true);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event){
        Session s=SESSIONS.get(event.getServer());if(s==null)return;
        try{
            if(s.preparing){
                if(s.terrain.prepareTick()){
                    // Reloaded tagged entities may only become visible after snapshot chunks load.
                    List<net.minecraft.world.entity.Entity> old=new ArrayList<>();
                    for(var e:s.level.getAllEntities())if(e.getTags().contains("fd_wilderness"))old.add(e);
                    for(var e:old){if(e instanceof ArchitectEntity a)a.discardLabActor();else e.discard();}
                    s.run=new ArchitectWildernessRun(s.terrain,s.scenario);s.preparing=false;
                    ArchitectDebugReports.invalidate(ArchitectLab.reports(s.level).resolve("wilderness"),s.run.id.toString(),"PREPARED","Recording has not started");
                    visit(s.owner,s.run.observation());
                    ArchitectLab.reply(s.owner,"Wilderness ready: "+s.scenario.id()+", seed "+s.seed+". Start with /fd architect wilderness run, then /tick unfreeze.");
                }else if(System.currentTimeMillis()>=s.nextNotice){s.nextNotice=System.currentTimeMillis()+5000;ArchitectLab.reply(s.owner,s.terrain.progress());}
            }else if(s.run!=null&&s.run.running()){
                s.run.tick();
                if(s.run.finished()){freeze(event.getServer());ArchitectLab.reply(s.owner,s.run.status()+": "+s.run.reason()+"; report: "+s.run.export());}
            }
        }catch(Exception error){
            s.preparing=false;freeze(event.getServer());
            if(s.run!=null&&s.run.running())s.run.finish("HARNESS_ERROR",error.toString());
            if(s.terrain!=null)s.terrain.release();
            try{ArchitectDebugReports.invalidate(ArchitectLab.reports(s.level).resolve("wilderness"),s.run==null?"pending":s.run.id.toString(),"FAILED",error.toString());}catch(IOException ignored){}
            ArchitectLab.reply(s.owner,"Wilderness preparation/run error: "+error+". Use reset after correcting the issue.");
            com.mojang.logging.LogUtils.getLogger().error("Wilderness lab failed",error);
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event){
        Session s=SESSIONS.remove(event.getServer());if(s==null)return;
        try{if(s.run!=null&&s.run.running()){s.run.finish("ABORTED","Server stopped");s.run.export();}}
        catch(IOException error){com.mojang.logging.LogUtils.getLogger().error("Could not export wilderness shutdown",error);}
        finally{if(s.terrain!=null)s.terrain.release();RETURNS.clear();}
    }
}
