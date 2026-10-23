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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;

/** Opted-in lifecycle replay. Entity identity and contact are never supplied to sensing. */
@net.neoforged.fml.common.EventBusSubscriber(modid="frozendawn")
public final class LabBlindUnload {
    private static final Gson JSON=new Gson();
    private static final Vec3 HOME=new Vec3(17.5,-44,5.5), AWAY=new Vec3(1041.5,64,5.5);
    private static final String REVIEWED_ATTEMPT="blind-lab-unload/attempt-fffc4dd9-31c0-4c28-b9eb-e045ae2e9354";
    private static final Map<String,String> REVIEWED_HASHES=Map.ofEntries(
        Map.entry("start.json","fde53a159063702695999093319813d86aa410db851546e57d405b26f4dcc200"),
        Map.entry("events.jsonl","1584039122b12132aa0a374203398c6311f9715a70ff2f31e2d6e932bde5b050"),
        Map.entry("server.jsonl","7aae672d94cf8ec9fe9e1f15a211639549cd119729c1a451596c6442336a4ef0"),
        Map.entry("client.jsonl","d64267064990d2a43bd9379d7b10af8b8a207fb124bea683a2fb220b46a3e759"),
        Map.entry("keys.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        Map.entry("summary.json","45fd3cc8e88c84cd24e08853efbc669e83d49dd6ad27fd792caafffa94587627"));
    /** Exact preserved failed attempt permits one repeat; its whole input remains unqualified. */
    public static boolean reviewedFailedAttempt(Path d,UUID actor,UUID player){
        try{
            for(var e:REVIEWED_HASHES.entrySet())if(!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(d.resolve(e.getKey())))).equals(e.getValue()))return false;
            var o=JsonParser.parseString(Files.readString(d.resolve("summary.json"))).getAsJsonObject();
            return o.get("status").getAsString().equals("INCONCLUSIVE")&&o.get("reason").getAsString().equals("INPUT_NOT_QUIET")
                &&o.get("actor").getAsString().equals(actor.toString())&&o.get("player").getAsString().equals(player.toString())
                &&o.get("ticks").getAsInt()==513&&o.get("serverFrames").getAsInt()==513&&o.get("clientFrames").getAsInt()==512;
        }catch(Exception failed){return false;}
    }
    /** Recompute completed lifecycle proof from its raw streams, never trust summary flags alone. */
    public static boolean completedProof(Path d,UUID actor,UUID player){
        try{
            var s=JsonParser.parseString(Files.readString(d.resolve("summary.json"))).getAsJsonObject();
            var start=JsonParser.parseString(Files.readString(d.resolve("start.json"))).getAsJsonObject();
            if(!s.get("status").getAsString().equals("COMPLETE")||!s.get("reason").isJsonNull()
                ||!s.get("actor").getAsString().equals(actor.toString())||!s.get("player").getAsString().equals(player.toString())
                ||!start.get("actor").equals(s.get("actor"))||!start.get("player").equals(s.get("player"))||start.get("sourceRun").getAsInt()!=53)return false;
            for(String flag:List.of("chunkUnloadCallback","entityUnloadToChunk","joinedFromDisk","emptyContactAtJoin","freshReacquisition"))if(!s.get(flag).getAsBoolean())return false;
            var hashes=s.getAsJsonObject("hashes");if(hashes.size()!=5)return false;
            for(String n:List.of("start.json","events.jsonl","server.jsonl","client.jsonl","keys.jsonl"))if(!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(d.resolve(n)))).equals(hashes.get(n).getAsString()))return false;
            var trace=proofRows(d,"server.jsonl");var client=proofRows(d,"client.jsonl");var events=proofRows(d,"events.jsonl");
            if(trace.size()!=s.get("serverFrames").getAsInt()||trace.size()!=s.get("ticks").getAsInt()||trace.isEmpty()||trace.size()>3600||client.size()!=s.get("clientFrames").getAsInt()||client.isEmpty()||!proofRows(d,"keys.jsonl").isEmpty()||s.get("keyEvents").getAsInt()!=0)return false;
            String session=client.getFirst().get("session").getAsString();long first=trace.getFirst().get("gameTime").getAsLong();
            for(var c:client)if(!quiet(c)||!c.get("player").getAsString().equals(player.toString())||!c.get("session").getAsString().equals(session)||!c.get("clockSource").getAsString().equals("INTEGRATED_SERVER_POST_TICK")||c.has("syntheticNativeFixture")&&c.get("syntheticNativeFixture").getAsBoolean())return false;
            for(int i=0;i<trace.size();i++){
                var t=trace.get(i);long time=t.get("gameTime").getAsLong();
                if(time!=first+i||t.get("timer").getAsInt()!=i+1||!t.get("actor").getAsString().equals(actor.toString())||t.get("playerHealth").getAsFloat()!=20||t.get("actorPresent").getAsBoolean()&&(t.get("health").getAsFloat()!=40||t.get("targetPresent").getAsBoolean()))return false;
                if(Set.of("CONTACT","SEALED_RETURN","REACQUIRE").contains(t.get("phase").getAsString())&&!client.stream().anyMatch(c->Math.abs(c.get("gameTime").getAsLong()-time)<=2&&Math.abs(c.get("x").getAsDouble()-HOME.x)<.000001&&Math.abs(c.get("y").getAsDouble()-HOME.y)<.000001&&Math.abs(c.get("z").getAsDouble()-HOME.z)<.000001))return false;
                if(t.get("phase").getAsString().equals("SEALED_RETURN")&&(!t.getAsJsonArray("tracks").isEmpty()||!t.get("selected").isJsonNull()||!t.get("noAI").getAsBoolean()))return false;
            }
            var ground=trace.stream().filter(t->t.get("phase").getAsString().equals("GROUND")).toList();if(ground.size()<40)return false;
            for(var g:ground.subList(ground.size()-40,ground.size()))if(!g.get("playerOnGround").getAsBoolean()||!g.get("noAI").getAsBoolean()||!client.stream().anyMatch(c->c.get("onGround").getAsBoolean()&&g.get("gameTime").getAsLong()-c.get("gameTime").getAsLong()>=0&&g.get("gameTime").getAsLong()-c.get("gameTime").getAsLong()<=2))return false;
            var by=new HashMap<String,JsonObject>();events.forEach(e->by.put(e.get("event").getAsString(),e));
            var before=by.get("FRESH_CONTACT_BEFORE_STORAGE");var removed=by.get("ENTITY_STORAGE_REMOVAL_CONFIRMED");var absent=by.get("CHUNK_AND_ENTITY_ABSENT");var join=by.get("JOIN_FROM_DISK_true");var after=by.get("NEW_CONTACT_AFTER_RELOAD");
            long gone=absent.get("gameTime").getAsLong(),joined=join.get("gameTime").getAsLong();
            if(before.get("gameTime").getAsLong()>=gone||gone>joined||joined>=after.get("gameTime").getAsLong()||absent.get("actorPresent").getAsBoolean()||absent.get("chunkLoaded").getAsBoolean()||!removed.get("originalRemoved").getAsBoolean()||!removed.get("originalRemovalReason").getAsString().equals("UNLOADED_TO_CHUNK")||removed.get("actorPresent").getAsBoolean()||removed.get("chunkLoaded").getAsBoolean()||removed.get("gameTime").getAsLong()>gone||by.get("CHUNK_UNLOAD_CALLBACK").get("gameTime").getAsLong()>gone||events.stream().noneMatch(e->e.get("event").getAsString().startsWith("ENTITY_LEAVE_")&&e.get("gameTime").getAsLong()<=gone))return false;
            return !join.get("sameObject").getAsBoolean()&&join.get("noAI").getAsBoolean()&&join.get("health").getAsFloat()==40&&join.getAsJsonArray("tracks").isEmpty()&&join.get("selected").isJsonNull()&&proofEcho(before,player)&&proofEcho(after,player)&&after.getAsJsonObject("selected").get("observed").getAsLong()>joined;
        }catch(Exception malformed){return false;}
    }
    private static List<JsonObject> proofRows(Path d,String n)throws Exception{return Files.readAllLines(d.resolve(n)).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();}
    private static boolean proofEcho(JsonObject frame,UUID player){var x=frame.getAsJsonObject("selected");long age=frame.get("gameTime").getAsLong()-x.get("observed").getAsLong();return x.get("sense").getAsString().equals("ECHO")&&x.get("subject").getAsString().equals(player.toString())&&age>=0&&age<=25&&x.get("travel").getAsDouble()<=8&&x.get("confidence").getAsDouble()>=.75;}
    private static volatile LabBlindUnload current;
    private final MinecraftServer server;
    private final ServerLevel level;
    private final UUID actor,player;
    private final Path directory;
    private final JsonArray client=new JsonArray(),keys=new JsonArray(),trace=new JsonArray(),events=new JsonArray();
    private final int spawnRadius;
    private JsonObject latestClient;
    private String phase="PREPARED",failure;
    private int ticks,phaseTicks,stable;
    private long lastTick=Long.MIN_VALUE,returnTick;
    private boolean leftChunk,trackingLeft,leftEntity,joinedDisk,emptyJoin,reacquired;
    private ArchitectEntity original,reloaded;
    private LabBlindUnload(ServerPlayer p,ArchitectEntity a)throws Exception {
        server=p.getServer();level=p.serverLevel();player=p.getUUID();actor=a.getUUID();original=a;
        spawnRadius=level.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS);
        directory=server.getWorldPath(LevelResource.ROOT).resolve("blind-lab-unload/attempt-"+UUID.randomUUID());
        Files.createDirectories(directory);
        var start=new JsonObject();start.addProperty("actor",actor.toString());start.addProperty("player",player.toString());start.addProperty("gameTime",level.getGameTime());start.addProperty("sourceRun",53);start.add("observerAwayPosition",JSON.toJsonTree(AWAY));start.addProperty("waitingRoom","Wool enclosure1040..1042,63..68,4..6; actual floor at63, actor remains in original chunk0,0");start.addProperty("originalSpawnChunkRadius",spawnRadius);start.addProperty("note","Actor held across real chunk storage; no client observation during away phase proves behavior inside unloaded chunk.");
        Files.writeString(directory.resolve("start.json"),start+"\n",StandardOpenOption.CREATE_NEW);
        Files.writeString(directory.resolve("events.jsonl"),"",StandardOpenOption.CREATE_NEW);
    }
    private static int score(ServerLevel l,String name){var o=l.getScoreboard().getObjective("blind_vib");if(o==null)return -1;var v=l.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(name),o);return v==null?-1:v.value();}
    private static void score(ServerLevel l,String name,int value){l.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(name),l.getScoreboard().getObjective("blind_vib")).set(value);}
    public static int ready(CommandSourceStack s){return score(s.getLevel(),"#unload")!=-1||current!=null&&current.server==s.getServer()?0:LabBlindDecoy.unloadReady(s);}
    /** Leave-level is a tracking callback; final storage removal may follow it later. */
    public static boolean storageConfirmed(ArchitectEntity original,boolean trackingLeft,boolean chunkLeft,boolean chunkLoaded,boolean actorPresent){
        return trackingLeft&&chunkLeft&&!chunkLoaded&&!actorPresent&&original.isRemoved()
            &&original.getRemovalReason()==net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK;
    }
    public static int repeatPrepare(CommandSourceStack s){
        if(current!=null&&current.server==s.getServer()||!(s.getEntity() instanceof ServerPlayer p)
            ||p.getPersistentData().contains("BlindLabUnloadRepeat")||p.position().distanceToSqr(AWAY)>.000001
            ||p.getHealth()!=20||score(s.getLevel(),"#unload")!=3||!s.getLevel().getForcedChunks().isEmpty()
            ||LabBlindDecoy.unloadRepeatReady(s)!=1)return 0;
        try{
            var a=(ArchitectEntity)s.getLevel().getEntity(p.getPersistentData().getUUID("BlindLabDecoyActor"));
            if(a==null||a.getHealth()!=40||a.position().distanceToSqr(new Vec3(15.5,-44,5.5))>.000001
                ||!a.blindController().tracks().isEmpty()||a.blindController().signal()!=null||a.getTarget()!=null
                ||!reviewedFailedAttempt(s.getServer().getWorldPath(LevelResource.ROOT).resolve(REVIEWED_ATTEMPT),a.getUUID(),p.getUUID()))return 0;
            current=new LabBlindUnload(p,a);
            // Persist before any scene change: an interrupted preparation cannot silently repeat.
            p.getPersistentData().putString("BlindLabUnloadRepeat",current.directory.getFileName().toString());
            Files.writeString(current.directory.resolve("predecessor.json"),"{\"attempt\":\""+REVIEWED_ATTEMPT+"\",\"status\":\"INCONCLUSIVE\",\"purpose\":\"guarded repeat, no retrospective qualification\"}\n",StandardOpenOption.CREATE_NEW);
            p.getAbilities().flying=false;p.onUpdateAbilities();p.setNoGravity(false);
            p.teleportTo(s.getLevel(),HOME.x,HOME.y,HOME.z,p.getYRot(),p.getXRot());
            score(s.getLevel(),"#stage",4);score(s.getLevel(),"#unload",0);
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal("UNLOAD REPEAT: pause. Travel and return are automatic; stay still until VIEW COMPLETE."));return 1;
        }catch(Exception e){s.sendFailure(net.minecraft.network.chat.Component.literal("Repeat preparation failed; preserve, do not retry."));return 0;}
    }
    public static int prepare(CommandSourceStack s){
        if(ready(s)!=1||!(s.getEntity() instanceof ServerPlayer p)||p.position().distanceToSqr(HOME)>.000001)return 0;
        if(!s.getLevel().getForcedChunks().isEmpty())return 0;
        try {
            var a=(ArchitectEntity)s.getLevel().getEntity(p.getPersistentData().getUUID("BlindLabDecoyActor"));
            if(a==null||!a.blindController().tracks().isEmpty())return 0;
            current=new LabBlindUnload(p,a);
            for(int x=1040;x<=1042;x++)for(int y=63;y<=68;y++)for(int z=4;z<=6;z++)s.getLevel().setBlock(new BlockPos(x,y,z),(x==1041&&z==5&&y>=64&&y<=67?Blocks.AIR:Blocks.WHITE_WOOL).defaultBlockState(),3);
            score(s.getLevel(),"#stage",4);score(s.getLevel(),"#unload",0);
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal("UNLOAD: pause. Automatic travel and return; stay still until VIEW COMPLETE."));return 1;
        }catch(Exception e){s.sendFailure(net.minecraft.network.chat.Component.literal("Unload preparation archive failed; preserve, do not retry."));return 0;}
    }
    public static int start(CommandSourceStack s){
        var c=current;if(c==null||c.server!=s.getServer()||!c.phase.equals("PREPARED")||!(s.getEntity() instanceof ServerPlayer p)||!p.getUUID().equals(c.player)||!LabBlindReplay.allowed(s,"vibration_lab")||score(c.level,"#stage")!=4||score(c.level,"#unload")!=0||!LabBlindMeleeObstruction.geometry(c.level)||!c.original.isNoAi())return 0;
        c.phase="GROUND";score(c.level,"#unload",1);p.setGameMode(GameType.SURVIVAL);p.sendSystemMessage(net.minecraft.network.chat.Component.literal("GROUND CHECK: stand still. Automatic unload follows contact."));return 1;
    }
    public static boolean active(MinecraftServer s){var c=current;return c!=null&&c.server==s&&!Set.of("PREPARED","COMPLETE","INCONCLUSIVE").contains(c.phase);}
    public static synchronized void client(MinecraftServer s,JsonObject row,boolean key){
        var c=current;if(!active(s))return;
        var rows=key?c.keys:c.client;if(rows.size()>=8192){c.failure="INPUT_OVERFLOW";return;}
        rows.add(row.deepCopy());if(!key)c.latestClient=row.deepCopy();
    }
    private static boolean quiet(JsonObject c){
        try{return c.get("focused").getAsBoolean()&&!c.get("screenOpen").getAsBoolean()&&!c.get("paused").getAsBoolean()&&c.has("windowKeys")
            &&c.getAsJsonObject("mapped").entrySet().stream().noneMatch(e->e.getValue().getAsBoolean())&&c.getAsJsonObject("windowKeys").entrySet().stream().noneMatch(e->e.getValue().getAsBoolean())&&!c.get("useDown").getAsBoolean();}catch(Exception e){return false;}
    }
    private JsonObject frame(ArchitectEntity a){
        var f=new JsonObject();f.addProperty("timer",ticks);f.addProperty("phaseTimer",phaseTicks);f.addProperty("phase",phase);f.addProperty("gameTime",level.getGameTime());f.addProperty("actor",actor.toString());f.addProperty("actorPresent",a!=null);f.addProperty("chunkLoaded",level.hasChunk(0,0));
        f.addProperty("originalRemoved",original.isRemoved());f.addProperty("originalRemovalReason",String.valueOf(original.getRemovalReason()));
        var p=server.getPlayerList().getPlayer(player);if(p!=null){f.add("playerPosition",JSON.toJsonTree(p.position()));f.addProperty("playerHealth",p.getHealth());f.addProperty("playerOnGround",p.onGround());f.addProperty("playerMode",p.gameMode.getGameModeForPlayer().getName());}
        if(a!=null){f.addProperty("health",a.getHealth());f.addProperty("noAI",a.isNoAi());f.add("position",JSON.toJsonTree(a.position()));f.add("tracks",JSON.toJsonTree(a.blindController().tracks()));f.add("selected",JSON.toJsonTree(a.blindController().signal()));f.addProperty("diagnostics",a.blindController().diagnostics());f.addProperty("targetPresent",a.getTarget()!=null);f.addProperty("sameObject",a==original);}
        return f;
    }
    private void event(String kind,ArchitectEntity a){var f=frame(a);f.addProperty("event",kind);events.add(f);try{Files.writeString(directory.resolve("events.jsonl"),f+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){failure="EVENT_ARCHIVE_FAILED";}}
    private void change(String next,ArchitectEntity a){event("END_"+phase,a);phase=next;phaseTicks=0;event("START_"+next,a);}
    private void gap(boolean open){level.setBlock(new BlockPos(16,-44,5),(open?Blocks.AIR:Blocks.GLASS).defaultBlockState(),3);level.setBlock(new BlockPos(16,-43,5),open?Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP):Blocks.GLASS.defaultBlockState(),3);}
    private boolean fresh(ArchitectEntity a,ServerPlayer p){var x=a.blindController().signal();return x!=null&&x.sense()==Sense.ECHO&&p.getUUID().equals(x.subject())&&x.lock(level.getGameTime())&&level.getGameTime()-x.observed()<=25&&x.travel()<=8;}
    public static synchronized void tick(MinecraftServer s){var c=current;if(active(s)&&!s.isPaused())c.tick();}
    private void tick(){
        long now=level.getGameTime();if(now<=lastTick)return;lastTick=now;ticks++;phaseTicks++;
        var p=server.getPlayerList().getPlayer(player);var entity=level.getEntity(actor);var a=entity instanceof ArchitectEntity x?x:null;
        trace.add(frame(a));
        if(p==null||server.getPlayerList().getPlayerCount()!=1||p.serverLevel()!=level||score(level,"#run")!=53||score(level,"#stage")!=4){finish("PARTICIPANT_OR_SCENE_CHANGED",p,a);return;}
        if(failure!=null){finish(failure,p,a);return;}
        if(ticks>3600){finish("UNLOAD_TIMEOUT",p,a);return;}
        if(p.getHealth()!=20||a!=null&&(a.getHealth()!=40||!a.isAlive()||!a.isBlindArchitect()||a.position().distanceToSqr(new Vec3(15.5,-44,5.5))>.001||a.getTarget()!=null)){finish("ACTOR_OR_HEALTH_CHANGED",p,a);return;}
        if(latestClient!=null&&!quiet(latestClient)){finish("INPUT_NOT_QUIET",p,a);return;}
        if(!LabBlindIsolation.unloadSourcesClean(level)){finish("FOREIGN_MOB",p,a);return;}
        if(Set.of("CONTACT","SEALED_RETURN","REACQUIRE").contains(phase)&&p.position().distanceToSqr(HOME)>.000001){finish("OBSERVER_MOVED",p,a);return;}
        switch(phase){
            case "GROUND" -> {
                boolean grounded=a!=null&&a.isNoAi()&&p.onGround()&&p.position().distanceToSqr(HOME)<.000001&&latestClient!=null&&quiet(latestClient)&&latestClient.get("onGround").getAsBoolean()&&latestClient.get("player").getAsString().equals(player.toString())&&Math.abs(now-latestClient.get("gameTime").getAsLong())<=2;
                stable=grounded?stable+1:0;
                if(stable>=40){gap(true);a.setNoAi(false);change("CONTACT",a);p.sendSystemMessage(net.minecraft.network.chat.Component.literal("VIEW STARTED: listen for contact. Travel is automatic."));}
                else if(phaseTicks>2400)finish("GROUND_TIMEOUT",p,a);
            }
            case "CONTACT" -> {
                if(a==null){finish("MISSING_BEFORE_UNLOAD",p,null);return;}
                if(fresh(a,p)){event("FRESH_CONTACT_BEFORE_STORAGE",a);gap(false);a.setNoAi(true);level.getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,server);p.setGameMode(GameType.CREATIVE);p.setNoGravity(false);p.teleportTo(level,AWAY.x,AWAY.y,AWAY.z,p.getYRot(),p.getXRot());change("AWAY",a);p.sendSystemMessage(net.minecraft.network.chat.Component.literal("AWAY: waiting for real chunk unload. Stay still."));}
                else if(phaseTicks>480)finish("NO_FRESH_CONTACT",p,a);
            }
            case "AWAY" -> {
                if(storageConfirmed(original,trackingLeft,leftChunk,level.hasChunk(0,0),a!=null)){
                    leftEntity=true;event("ENTITY_STORAGE_REMOVAL_CONFIRMED",null);
                    event("CHUNK_AND_ENTITY_ABSENT",null);returnTick=now;change("RETURNING",null);p.teleportTo(level,HOME.x,HOME.y,HOME.z,p.getYRot(),p.getXRot());p.setNoGravity(false);p.sendSystemMessage(net.minecraft.network.chat.Component.literal("RETURNING: same actor; barrier stays closed first."));}
            }
            case "RETURNING" -> {
                if(a!=null){if(a==original||a!=reloaded||!joinedDisk||!emptyJoin||!a.isNoAi()||!a.blindController().tracks().isEmpty()||a.blindController().signal()!=null){finish("INVALID_RELOAD",p,a);return;}change("SEALED_RETURN",a);}
                else if(phaseTicks>400)finish("RELOAD_TIMEOUT",p,null);
            }
            case "SEALED_RETURN" -> {
                if(a==null||!a.blindController().tracks().isEmpty()||a.blindController().signal()!=null){finish("RESTORED_CONTACT",p,a);return;}
                if(phaseTicks>=120&&p.onGround()){gap(true);p.setGameMode(GameType.SURVIVAL);a.setNoAi(false);change("REACQUIRE",a);p.sendSystemMessage(net.minecraft.network.chat.Component.literal("GAP OPEN: watch for fresh sensing again."));}
            }
            case "REACQUIRE" -> {
                if(a==null){finish("MISSING_AFTER_RELOAD",p,null);return;}
                if(fresh(a,p)&&a.blindController().signal().observed()>returnTick&&!reacquired){reacquired=true;event("NEW_CONTACT_AFTER_RELOAD",a);}
                if(phaseTicks>=240)finish(reacquired?null:"NO_NEW_CONTACT",p,a);
            }
            default -> finish("UNKNOWN_PHASE",p,a);
        }
    }
    @net.neoforged.bus.api.SubscribeEvent public static synchronized void chunk(net.neoforged.neoforge.event.level.ChunkEvent.Unload e){var c=current;if(c!=null&&e.getLevel()==c.level&&c.phase.equals("AWAY")&&e.getChunk().getPos().x==0&&e.getChunk().getPos().z==0){c.leftChunk=true;c.event("CHUNK_UNLOAD_CALLBACK",null);}}
    @net.neoforged.bus.api.SubscribeEvent public static synchronized void left(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e){var c=current;if(c!=null&&e.getLevel()==c.level&&e.getEntity()==c.original&&c.phase.equals("AWAY")){c.trackingLeft=true;c.event("ENTITY_LEAVE_"+e.getEntity().getRemovalReason(),c.original);}}
    @net.neoforged.bus.api.SubscribeEvent public static synchronized void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e){var c=current;if(c!=null&&e.getLevel()==c.level&&e.getEntity().getUUID().equals(c.actor)&&e.getEntity() instanceof ArchitectEntity a&&c.phase.equals("RETURNING")){c.reloaded=a;c.joinedDisk=e.loadedFromDisk();c.emptyJoin=a.blindController().tracks().isEmpty()&&a.blindController().signal()==null&&a.getTarget()==null;c.event("JOIN_FROM_DISK_"+c.joinedDisk,a);}}
    @net.neoforged.bus.api.SubscribeEvent public static synchronized void stopped(net.neoforged.neoforge.event.server.ServerStoppingEvent e){var c=current;if(c!=null&&c.server==e.getServer()){if(active(c.server))c.finish("SESSION_CLOSED",c.server.getPlayerList().getPlayer(c.player),c.level.getEntity(c.actor) instanceof ArchitectEntity a?a:null);current=null;}}
    private void finish(String reason,ServerPlayer p,ArchitectEntity a){
        if(reason==null&&(client.isEmpty()||!keys.isEmpty()||java.util.stream.StreamSupport.stream(client.spliterator(),false).anyMatch(x->!quiet(x.getAsJsonObject()))))reason="INPUT_UNQUALIFIED";
        failure=reason;if(a!=null)gap(false);level.getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(spawnRadius,server);if(a!=null)a.setNoAi(true);
        if(p!=null){p.setNoGravity(false);p.setGameMode(GameType.CREATIVE);if(!phase.equals("AWAY"))p.sendSystemMessage(net.minecraft.network.chat.Component.literal("VIEW COMPLETE: pause, then describe what you saw. Result: "+(reason==null?"captured":"inconclusive")));else p.sendSystemMessage(net.minecraft.network.chat.Component.literal("UNLOAD INCONCLUSIVE: pause at the waiting spot; preserve this attempt."));}
        phase=reason==null?"COMPLETE":"INCONCLUSIVE";score(level,"#unload",reason==null?2:3);score(level,"#stage",reason==null?3:4);
        try{for(var e:Map.of("server.jsonl",trace,"client.jsonl",client,"keys.jsonl",keys).entrySet()){var b=new StringBuilder();e.getValue().forEach(x->b.append(x).append('\n'));Files.writeString(directory.resolve(e.getKey()),b,StandardOpenOption.CREATE_NEW);}
            var o=new JsonObject();o.addProperty("status",phase);o.addProperty("reason",reason);o.addProperty("actor",actor.toString());o.addProperty("player",player.toString());o.addProperty("ticks",ticks);o.addProperty("chunkUnloadCallback",leftChunk);o.addProperty("entityUnloadToChunk",leftEntity);o.addProperty("joinedFromDisk",joinedDisk);o.addProperty("emptyContactAtJoin",emptyJoin);o.addProperty("freshReacquisition",reacquired);o.addProperty("serverFrames",trace.size());o.addProperty("clientFrames",client.size());o.addProperty("keyEvents",keys.size());o.addProperty("limits","Automatic observer teleport; held actor across storage. Whole input must be independently checked before visual acceptance; serializer native test does not prove live unload.");var hashes=new JsonObject();for(String n:List.of("start.json","events.jsonl","server.jsonl","client.jsonl","keys.jsonl"))hashes.addProperty(n,java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(directory.resolve(n)))));o.add("hashes",hashes);Files.writeString(directory.resolve("summary.json"),o+"\n",StandardOpenOption.CREATE_NEW);
        }catch(Exception e){System.err.println("[BlindUnload] Archive failed: "+e);phase="INCONCLUSIVE";score(level,"#unload",3);}
    }
}
