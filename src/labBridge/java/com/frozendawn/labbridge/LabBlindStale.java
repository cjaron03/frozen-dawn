package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.blind.BlindSignalMemory.Sense;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;

/** Ordinary contact decay behind a real sealed barrier; never clears or supplies a signal. */
@net.neoforged.fml.common.EventBusSubscriber(modid="frozendawn")
public final class LabBlindStale {
    private static final Gson JSON=new Gson();
    private static final Vec3 HOME=new Vec3(17.5,-44,5.5),ACTOR=new Vec3(15.5,-44,5.5);
    private static volatile LabBlindStale current;
    private final MinecraftServer server;
    private final ServerLevel level;
    private final UUID actor,player;
    private final ArchitectEntity original;
    private final Path directory;
    private final JsonArray client=new JsonArray(),keys=new JsonArray(),trace=new JsonArray(),events=new JsonArray();
    private JsonObject latestClient;
    private String phase="PREPARED",failure;
    private int ticks,phaseTicks,stable,idleTail;
    private long lastTick=Long.MIN_VALUE,closedAt,contactObserved;
    private boolean lostLock,searched,expired;
    private LabBlindStale(ServerPlayer p,ArchitectEntity a,Path predecessor)throws Exception{
        server=p.getServer();level=p.serverLevel();player=p.getUUID();actor=a.getUUID();original=a;
        directory=server.getWorldPath(LevelResource.ROOT).resolve("blind-lab-stale/attempt-"+UUID.randomUUID());Files.createDirectories(directory);
        var start=new JsonObject();start.addProperty("actor",actor.toString());start.addProperty("player",player.toString());start.addProperty("gameTime",level.getGameTime());start.addProperty("sourceRun",53);start.addProperty("predecessor",predecessor.getFileName().toString());start.addProperty("sealedTicks",360);start.addProperty("idleTailRequired",40);start.addProperty("note","Same actor; actual fresh echo then sealed barrier. No contact clearing, actor reset, teleport, synthetic sensing or tick acceleration during capture.");
        Files.writeString(directory.resolve("start.json"),start+"\n",StandardOpenOption.CREATE_NEW);Files.writeString(directory.resolve("events.jsonl"),"",StandardOpenOption.CREATE_NEW);
        var hashes=new JsonObject();for(String n:List.of("start.json","predecessor.json","events.jsonl","server.jsonl","client.jsonl","keys.jsonl","summary.json"))hashes.addProperty(n,hash(predecessor.resolve(n)));
        Files.writeString(directory.resolve("predecessor.json"),hashes+"\n",StandardOpenOption.CREATE_NEW);
    }
    private static int score(ServerLevel l,String n){var o=l.getScoreboard().getObjective("blind_vib");if(o==null)return -1;var v=l.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(n),o);return v==null?-1:v.value();}
    private static void score(ServerLevel l,String n,int v){l.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(n),l.getScoreboard().getObjective("blind_vib")).set(v);}
    private static String hash(Path p)throws Exception{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    public static int prepare(CommandSourceStack s){
        if(current!=null&&current.server==s.getServer()||!(s.getEntity() instanceof ServerPlayer p)||!LabBlindReplay.allowed(s,"vibration_lab")
            ||p.getPersistentData().contains("BlindLabStaleAttempt")||score(s.getLevel(),"#stale")!=-1||score(s.getLevel(),"#unload")!=2
            ||p.getHealth()!=20||p.position().distanceToSqr(HOME)>.000001||!s.getLevel().getForcedChunks().isEmpty()||LabBlindDecoy.unloadReady(s)!=1)return 0;
        try{
            var a=(ArchitectEntity)s.getLevel().getEntity(p.getPersistentData().getUUID("BlindLabDecoyActor"));
            String prior=p.getPersistentData().getString("BlindLabUnloadRepeat");if(!prior.matches("attempt-[0-9a-f-]{36}"))return 0;
            Path predecessor=s.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-unload").resolve(prior);
            if(a==null||a.getHealth()!=40||a.position().distanceToSqr(ACTOR)>.001||!a.blindController().tracks().isEmpty()||a.blindController().signal()!=null||a.getTarget()!=null
                ||!LabBlindUnload.completedProof(predecessor,a.getUUID(),p.getUUID()))return 0;
            current=new LabBlindStale(p,a,predecessor);p.getPersistentData().putString("BlindLabStaleAttempt",current.directory.getFileName().toString());
            score(s.getLevel(),"#stage",4);score(s.getLevel(),"#stale",0);
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal("STALE CONTACT: pause. Find, seal, search, forget; stay still until VIEW COMPLETE."));return 1;
        }catch(Exception failed){s.sendFailure(net.minecraft.network.chat.Component.literal("Stale preparation failed; preserve, do not retry."));return 0;}
    }
    public static int start(CommandSourceStack s){var c=current;if(c==null||c.server!=s.getServer()||!c.phase.equals("PREPARED")||!(s.getEntity() instanceof ServerPlayer p)||!p.getUUID().equals(c.player)||!LabBlindReplay.allowed(s,"vibration_lab")||score(c.level,"#stage")!=4||score(c.level,"#stale")!=0||!LabBlindMeleeObstruction.geometry(c.level,true)||!c.original.isNoAi())return 0;
        c.phase="GROUND";score(c.level,"#stale",1);p.setGameMode(GameType.SURVIVAL);p.sendSystemMessage(net.minecraft.network.chat.Component.literal("GROUND CHECK: stay still. Contact and sealing are automatic."));return 1;}
    public static boolean active(MinecraftServer s){var c=current;return c!=null&&c.server==s&&!Set.of("PREPARED","COMPLETE","INCONCLUSIVE").contains(c.phase);}
    public static synchronized void client(MinecraftServer s,JsonObject row,boolean key){var c=current;if(!active(s))return;var rows=key?c.keys:c.client;if(rows.size()>=8192){c.failure="INPUT_OVERFLOW";return;}rows.add(row.deepCopy());if(!key)c.latestClient=row.deepCopy();}
    private static boolean quiet(JsonObject c){try{return c.get("focused").getAsBoolean()&&!c.get("screenOpen").getAsBoolean()&&!c.get("paused").getAsBoolean()&&c.has("windowKeys")&&c.getAsJsonObject("mapped").entrySet().stream().noneMatch(e->e.getValue().getAsBoolean())&&c.getAsJsonObject("windowKeys").entrySet().stream().noneMatch(e->e.getValue().getAsBoolean())&&!c.get("useDown").getAsBoolean();}catch(Exception e){return false;}}
    private JsonObject frame(ArchitectEntity a){
        var f=new JsonObject();f.addProperty("timer",ticks);f.addProperty("phaseTimer",phaseTicks);f.addProperty("phase",phase);f.addProperty("gameTime",level.getGameTime());f.addProperty("actor",actor.toString());f.addProperty("actorPresent",a!=null);f.addProperty("geometryIntact",LabBlindMeleeObstruction.geometry(level,!phase.equals("CONTACT")));f.addProperty("barrierClosed",level.getBlockState(new BlockPos(16,-44,5)).is(Blocks.GLASS));
        var p=server.getPlayerList().getPlayer(player);if(p!=null){f.addProperty("player",player.toString());f.add("playerPosition",JSON.toJsonTree(p.position()));f.addProperty("playerHealth",p.getHealth());f.addProperty("playerOnGround",p.onGround());}
        if(a!=null){f.addProperty("health",a.getHealth());f.addProperty("noAI",a.isNoAi());f.add("position",JSON.toJsonTree(a.position()));f.add("tracks",JSON.toJsonTree(a.blindController().tracks()));f.add("selected",JSON.toJsonTree(a.blindController().signal()));f.addProperty("lock",a.blindController().signal()!=null&&a.blindController().signal().lock(level.getGameTime()));f.addProperty("diagnostics",a.blindController().diagnostics());f.addProperty("targetPresent",a.getTarget()!=null);f.addProperty("mining",a.isMiningBlock());f.addProperty("breaks",a.successfulBreakCount());f.addProperty("headYaw",a.getYHeadRot());f.addProperty("pitch",a.getXRot());}
        return f;
    }
    private void event(String kind,ArchitectEntity a){var f=frame(a);f.addProperty("event",kind);events.add(f);try{Files.writeString(directory.resolve("events.jsonl"),f+"\n",StandardOpenOption.APPEND);}catch(Exception e){failure="EVENT_ARCHIVE_FAILED";}}
    private void gap(boolean open){level.setBlock(new BlockPos(16,-44,5),(open?Blocks.AIR:Blocks.GLASS).defaultBlockState(),3);level.setBlock(new BlockPos(16,-43,5),open?Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP):Blocks.GLASS.defaultBlockState(),3);}
    private boolean fresh(ArchitectEntity a,ServerPlayer p){var x=a.blindController().signal();return x!=null&&x.sense()==Sense.ECHO&&p.getUUID().equals(x.subject())&&x.lock(level.getGameTime())&&level.getGameTime()-x.observed()<=25&&x.travel()<=8;}
    public static synchronized void tick(MinecraftServer s){var c=current;if(active(s)&&!s.isPaused())c.tick();}
    private void tick(){long now=level.getGameTime();if(now<=lastTick)return;lastTick=now;ticks++;phaseTicks++;
        var p=server.getPlayerList().getPlayer(player);var entity=level.getEntity(actor);var a=entity instanceof ArchitectEntity x?x:null;trace.add(frame(a));
        if(p==null||server.getPlayerList().getPlayerCount()!=1||p.serverLevel()!=level||score(level,"#run")!=53||score(level,"#stage")!=4||score(level,"#stale")!=1){finish("PARTICIPANT_OR_SCENE_CHANGED",p,a);return;}
        if(failure!=null){finish(failure,p,a);return;}if(ticks>3000){finish("VIEW_TIMEOUT",p,a);return;}
        if(a!=original||a.getHealth()!=40||p.getHealth()!=20||!a.isAlive()||!a.isBlindArchitect()||a.position().distanceToSqr(ACTOR)>.001||a.getTarget()!=null||a.isMiningBlock()||a.successfulBreakCount()!=0){finish("ACTOR_OR_HEALTH_CHANGED",p,a);return;}
        if(p.position().distanceToSqr(HOME)>.000001||!LabBlindMeleeObstruction.geometry(level,!phase.equals("CONTACT"))||!LabBlindIsolation.sourcesClean(level)||!LabBlindIsolation.unrelated(level).isEmpty()){finish("OBSERVER_GEOMETRY_OR_SOURCE_CHANGED",p,a);return;}
        if(latestClient!=null&&!quiet(latestClient)){finish("INPUT_NOT_QUIET",p,a);return;}
        switch(phase){
            case "GROUND" -> {boolean grounded=a.isNoAi()&&p.onGround()&&latestClient!=null&&quiet(latestClient)&&latestClient.get("onGround").getAsBoolean()&&latestClient.get("player").getAsString().equals(player.toString())&&Math.abs(now-latestClient.get("gameTime").getAsLong())<=2;stable=grounded?stable+1:0;
                if(stable>=40){event("GROUND_READY",a);gap(true);a.setNoAi(false);phase="CONTACT";phaseTicks=0;event("APERTURE_OPENED",a);p.sendSystemMessage(net.minecraft.network.chat.Component.literal("VIEW STARTED: watch for contact, then automatic sealing."));}
                else if(phaseTicks>2400)finish("GROUND_TIMEOUT",p,a);}
            case "CONTACT" -> {if(a.isNoAi()){finish("AI_HELD_DURING_CONTACT",p,a);return;}
                if(fresh(a,p)){contactObserved=a.blindController().signal().observed();event("FRESH_CONTACT",a);gap(false);closedAt=now;phase="SEALED";phaseTicks=0;event("BARRIER_SEALED",a);p.sendSystemMessage(net.minecraft.network.chat.Component.literal("BARRIER CLOSED: stay still. Watch it search and settle."));}
                else if(phaseTicks>480)finish("NO_FRESH_CONTACT",p,a);}
            case "SEALED" -> {if(a.isNoAi()){finish("AI_HELD_DURING_DECAY",p,a);return;}
                if(a.blindController().tracks().stream().anyMatch(x->x.observed()>closedAt)){finish("CONTACT_REFRESHED_AFTER_SEAL",p,a);return;}
                var x=a.blindController().signal();if(!lostLock&&(x==null||!x.lock(now))){lostLock=true;event("LOCK_EXPIRED",a);}if(!searched&&a.blindController().diagnostics().startsWith("blind=SEARCH ")){searched=true;event("SEARCH_STARTED",a);}
                boolean idle=x==null&&a.blindController().tracks().isEmpty()&&a.blindController().diagnostics().startsWith("blind=WANDER ");idleTail=idle?idleTail+1:0;
                if(idle&&!expired){expired=true;event("CONTACT_MEMORY_EMPTY",a);}
                if(phaseTicks>=360)finish(lostLock&&searched&&expired&&idleTail>=40?null:"EXPIRY_NOT_PROVEN",p,a);}
            default -> finish("UNKNOWN_PHASE",p,a);
        }
    }
    @net.neoforged.bus.api.SubscribeEvent public static synchronized void stopped(net.neoforged.neoforge.event.server.ServerStoppingEvent e){var c=current;if(c!=null&&c.server==e.getServer()){if(active(c.server))c.finish("SESSION_CLOSED",c.server.getPlayerList().getPlayer(c.player),c.level.getEntity(c.actor) instanceof ArchitectEntity a?a:null);current=null;}}
    private void finish(String reason,ServerPlayer p,ArchitectEntity a){
        if(reason==null&&(client.isEmpty()||!keys.isEmpty()||java.util.stream.StreamSupport.stream(client.spliterator(),false).anyMatch(x->!quiet(x.getAsJsonObject()))))reason="INPUT_UNQUALIFIED";
        failure=reason;gap(false);if(a!=null)a.setNoAi(true);if(p!=null){p.setGameMode(GameType.CREATIVE);p.sendSystemMessage(net.minecraft.network.chat.Component.literal("VIEW COMPLETE: pause, then describe what you saw. Result: "+(reason==null?"captured":"inconclusive")));}
        phase=reason==null?"COMPLETE":"INCONCLUSIVE";score(level,"#stale",reason==null?2:3);score(level,"#stage",reason==null?3:4);
        try{for(var e:Map.of("server.jsonl",trace,"client.jsonl",client,"keys.jsonl",keys).entrySet()){var b=new StringBuilder();e.getValue().forEach(x->b.append(x).append('\n'));Files.writeString(directory.resolve(e.getKey()),b,StandardOpenOption.CREATE_NEW);}
            var o=new JsonObject();o.addProperty("status",phase);o.addProperty("reason",reason);o.addProperty("actor",actor.toString());o.addProperty("player",player.toString());o.addProperty("ticks",ticks);o.addProperty("closedAt",closedAt);o.addProperty("contactObserved",contactObserved);o.addProperty("lostLock",lostLock);o.addProperty("searched",searched);o.addProperty("expired",expired);o.addProperty("idleTail",idleTail);o.addProperty("serverFrames",trace.size());o.addProperty("clientFrames",client.size());o.addProperty("keyEvents",keys.size());o.addProperty("limits","Same authored stationary cells; ordinary AI throughout decay. Independent input and raw-stream acceptance still required.");var hashes=new JsonObject();for(String n:List.of("start.json","predecessor.json","events.jsonl","server.jsonl","client.jsonl","keys.jsonl"))hashes.addProperty(n,hash(directory.resolve(n)));o.add("hashes",hashes);Files.writeString(directory.resolve("summary.json"),o+"\n",StandardOpenOption.CREATE_NEW);
        }catch(Exception e){System.err.println("[BlindStale] Archive failed: "+e);phase="INCONCLUSIVE";score(level,"#stale",3);}
    }
}
