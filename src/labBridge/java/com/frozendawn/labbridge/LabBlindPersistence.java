package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.blind.BlindSignalMemory.Sense;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;

/** Real owner Save and Quit / process restart. Never clears or restores sensory contact. */
@net.neoforged.fml.common.EventBusSubscriber(modid="frozendawn")
public final class LabBlindPersistence {
    private static final Gson JSON=new Gson();
    private static final String PROCESS=UUID.randomUUID().toString(),MARKER="BlindLabSaveAttempt";
    private static final Vec3 HOME=new Vec3(17.5,-44,5.5),ACTOR=new Vec3(15.5,-44,5.5);
    private static final Map<String,String> REVIEWED_STALE=Map.ofEntries(Map.entry("start.json","19e704fe576ce0681e2755d1db0221a7cb486b0ad4b0e9a4152d022593ed97fe"),
        Map.entry("predecessor.json","f05c2ca654db5b9e54efcc73a7a1b340bd2e15bd1d0fde01a2405a817027d155"),
        Map.entry("events.jsonl","83e5a2a10f984ce32b30d4adce556b6fa7bb530d84a74c852b254ff386d82939"),
        Map.entry("server.jsonl","c72a54794b6f448f26a82a4169eeb543d11874e8c501ab602377829d4c69ea6c"),
        Map.entry("client.jsonl","2fc211366106d668bf2d5facdb7e693dfe75d3ceb493459bbf049553ec725ddd"),
        Map.entry("keys.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        Map.entry("summary.json","1e3307e33d2c05e9e97bbe9f1f781d5d2a242ab2184624619096e97a1148af62"));
    private static volatile LabBlindPersistence current;
    private final MinecraftServer server;
    private final ServerLevel level;
    private final UUID actor,player;
    private final ArchitectEntity original;
    private final Path root,directory;
    private final boolean after;
    private final JsonArray client=new JsonArray(),keys=new JsonArray(),trace=new JsonArray();
    private JsonObject latestClient;
    private String phase="PREPARED",failure;
    private int ticks,phaseTicks,stable;
    private long lastTick=Long.MIN_VALUE;
    private boolean reacquired;
    private LabBlindPersistence(ServerPlayer p,ArchitectEntity a,Path root,boolean after)throws Exception{
        server=p.getServer();level=p.serverLevel();player=p.getUUID();actor=a.getUUID();original=a;this.root=root;this.after=after;
        directory=after?root.resolve("after"):root;Files.createDirectory(directory);
        var start=actorFrame(a);start.addProperty("player",player.toString());start.addProperty("process",PROCESS);start.addProperty("sourceRun",53);start.addProperty("part",after?"AFTER_REOPEN":"BEFORE_SAVE");
        start.addProperty("note","Same authored stationary cells. Actual sensing, held actor during owner save/reopen, no contact injection or clearing by replay; independent raw-input acceptance required.");
        write(directory,"start.json",start);Files.writeString(directory.resolve("events.jsonl"),"",StandardOpenOption.CREATE_NEW);
    }
    private static int score(ServerLevel l,String n){var o=l.getScoreboard().getObjective("blind_vib");if(o==null)return -1;var v=l.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(n),o);return v==null?-1:v.value();}
    private static void score(ServerLevel l,String n,int v){l.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(n),l.getScoreboard().getObjective("blind_vib")).set(v);}
    private static String hash(Path p)throws Exception{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    private static JsonObject read(Path d,String n)throws Exception{return JsonParser.parseString(Files.readString(d.resolve(n))).getAsJsonObject();}
    private static void write(Path d,String n,JsonObject o)throws Exception{Files.writeString(d.resolve(n),o+"\n",StandardOpenOption.CREATE_NEW);}
    /** This one-use successor accepts only the independently reviewed immutable stale-contact attempt. */
    public static boolean reviewedStaleProof(Path d,UUID actor,UUID player){try{
        for(var e:REVIEWED_STALE.entrySet())if(!hash(d.resolve(e.getKey())).equals(e.getValue()))return false;
        var s=read(d,"summary.json");return s.get("status").getAsString().equals("COMPLETE")&&s.get("reason").isJsonNull()&&s.get("actor").getAsString().equals(actor.toString())&&s.get("player").getAsString().equals(player.toString());
    }catch(Exception e){return false;}}
    private static ArchitectEntity actor(ServerPlayer p){return p.serverLevel().getEntity(p.getPersistentData().getUUID("BlindLabDecoyActor")) instanceof ArchitectEntity a?a:null;}
    private static boolean base(CommandSourceStack s,ServerPlayer p,ArchitectEntity a){return LabBlindReplay.allowed(s,"vibration_lab")&&p.isCreative()&&p.getHealth()==20&&p.position().distanceToSqr(HOME)<.000001&&a!=null&&a.isBlindArchitect()&&a.isAlive()&&a.getHealth()==40&&a.isNoAi()&&a.position().distanceToSqr(ACTOR)<.001&&a.getTarget()==null&&a.successfulBreakCount()==0&&!a.isMiningBlock()&&LabBlindMeleeObstruction.geometry(p.serverLevel(),true)&&LabBlindIsolation.sourcesClean(p.serverLevel())&&LabBlindIsolation.unrelated(p.serverLevel()).isEmpty()&&p.serverLevel().getForcedChunks().isEmpty();}
    public static int prepare(CommandSourceStack s){if(current!=null||!(s.getEntity() instanceof ServerPlayer p)||p.getPersistentData().contains(MARKER)||score(s.getLevel(),"#persist")!=-1||score(s.getLevel(),"#stale")!=2||score(s.getLevel(),"#unload")!=2||LabBlindDecoy.unloadReady(s)!=1)return 0;
        try{var a=actor(p);if(!base(s,p,a)||!a.blindController().tracks().isEmpty()||a.blindController().signal()!=null||a.getPersistentData().contains(MARKER))return 0;
            String prior=p.getPersistentData().getString("BlindLabStaleAttempt");if(!prior.matches("attempt-[0-9a-f-]{36}"))return 0;
            Path predecessor=s.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-stale").resolve(prior);if(!reviewedStaleProof(predecessor,a.getUUID(),p.getUUID()))return 0;
            Path parent=s.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-save");Files.createDirectories(parent);Path root=parent.resolve("attempt-"+UUID.randomUUID());
            current=new LabBlindPersistence(p,a,root,false);var hashes=new JsonObject();for(String n:REVIEWED_STALE.keySet())hashes.addProperty(n,hash(predecessor.resolve(n)));write(root,"predecessor.json",hashes);
            p.getPersistentData().putString(MARKER,root.getFileName().toString());a.getPersistentData().putString(MARKER,root.getFileName().toString());score(s.getLevel(),"#stage",4);score(s.getLevel(),"#persist",0);
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal("SAVE TEST: pause. Fresh contact will be captured before Save and Quit."));return 1;
        }catch(Exception e){s.sendFailure(net.minecraft.network.chat.Component.literal("Save preparation failed; preserve, do not retry."));return 0;}}
    public static int resume(CommandSourceStack s){if(current!=null||!(s.getEntity() instanceof ServerPlayer p)||score(s.getLevel(),"#stage")!=4||score(s.getLevel(),"#persist")!=2||score(s.getLevel(),"#stale")!=2||score(s.getLevel(),"#unload")!=2||score(s.getLevel(),"#run")!=53)return 0;
        try{var a=actor(p);if(!base(s,p,a)||!a.blindController().tracks().isEmpty()||a.blindController().signal()!=null)return 0;
            String name=p.getPersistentData().getString(MARKER);if(!name.matches("attempt-[0-9a-f-]{36}")||!a.getPersistentData().getString(MARKER).equals(name))return 0;
            Path root=s.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-save").resolve(name);var join=read(root,"reopen-join.json");var stop=read(root,"shutdown.json");var start=read(root,"start.json");
            if(!qualifiedPhase(root,a.getUUID(),p.getUUID(),false)||!stop.get("heldContactRetained").getAsBoolean()||!stop.get("actor").getAsString().equals(a.getUUID().toString())||!stop.get("process").equals(start.get("process"))||!join.get("loadedFromDisk").getAsBoolean()||!join.get("process").getAsString().equals(PROCESS)||join.get("process").equals(start.get("process"))||!join.get("actor").equals(start.get("actor"))||!join.get("noAI").getAsBoolean()||!join.get("blind").getAsBoolean()||join.get("health").getAsFloat()!=40||!join.getAsJsonArray("tracks").isEmpty()||!join.get("selected").isJsonNull()||join.get("targetPresent").getAsBoolean())return 0;
            var saved=net.minecraft.nbt.TagParser.parseTag(stop.get("nbt").getAsString());var loaded=net.minecraft.nbt.TagParser.parseTag(join.get("nbt").getAsString());
            for(String k:List.of("UUID","Pos","Health","NoAI","BlindArchitect","PersistenceRequired","Tags","CustomName","NeoForgeData","BlindNoiseUntil"))if(!Objects.equals(saved.get(k),loaded.get(k)))return 0;
            current=new LabBlindPersistence(p,a,root,true);var hashes=new JsonObject();for(String n:List.of("start.json","predecessor.json","events.jsonl","server.jsonl","client.jsonl","keys.jsonl","summary.json","shutdown.json","reopen-join.json"))hashes.addProperty(n,hash(root.resolve(n)));write(current.directory,"predecessor.json",hashes);current.event("REOPEN_VERIFIED_EMPTY",a);score(s.getLevel(),"#persist",3);
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal("REOPEN VERIFIED: pause. Same actor, empty contact. Fresh sensing comes next."));return 1;
        }catch(Exception e){s.sendFailure(net.minecraft.network.chat.Component.literal("Reopen verification failed; preserve, do not retry."));return 0;}}
    public static int start(CommandSourceStack s){var c=current;if(c==null||c.server!=s.getServer()||!c.phase.equals("PREPARED")||!(s.getEntity() instanceof ServerPlayer p)||!p.getUUID().equals(c.player)||!LabBlindReplay.allowed(s,"vibration_lab")||score(c.level,"#stage")!=4||score(c.level,"#persist")!=(c.after?3:0)||!base(s,p,c.original))return 0;
        c.phase="GROUND";score(c.level,"#persist",c.after?4:1);p.setGameMode(GameType.SURVIVAL);p.sendSystemMessage(net.minecraft.network.chat.Component.literal("GROUND CHECK: stay still. The aperture opens automatically."));return 1;}
    public static boolean active(MinecraftServer s){var c=current;return c!=null&&c.server==s&&Set.of("GROUND","SEALED","CONTACT","REACQUIRE").contains(c.phase);}
    public static synchronized void client(MinecraftServer s,JsonObject row,boolean key){var c=current;if(!active(s))return;var rows=key?c.keys:c.client;if(rows.size()>=8192){c.failure="INPUT_OVERFLOW";return;}rows.add(row.deepCopy());if(!key)c.latestClient=row.deepCopy();}
    private static boolean quiet(JsonObject c){try{return c.get("focused").getAsBoolean()&&!c.get("screenOpen").getAsBoolean()&&!c.get("paused").getAsBoolean()&&c.has("windowKeys")&&c.getAsJsonObject("mapped").entrySet().stream().noneMatch(e->e.getValue().getAsBoolean())&&c.getAsJsonObject("windowKeys").entrySet().stream().noneMatch(e->e.getValue().getAsBoolean())&&!c.get("useDown").getAsBoolean()&&!c.get("flying").getAsBoolean()&&!c.get("sneaking").getAsBoolean()&&c.getAsJsonObject("input").get("forwardImpulse").getAsDouble()==0&&c.getAsJsonObject("input").get("leftImpulse").getAsDouble()==0;}catch(Exception e){return false;}}
    private static JsonObject actorFrame(ArchitectEntity a){var f=new JsonObject();f.addProperty("actor",a.getUUID().toString());f.addProperty("gameTime",a.level().getGameTime());f.addProperty("health",a.getHealth());f.addProperty("blind",a.isBlindArchitect());f.addProperty("noAI",a.isNoAi());f.add("position",JSON.toJsonTree(a.position()));f.add("tracks",JSON.toJsonTree(a.blindController().tracks()));f.add("selected",JSON.toJsonTree(a.blindController().signal()));f.addProperty("targetPresent",a.getTarget()!=null);f.addProperty("mining",a.isMiningBlock());f.addProperty("breaks",a.successfulBreakCount());f.addProperty("diagnostics",a.blindController().diagnostics());return f;}
    private JsonObject frame(ArchitectEntity a){var f=a==null?new JsonObject():actorFrame(a);f.addProperty("actor",actor.toString());f.addProperty("actorPresent",a!=null);f.addProperty("gameTime",level.getGameTime());f.addProperty("timer",ticks);f.addProperty("phaseTimer",phaseTicks);f.addProperty("phase",phase);f.addProperty("geometryIntact",LabBlindMeleeObstruction.geometry(level,!Set.of("CONTACT","REACQUIRE").contains(phase)));f.addProperty("barrierClosed",level.getBlockState(new BlockPos(16,-44,5)).is(Blocks.GLASS));var p=server.getPlayerList().getPlayer(player);if(p!=null){f.addProperty("player",player.toString());f.addProperty("playerHealth",p.getHealth());f.addProperty("playerOnGround",p.onGround());f.add("playerPosition",JSON.toJsonTree(p.position()));}return f;}
    private void event(String kind,ArchitectEntity a){var f=frame(a);f.addProperty("event",kind);try{Files.writeString(directory.resolve("events.jsonl"),f+"\n",StandardOpenOption.APPEND);}catch(Exception e){failure="EVENT_ARCHIVE_FAILED";}}
    private void gap(boolean open){level.setBlock(new BlockPos(16,-44,5),(open?Blocks.AIR:Blocks.GLASS).defaultBlockState(),3);level.setBlock(new BlockPos(16,-43,5),open?Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP):Blocks.GLASS.defaultBlockState(),3);}
    private boolean fresh(ArchitectEntity a,ServerPlayer p){var x=a.blindController().signal();return x!=null&&x.sense()==Sense.ECHO&&p.getUUID().equals(x.subject())&&x.lock(level.getGameTime())&&level.getGameTime()-x.observed()>=0&&level.getGameTime()-x.observed()<=25&&x.travel()<=8;}
    public static synchronized void tick(MinecraftServer s){var c=current;if(active(s)&&!s.isPaused())c.tick();}
    private void tick(){long now=level.getGameTime();if(now<=lastTick)return;lastTick=now;ticks++;phaseTicks++;var p=server.getPlayerList().getPlayer(player);var a=level.getEntity(actor) instanceof ArchitectEntity x?x:null;trace.add(frame(a));
        if(p==null||server.getPlayerList().getPlayerCount()!=1||p.serverLevel()!=level||score(level,"#run")!=53||score(level,"#stage")!=4||score(level,"#persist")!=(after?4:1)){finish("PARTICIPANT_OR_SCENE_CHANGED",p,a);return;}
        if(failure!=null){finish(failure,p,a);return;}if(ticks>3000){finish("VIEW_TIMEOUT",p,a);return;}
        if(a!=original||a.getHealth()!=40||p.getHealth()!=20||!a.isAlive()||!a.isBlindArchitect()||a.position().distanceToSqr(ACTOR)>.001||a.getTarget()!=null||a.isMiningBlock()||a.successfulBreakCount()!=0){finish("ACTOR_OR_HEALTH_CHANGED",p,a);return;}
        if(p.position().distanceToSqr(HOME)>.000001||!LabBlindMeleeObstruction.geometry(level,!Set.of("CONTACT","REACQUIRE").contains(phase))||!LabBlindIsolation.sourcesClean(level)||!LabBlindIsolation.unrelated(level).isEmpty()){finish("OBSERVER_GEOMETRY_OR_SOURCE_CHANGED",p,a);return;}
        if(latestClient!=null&&!quiet(latestClient)){finish("INPUT_NOT_QUIET",p,a);return;}
        switch(phase){
            case "GROUND" -> {boolean ready=a.isNoAi()&&p.onGround()&&latestClient!=null&&quiet(latestClient)&&latestClient.get("onGround").getAsBoolean()&&latestClient.get("player").getAsString().equals(player.toString())&&now-latestClient.get("gameTime").getAsLong()>=0&&now-latestClient.get("gameTime").getAsLong()<=2;stable=ready?stable+1:0;
                if(stable>=40){event("GROUND_READY",a);phase=after?"SEALED":"CONTACT";phaseTicks=0;if(!after){gap(true);a.setNoAi(false);event("APERTURE_OPENED",a);}p.sendSystemMessage(net.minecraft.network.chat.Component.literal(after?"SEALED CHECK: it must remain without contact before reopening.":"VIEW STARTED: watch for contact, then SAVE READY."));}else if(phaseTicks>2400)finish("GROUND_TIMEOUT",p,a);}
            case "SEALED" -> {if(!a.isNoAi()||!a.blindController().tracks().isEmpty()||a.blindController().signal()!=null){finish("RESTORED_CONTACT_PRESENT",p,a);return;}if(phaseTicks>=80){gap(true);a.setNoAi(false);phase="REACQUIRE";phaseTicks=0;event("APERTURE_REOPENED",a);p.sendSystemMessage(net.minecraft.network.chat.Component.literal("APERTURE OPEN: watch it find you again. Stay still until VIEW COMPLETE."));}}
            case "CONTACT" -> {if(a.isNoAi()){finish("AI_HELD_DURING_CONTACT",p,a);return;}if(fresh(a,p)){event("FRESH_CONTACT_BEFORE_SAVE",a);finish(null,p,a);}else if(phaseTicks>480)finish("NO_FRESH_CONTACT",p,a);}
            case "REACQUIRE" -> {if(a.isNoAi()){finish("AI_HELD_DURING_REACQUISITION",p,a);return;}if(!reacquired&&fresh(a,p)&&a.blindController().signal().observed()>readJoinTime()){reacquired=true;event("NEW_CONTACT_AFTER_REOPEN",a);}if(phaseTicks>=240)finish(reacquired?null:"NO_NEW_CONTACT",p,a);}
            default -> finish("UNKNOWN_PHASE",p,a);
        }
    }
    private long readJoinTime(){try{return read(root,"reopen-join.json").get("gameTime").getAsLong();}catch(Exception e){return Long.MAX_VALUE;}}
    private void finish(String reason,ServerPlayer p,ArchitectEntity a){if(reason==null&&(client.isEmpty()||!keys.isEmpty()||java.util.stream.StreamSupport.stream(client.spliterator(),false).anyMatch(x->!quiet(x.getAsJsonObject()))))reason="INPUT_UNQUALIFIED";
        failure=reason;gap(false);if(a!=null)a.setNoAi(true);if(reason==null&&!after)event("HELD_CONTACT_FOR_SAVE",a);phase=reason!=null?"INCONCLUSIVE":after?"COMPLETE":"WAITING_FOR_REOPEN";score(level,"#persist",reason!=null?6:after?5:2);score(level,"#stage",reason==null&&after?3:4);
        if(p!=null){p.setGameMode(GameType.CREATIVE);p.sendSystemMessage(net.minecraft.network.chat.Component.literal(reason!=null?"INCONCLUSIVE: pause and preserve.":after?"VIEW COMPLETE: pause, then describe what you saw.":"SAVE READY: pause and describe what you saw. Save and Quit after operator confirms capture."));}
        try{for(var e:Map.of("server.jsonl",trace,"client.jsonl",client,"keys.jsonl",keys).entrySet()){var b=new StringBuilder();e.getValue().forEach(x->b.append(x).append('\n'));Files.writeString(directory.resolve(e.getKey()),b,StandardOpenOption.CREATE_NEW);}var o=new JsonObject();o.addProperty("status",phase);o.addProperty("reason",reason);o.addProperty("actor",actor.toString());o.addProperty("player",player.toString());o.addProperty("process",PROCESS);o.addProperty("ticks",ticks);o.addProperty("serverFrames",trace.size());o.addProperty("clientFrames",client.size());o.addProperty("keyEvents",keys.size());o.addProperty("freshReacquisition",reacquired);var hashes=new JsonObject();for(String n:List.of("start.json","predecessor.json","events.jsonl","server.jsonl","client.jsonl","keys.jsonl"))hashes.addProperty(n,hash(directory.resolve(n)));o.add("hashes",hashes);write(directory,"summary.json",o);
        }catch(Exception e){System.err.println("[BlindPersistence] Archive failed: "+e);phase="INCONCLUSIVE";score(level,"#persist",6);}}
    @net.neoforged.bus.api.SubscribeEvent public static synchronized void stopped(net.neoforged.neoforge.event.server.ServerStoppingEvent e){var c=current;if(c==null||c.server!=e.getServer())return;
        if(active(c.server))c.finish("SESSION_CLOSED",c.server.getPlayerList().getPlayer(c.player),c.level.getEntity(c.actor) instanceof ArchitectEntity a?a:null);
        if(c.phase.equals("WAITING_FOR_REOPEN")){try{var a=c.level.getEntity(c.actor) instanceof ArchitectEntity x?x:null;var o=a==null?new JsonObject():actorFrame(a);o.addProperty("process",PROCESS);o.addProperty("heldContactRetained",a==c.original&&a.isNoAi()&&a.getHealth()==40&&!a.blindController().tracks().isEmpty()&&a.blindController().signal()!=null&&a.getTarget()==null&&LabBlindMeleeObstruction.geometry(c.level,true));if(a!=null){var nbt=new CompoundTag();a.save(nbt);o.addProperty("nbt",nbt.toString());}write(c.root,"shutdown.json",o);}catch(Exception ex){System.err.println("[BlindPersistence] Shutdown archive failed: "+ex);}}
        current=null;}
    @net.neoforged.bus.api.SubscribeEvent public static synchronized void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e){if(!(e.getLevel() instanceof ServerLevel l)||!(e.getEntity() instanceof ArchitectEntity a)||!Boolean.getBoolean("frozendawn.labBridge")||!l.getServer().getWorldData().getLevelName().equals("Blind Architect Reusable Lab"))return;
        String name=a.getPersistentData().getString(MARKER);if(!name.matches("attempt-[0-9a-f-]{36}"))return;
        try{Path root=l.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-save").resolve(name);var start=read(root,"start.json");if(!start.get("actor").getAsString().equals(a.getUUID().toString())||start.get("process").getAsString().equals(PROCESS))return;var o=actorFrame(a);o.addProperty("process",PROCESS);o.addProperty("loadedFromDisk",e.loadedFromDisk());var tag=new CompoundTag();a.save(tag);o.addProperty("nbt",tag.toString());write(root,"reopen-join.json",o);
        }catch(Exception ex){System.err.println("[BlindPersistence] Disk join archive failed; preserve: "+ex);}}
    private static List<JsonObject> rows(Path d,String n)throws Exception{return Files.readAllLines(d.resolve(n)).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();}
    /** Raw quiet-input and ordinary-time proof required before advancing either captured part. */
    public static boolean qualifiedPhase(Path d,UUID actor,UUID player,boolean after){try{var summary=read(d,"summary.json");var start=read(d,"start.json");if(!summary.get("status").getAsString().equals(after?"COMPLETE":"WAITING_FOR_REOPEN")||!summary.get("reason").isJsonNull()||!summary.get("actor").getAsString().equals(actor.toString())||!summary.get("player").getAsString().equals(player.toString())||!start.get("actor").equals(summary.get("actor"))||!start.get("player").equals(summary.get("player"))||start.get("sourceRun").getAsInt()!=53)return false;
        var hashes=summary.getAsJsonObject("hashes");if(hashes.size()!=6)return false;for(String n:List.of("start.json","predecessor.json","events.jsonl","server.jsonl","client.jsonl","keys.jsonl"))if(!hash(d.resolve(n)).equals(hashes.get(n).getAsString()))return false;
        var t=rows(d,"server.jsonl");var c=rows(d,"client.jsonl");var events=rows(d,"events.jsonl");if(t.isEmpty()||t.size()>3000||t.size()!=summary.get("ticks").getAsInt()||t.size()!=summary.get("serverFrames").getAsInt()||c.isEmpty()||c.size()!=summary.get("clientFrames").getAsInt()||!rows(d,"keys.jsonl").isEmpty()||summary.get("keyEvents").getAsInt()!=0)return false;
        String session=c.getFirst().get("session").getAsString();long first=t.getFirst().get("gameTime").getAsLong(),lastClient=Long.MIN_VALUE;for(var x:c){long time=x.get("gameTime").getAsLong();if(time<lastClient||!quiet(x)||!x.get("session").getAsString().equals(session)||!x.get("player").getAsString().equals(player.toString())||!x.get("clockSource").getAsString().equals("INTEGRATED_SERVER_POST_TICK")||x.has("syntheticNativeFixture")&&x.get("syntheticNativeFixture").getAsBoolean())return false;lastClient=time;}
        for(int i=0;i<t.size();i++){var f=t.get(i);long time=f.get("gameTime").getAsLong();if(time!=first+i||f.get("timer").getAsInt()!=i+1||!f.get("actor").getAsString().equals(actor.toString())||!f.get("player").getAsString().equals(player.toString())||!f.get("actorPresent").getAsBoolean()||!f.get("geometryIntact").getAsBoolean()||f.get("health").getAsFloat()!=40||f.get("playerHealth").getAsFloat()!=20||f.get("targetPresent").getAsBoolean()||f.get("mining").getAsBoolean()||f.get("breaks").getAsInt()!=0)return false;
            if(!f.get("phase").getAsString().equals("GROUND")&&!c.stream().anyMatch(x->Math.abs(time-x.get("gameTime").getAsLong())<=2&&Math.abs(x.get("x").getAsDouble()-HOME.x)<.000001&&Math.abs(x.get("y").getAsDouble()-HOME.y)<.000001&&Math.abs(x.get("z").getAsDouble()-HOME.z)<.000001))return false;
            if(f.get("phase").getAsString().equals("SEALED")&&(!f.get("noAI").getAsBoolean()||!f.getAsJsonArray("tracks").isEmpty()||!f.get("selected").isJsonNull()))return false;}
        var ground=t.stream().filter(f->f.get("phase").getAsString().equals("GROUND")).toList();if(ground.size()<40)return false;for(var g:ground.subList(ground.size()-40,ground.size()))if(!g.get("noAI").getAsBoolean()||!g.get("playerOnGround").getAsBoolean()||!c.stream().anyMatch(x->x.get("onGround").getAsBoolean()&&g.get("gameTime").getAsLong()-x.get("gameTime").getAsLong()>=0&&g.get("gameTime").getAsLong()-x.get("gameTime").getAsLong()<=2))return false;
        var contacts=events.stream().filter(e->e.get("event").getAsString().equals(after?"NEW_CONTACT_AFTER_REOPEN":"FRESH_CONTACT_BEFORE_SAVE")).toList();if(contacts.size()!=1)return false;var f=contacts.getFirst();var signal=f.getAsJsonObject("selected");long age=f.get("gameTime").getAsLong()-signal.get("observed").getAsLong();if(!signal.get("sense").getAsString().equals("ECHO")||!signal.get("subject").getAsString().equals(player.toString())||signal.get("travel").getAsDouble()>8||signal.get("confidence").getAsDouble()<.75||age<0||age>25||f.get("noAI").getAsBoolean())return false;
        return after?summary.get("freshReacquisition").getAsBoolean()&&t.stream().filter(x->x.get("phase").getAsString().equals("SEALED")).count()==80&&t.stream().filter(x->x.get("phase").getAsString().equals("REACQUIRE")).count()==240:events.stream().anyMatch(x->x.get("event").getAsString().equals("HELD_CONTACT_FOR_SAVE")&&x.get("noAI").getAsBoolean()&&!x.getAsJsonArray("tracks").isEmpty()&&!x.get("selected").isJsonNull());
    }catch(Exception e){return false;}}
}
