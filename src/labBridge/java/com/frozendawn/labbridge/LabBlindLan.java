package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.blind.BlindSignalMemory.Sense;
import com.frozendawn.init.ModItems;
import com.frozendawn.maeve.MaeveDirector;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;

/** One guarded local two-client view. Guest input is independently sampled in its remote client. */
public final class LabBlindLan {
    private static final Gson JSON=new Gson();
    private static final String MARKER="BlindLabLanAttempt",PROCESS=UUID.randomUUID().toString();
    private static final Vec3 HOME=new Vec3(17.5,-44,5.5),ACTOR=new Vec3(15.5,-44,5.5),HOST=new Vec3(19.5,-44,5.5);
    private static final Map<String,String> REVIEWED_SAVE=Map.ofEntries(Map.entry("start.json","6c04d1254eceb808deea4fe2e874a40006e716624ef4a5a7cbeaad7e65555410"),
        Map.entry("predecessor.json","aaf11ddedca14e995ff9f057c7049b6f86b07c21e585e822859e35d5984ae3b4"),
        Map.entry("events.jsonl","15f4ebd06597535347bc91f062a77226fea1ae2e66244675d9f8b5fe8cf3a7c8"),
        Map.entry("server.jsonl","3cecf8a42f1a12cc6100de7d2d3e08193c6fa71dc52edf46c92091b1df92f9e0"),
        Map.entry("client.jsonl","ae4b57ed604bb84a9717a7d363abadfbaf4fea56107d9bf68129ef387df94228"),
        Map.entry("keys.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        Map.entry("summary.json","a005e829ae377322fc29bce6f1196f279f0c92e7e5afe1a400f91c87e93743c0"),
        Map.entry("shutdown.json","b7520e62feb5321d7c7b58ee9ddc9a85ab0990704064ea15b4aea567ee3a438a"),
        Map.entry("reopen-join.json","cd8b0d2755b12154a10e43a9f3755843e21cba116ebd2299448117527a178ebb"),
        Map.entry("after/start.json","161cbb4a0e5215940ce8541ed4d50bcb34b8e9d55894138b1a51a76faa7ce8ff"),
        Map.entry("after/predecessor.json","46dcf5b49ed960aaf4751fb4f391f11952efd2bf3972dc64978bcd7404be88f1"),
        Map.entry("after/events.jsonl","25fa9a1a43c2785a8512b5bbf24103e476cf46f3c88d4208b779e51a60256a1a"),
        Map.entry("after/server.jsonl","d5f70ae5c4cae36e4211bdb899b3f84369b8dc1b2c52d38a111a29cbbdc1af1c"),
        Map.entry("after/client.jsonl","c4b2ccf290fbae64d4c39970e738a02f4f35e3fde0e4fa3553d9ed16be98a8a6"),
        Map.entry("after/keys.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        Map.entry("after/summary.json","57222b9aac2f7bd96b3ec7d84d98e2dfd9d430856bc1bd503dbc262522d9012d"));
    private static final String FAILED_ATTEMPT="attempt-fffba83f-b095-45d9-95eb-f9e03dd3cf8e";
    private static final Map<String,String> REVIEWED_FAILED=Map.ofEntries(Map.entry("start.json","e586e867263d0343beda6d45e20b402dd6e17e497886091cbc22d455de07322a"),
        Map.entry("predecessor.json","9a137ed6797f6a8e2248c009e789488ccc0a8e326bdfcfb38f1a88053eb218e8"),
        Map.entry("players-before-staging.json","0323d66758aaadf2f734dc620d80e9170985a9c918e52bad661648397c2eef22"),
        Map.entry("events.jsonl","0af57ec5faa5dee469737b2a571f9cb033005ba2aa71a3b7ecb59bed6a2c76cc"),
        Map.entry("server.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        Map.entry("server-readiness.jsonl","7627c94cc0ff2c3e0378e3e2458ba5bd8696a1348244507f4b6e1ec38156044b"),
        Map.entry("host-client.jsonl","561d5f3297dc88a7978424cc274b665633c77e730e2b34f6538ecede5c7e9e9b"),
        Map.entry("host-keys.jsonl","e26ecb3e7bfbe1a25437138f6fb493512f7618d384c466159763aaf51151778e"),
        Map.entry("summary.json","02c6ed330bb4dff7aeb7f7823a40c4e4392d59b3ad78e55802db88babe763447"),
        Map.entry("guest-client.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        Map.entry("guest-keys.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        Map.entry("guest-readiness.jsonl","f48c5391c79d4b883af70cdd376a626b405c6d65405930d6e514a76710dd78a3"),
        Map.entry("guest-readiness-keys.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        Map.entry("guest-summary.json","40f8bd7caaf8f622ca76cc7f1688d3bcfb962a145c601669fa61b1034d671b59"));
    private static volatile LabBlindLan current;
    private final MinecraftServer server;
    private final ServerLevel level;
    private final UUID host,actor;
    private UUID guest;
    private final ArchitectEntity original;
    private final Path directory;
    private final String previousAttempt;
    private final UUID previousGuest;
    private final String session=UUID.randomUUID().toString();
    private final JsonArray trace=new JsonArray(),readiness=new JsonArray(),hostClient=new JsonArray(),hostKeys=new JsonArray();
    private String phase="PREPARED",failure;
    private int stable,phaseTicks,ticks,idleTail;
    private long lastTick=Long.MIN_VALUE,opened=-1,sealed=-1;
    private boolean acquired,attributed;
    private LabBlindLan(ServerPlayer p,ArchitectEntity a,Path d)throws Exception{this(p,a,d,"",null);}
    private LabBlindLan(ServerPlayer p,ArchitectEntity a,Path d,String previous,UUID guest)throws Exception{this(p,a,d,previous,guest,false);}
    private LabBlindLan(ServerPlayer p,ArchitectEntity a,Path d,String previous,UUID guest,boolean reserved)throws Exception{
        previousAttempt=previous;previousGuest=guest;
        server=p.getServer();level=p.serverLevel();host=p.getUUID();actor=a.getUUID();original=a;directory=d;if(!reserved)Files.createDirectory(d);
        var o=frame(a);o.addProperty("process",PROCESS);o.addProperty("captureSession",session);o.addProperty("previousAttempt",previousAttempt);o.addProperty("note","Two actual clients. Spectator host, stationary Survival guest. Shared-file host clock explicitly distinguished from guest remote clock. Ordinary sensing only; no injected contact, memory clearing or capture teleports.");
        write("start.json",o);Files.writeString(d.resolve("events.jsonl"),"",StandardOpenOption.CREATE_NEW);
    }
    private static int score(ServerLevel l,String n){var o=l.getScoreboard().getObjective("blind_vib");if(o==null)return -1;var v=l.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(n),o);return v==null?-1:v.value();}
    private static void score(ServerLevel l,String n,int v){l.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(n),l.getScoreboard().getObjective("blind_vib")).set(v);}
    private static String hash(Path p)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    private static JsonObject read(Path p)throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
    private void write(String n,JsonObject o)throws Exception{Files.writeString(directory.resolve(n),o+"\n",StandardOpenOption.CREATE_NEW);}
    public static boolean reviewedSaveProof(Path d,UUID a,UUID p){try{
        for(var e:REVIEWED_SAVE.entrySet())if(!hash(d.resolve(e.getKey())).equals(e.getValue()))return false;
        var start=read(d.resolve("start.json"));var join=read(d.resolve("reopen-join.json"));var after=read(d.resolve("after/start.json"));
        return LabBlindPersistence.qualifiedPhase(d,a,p,false)&&LabBlindPersistence.qualifiedPhase(d.resolve("after"),a,p,true)
            &&join.get("loadedFromDisk").getAsBoolean()&&!join.get("process").equals(start.get("process"))&&join.get("process").equals(after.get("process"));
    }catch(Exception e){return false;}}
    private static boolean exact(MinecraftServer s){return !net.neoforged.fml.loading.FMLEnvironment.production&&Boolean.getBoolean("frozendawn.labBridge")&&!s.isDedicatedServer()&&s.getWorldData().getLevelName().equals("Blind Architect Reusable Lab");}
    private static ArchitectEntity actor(ServerPlayer p){return p.serverLevel().getEntity(p.getPersistentData().getUUID("BlindLabDecoyActor")) instanceof ArchitectEntity a?a:null;}
    private boolean source(CommandSourceStack s){return exact(s.getServer())&&server==s.getServer()&&s.getEntity() instanceof ServerPlayer p&&p.getUUID().equals(host)&&p.serverLevel()==level&&p.getPersistentData().getString(MARKER).equals(directory.getFileName().toString());}
    private boolean held(){return original==level.getEntity(actor)&&original.isAlive()&&original.isBlindArchitect()&&original.getHealth()==40&&original.isNoAi()&&original.position().distanceToSqr(ACTOR)<.001&&original.getTarget()==null&&!original.isMiningBlock()&&original.successfulBreakCount()==0&&original.blindController().tracks().isEmpty()&&original.blindController().signal()==null;}
    public static int prepare(CommandSourceStack s){if(current!=null||!(s.getEntity() instanceof ServerPlayer p)||!LabBlindReplay.allowed(s,"vibration_lab")||s.getServer().isPublished()||p.getPersistentData().contains(MARKER)||score(s.getLevel(),"#lan")!=-1||score(s.getLevel(),"#persist")!=5||score(s.getLevel(),"#stale")!=2||score(s.getLevel(),"#unload")!=2||LabBlindDecoy.unloadReady(s)!=1)return 0;
        try{var a=actor(p);if(!p.isCreative()||p.getHealth()!=20||p.position().distanceToSqr(HOME)>.000001||a==null||a.getPersistentData().contains(MARKER)||!LabBlindMeleeObstruction.geometry(p.serverLevel(),true)||!LabBlindIsolation.sourcesClean(p.serverLevel())||!LabBlindIsolation.unrelated(p.serverLevel()).isEmpty()||!p.serverLevel().getForcedChunks().isEmpty())return 0;
            if(!a.isNoAi()||a.getHealth()!=40||a.position().distanceToSqr(ACTOR)>.001||!a.isBlindArchitect()||a.getTarget()!=null||a.isMiningBlock()||a.successfulBreakCount()!=0||!a.blindController().tracks().isEmpty()||a.blindController().signal()!=null)return 0;
            String prior=p.getPersistentData().getString("BlindLabSaveAttempt");if(!prior.matches("attempt-[0-9a-f-]{36}"))return 0;
            Path predecessor=s.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-save").resolve(prior);if(!reviewedSaveProof(predecessor,a.getUUID(),p.getUUID()))return 0;
            Path parent=s.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-lan");Files.createDirectories(parent);current=new LabBlindLan(p,a,parent.resolve("attempt-"+UUID.randomUUID()));
            var proof=new JsonObject();for(var e:REVIEWED_SAVE.entrySet())proof.addProperty(e.getKey(),e.getValue());current.write("predecessor.json",proof);
            p.getPersistentData().putString(MARKER,current.directory.getFileName().toString());a.getPersistentData().putString(MARKER,current.directory.getFileName().toString());score(s.getLevel(),"#stage",4);score(s.getLevel(),"#lan",0);return 1;
        }catch(Exception e){s.sendFailure(Component.literal("LAN preparation failed; preserve, do not retry. "+e));return 0;}}
    /** Only the archived, reviewed pre-gameplay failure authorizes this one fresh continuation. */
    public static boolean reviewedFailedProof(Path d,UUID a,UUID h){try{
        for(var e:REVIEWED_FAILED.entrySet())if(!hash(d.resolve(e.getKey())).equals(e.getValue()))return false;
        var s=read(d.resolve("summary.json"));var g=read(d.resolve("guest-summary.json"));
        return s.get("status").getAsString().equals("INCONCLUSIVE")&&s.get("reason").getAsString().equals("ROSTER_OR_SCENE_CHANGED")&&s.get("lastPhase").getAsString().equals("GROUND")&&s.get("actor").getAsString().equals(a.toString())&&s.get("host").getAsString().equals(h.toString())&&s.get("guest").getAsString().equals("347c4be4-4dda-3d32-a649-477c0bff527c")&&s.get("ticks").getAsInt()==0&&s.get("opened").getAsLong()==-1&&s.get("sealed").getAsLong()==-1&&!s.get("actualGuestContact").getAsBoolean()&&!s.get("guestMaeveContact").getAsBoolean()&&g.get("writersStopped").getAsBoolean()&&g.get("frames").getAsInt()==0&&g.get("keyEvents").getAsInt()==0&&g.get("dropped").getAsInt()==0&&g.get("captureSession").equals(s.get("captureSession"));
    }catch(Exception e){return false;}}
    public static int repeatPrepare(CommandSourceStack s){if(current!=null||!(s.getEntity() instanceof ServerPlayer p)||!LabBlindReplay.allowed(s,"vibration_lab")||s.getServer().isPublished()||score(s.getLevel(),"#stage")!=4||score(s.getLevel(),"#lan")!=3||score(s.getLevel(),"#persist")!=5||score(s.getLevel(),"#stale")!=2||score(s.getLevel(),"#unload")!=2||!p.isSpectator()||p.getHealth()!=20||!p.getTags().contains("blind_lab_player")||p.getPersistentData().contains("BlindLabLanRepeatUsed"))return 0;
        try{var l=p.serverLevel();for(String n:List.of("#run","#case","#isolated"))if(score(l,n)!=53)return 0;for(String n:List.of("#timer","#frames","#last_frame"))if(score(l,n)!=480)return 0;if(score(l,"#archived")!=1||score(l,"#inconclusive")!=0)return 0;
            var a=actor(p);if(a==null||!a.isAlive()||!a.isBlindArchitect()||a.getHealth()!=40||!a.isNoAi()||a.position().distanceToSqr(ACTOR)>.001||a.getTarget()!=null||a.isMiningBlock()||a.successfulBreakCount()!=0||!a.blindController().tracks().isEmpty()||a.blindController().signal()!=null||a.getPersistentData().contains("BlindLabLanRepeatUsed")||!p.getPersistentData().getString(MARKER).equals(FAILED_ATTEMPT)||!a.getPersistentData().getString(MARKER).equals(FAILED_ATTEMPT)||!LabBlindMeleeObstruction.geometry(l,true)||!LabBlindIsolation.unloadSourcesClean(l)||!l.getForcedChunks().isEmpty())return 0;
            Path parent=s.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-lan"),failed=parent.resolve(FAILED_ATTEMPT);if(!reviewedFailedProof(failed,a.getUUID(),p.getUUID()))return 0;
            String saved=p.getPersistentData().getString("BlindLabSaveAttempt");if(!saved.matches("attempt-[0-9a-f-]{36}")||!reviewedSaveProof(s.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-save").resolve(saved),a.getUUID(),p.getUUID()))return 0;
            // Archive actual saved host/actor state before any repeat setup or marker changes.
            var before=new JsonObject();before.addProperty("hostNbt",p.saveWithoutId(new CompoundTag()).toString());before.addProperty("actorNbt",a.saveWithoutId(new CompoundTag()).toString());before.addProperty("gameTime",l.getGameTime());before.addProperty("previousAttempt",FAILED_ATTEMPT);
            Path next=parent.resolve("attempt-"+UUID.randomUUID());Files.createDirectory(next);Files.writeString(next.resolve("repeat-setup-before.json"),before+"\n",StandardOpenOption.CREATE_NEW);
            p.getPersistentData().putBoolean("BlindLabLanRepeatUsed",true);a.getPersistentData().putBoolean("BlindLabLanRepeatUsed",true);
            p.setGameMode(GameType.CREATIVE);p.setNoGravity(false);p.getAbilities().flying=false;p.onUpdateAbilities();p.teleportTo(l,HOME.x,HOME.y,HOME.z,Set.of(),90,0);
            current=new LabBlindLan(p,a,next,FAILED_ATTEMPT,UUID.fromString("347c4be4-4dda-3d32-a649-477c0bff527c"),true);
            var proof=new JsonObject();for(var e:REVIEWED_FAILED.entrySet())proof.addProperty(e.getKey(),e.getValue());current.write("predecessor.json",proof);
            p.getPersistentData().putString(MARKER,next.getFileName().toString());a.getPersistentData().putString(MARKER,next.getFileName().toString());score(l,"#lan",0);current.event("GUARDED_REPEAT_PREPARED",a);return 1;
        }catch(Exception e){s.sendFailure(Component.literal("LAN repeat preparation failed; preserve, do not retry. "+e));return 0;}}
    public static int publish(CommandSourceStack s){var c=current;if(c==null||!c.source(s)||!c.phase.equals("PREPARED")||c.failure!=null||s.getServer().isPublished()||s.getServer().getPlayerList().getPlayerCount()!=1||!c.held()||!LabBlindMeleeObstruction.geometry(c.level,true)||!LabBlindIsolation.unloadSourcesClean(c.level))return 0;
        try{int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
            if(!c.server.publishServer(GameType.CREATIVE,true,port)){c.failure="PUBLISH_FAILED";return 0;}c.phase="PUBLISHED";c.event("LAN_PUBLISHED",c.original);return 1;
        }catch(Exception e){c.failure="PUBLISH_EXCEPTION";s.sendFailure(Component.literal("LAN publication failed; preserve, do not retry."));return 0;}}
    public static int stage(CommandSourceStack s){var c=current;if(c==null||!c.source(s)||!c.phase.equals("PUBLISHED")||c.failure!=null||!c.server.isPublished()||c.server.getPlayerList().getPlayerCount()!=2||!c.held()||!LabBlindMeleeObstruction.geometry(c.level,true)||!LabBlindIsolation.unloadSourcesClean(c.level))return 0;
        var p=c.server.getPlayerList().getPlayer(c.host);var guests=c.server.getPlayerList().getPlayers().stream().filter(x->!x.getUUID().equals(c.host)).toList();
        if(p==null||p.getHealth()!=20||!p.isCreative()||guests.size()!=1)return 0;var g=guests.getFirst();if(!g.getGameProfile().getName().equals("ArchitectGuest")||g.serverLevel()!=c.level||g.getHealth()!=20||!g.isCreative()||(g.getPersistentData().contains(MARKER)&&!(g.getPersistentData().getString(MARKER).equals(c.previousAttempt)&&g.getUUID().equals(c.previousGuest))))return 0;
        try{c.guest=g.getUUID();var before=new JsonObject();before.addProperty("guest",g.getUUID().toString());before.addProperty("guestNbt",g.saveWithoutId(new CompoundTag()).toString());before.addProperty("hostNbt",p.saveWithoutId(new CompoundTag()).toString());c.write("players-before-staging.json",before);
            g.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModItems.EVA_HELMET.get()));g.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.EVA_CHESTPLATE.get()));g.setItemSlot(EquipmentSlot.LEGS,new ItemStack(ModItems.EVA_LEGGINGS.get()));g.setItemSlot(EquipmentSlot.FEET,new ItemStack(ModItems.EVA_BOOTS.get()));g.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new ItemStack(ModItems.O2_TANK_MK3.get()));
            g.teleportTo(c.level,HOME.x,HOME.y,HOME.z,Set.of(),90,0);g.getPersistentData().putString(MARKER,c.directory.getFileName().toString());g.addTag("blind_lab_guest");
            p.setGameMode(GameType.SPECTATOR);p.teleportTo(c.level,HOST.x,HOST.y,HOST.z,Set.of(),90,0);
            c.phase="STAGED";c.event("TWO_PLAYERS_STAGED",c.original);c.gate(true);g.sendSystemMessage(Component.literal("LAN READY: guest window. Stay still; test waits for focus and grounded input."));return 1;
        }catch(Exception e){c.failure="STAGING_FAILED";s.sendFailure(Component.literal("LAN staging failed; preserve, do not retry. "+e));return 0;}}
    public static int start(CommandSourceStack s){var c=current;if(c==null||!c.source(s)||!c.phase.equals("STAGED")||c.failure!=null||!c.held()||!c.roster())return 0;
        c.server.getPlayerList().getPlayer(c.guest).setGameMode(GameType.SURVIVAL);c.phase="GROUND";score(c.level,"#lan",1);c.event("WAITING_FOR_GUEST_FOCUS_AND_GROUND",c.original);return 1;}
    private boolean roster(){var h=server.getPlayerList().getPlayer(host);var g=guest==null?null:server.getPlayerList().getPlayer(guest);return h!=null&&g!=null&&server.getPlayerList().getPlayerCount()==2&&h.serverLevel()==level&&g.serverLevel()==level&&h.isSpectator()&&h.getHealth()==20&&h.position().distanceToSqr(HOST)<.000001&&g.getHealth()==20&&g.position().distanceToSqr(HOME)<.000001&&g.getGameProfile().getName().equals("ArchitectGuest")&&g.getPersistentData().getString(MARKER).equals(directory.getFileName().toString());}
    public static boolean isolationAllowed(ServerLevel l){var c=current;return c!=null&&c.level==l&&exact(c.server)&&score(l,"#lan")>=0&&score(l,"#lan")<=3&&score(l,"#run")==53&&score(l,"#persist")==5&&c.server.getPlayerList().getPlayerCount()<=2;}
    public static boolean active(MinecraftServer s){var c=current;return c!=null&&c.server==s&&Set.of("GROUND","OPEN","SEALED").contains(c.phase);}
    public static synchronized void hostClient(MinecraftServer s,JsonObject row,boolean key){var c=current;if(!active(s))return;var rows=key?c.hostKeys:c.hostClient;if(rows.size()>=8192)c.failure="HOST_INPUT_OVERFLOW";else rows.add(row.deepCopy());}
    private Path gatePath(){return Path.of(System.getProperty("frozendawn.labLanGate"));}
    private void gate(boolean on)throws Exception{var o=new JsonObject();o.addProperty("active",on);o.addProperty("capture",Set.of("OPEN","SEALED").contains(phase));o.addProperty("captureSession",session);o.addProperty("directory",directory.toAbsolutePath().normalize().toString());o.addProperty("checkout",System.getProperty("frozendawn.labBridge.checkout"));o.addProperty("world",server.getWorldData().getLevelName());o.addProperty("guest",guest==null?"":guest.toString());o.addProperty("port",server.getPort());o.addProperty("gameTime",level.getGameTime());o.addProperty("sampledAtMillis",System.currentTimeMillis());o.addProperty("phase",phase);atomic(gatePath(),o);}
    static void atomic(Path p,JsonObject o)throws Exception{Files.createDirectories(p.getParent());Path tmp=p.resolveSibling(p.getFileName()+".tmp");Files.writeString(tmp,o+"\n",StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
    public static boolean quiet(JsonObject x){try{return x.get("focused").getAsBoolean()&&!x.get("screenOpen").getAsBoolean()&&!x.get("paused").getAsBoolean()&&!x.get("sneaking").getAsBoolean()&&!x.get("flying").getAsBoolean()&&!x.get("useDown").getAsBoolean()&&!x.get("attackDown").getAsBoolean()&&x.getAsJsonObject("mapped").entrySet().stream().noneMatch(e->e.getValue().getAsBoolean())&&x.getAsJsonObject("windowKeys").entrySet().stream().noneMatch(e->e.getValue().getAsBoolean())&&x.getAsJsonObject("input").get("forwardImpulse").getAsDouble()==0&&x.getAsJsonObject("input").get("leftImpulse").getAsDouble()==0;}catch(Exception e){return false;}}
    private JsonObject latest(){try{return read(directory.resolve("guest-latest.json"));}catch(Exception e){return null;}}
    private boolean freshInput(JsonObject x,long now){try{return x!=null&&x.get("captureSession").getAsString().equals(session)&&x.get("player").getAsString().equals(guest.toString())&&x.get("clockSource").getAsString().equals("HOST_FILE_POST_TICK")&&now-x.get("gameTime").getAsLong()>=0&&now-x.get("gameTime").getAsLong()<=3&&System.currentTimeMillis()-x.get("sampledAtMillis").getAsLong()<=750&&x.get("remoteConnection").getAsBoolean()&&x.get("port").getAsInt()==server.getPort();}catch(Exception e){return false;}}
    private boolean homeInput(JsonObject x){return Math.abs(x.get("x").getAsDouble()-HOME.x)<.000001&&Math.abs(x.get("y").getAsDouble()-HOME.y)<.000001&&Math.abs(x.get("z").getAsDouble()-HOME.z)<.000001;}
    private void gap(boolean open){level.setBlock(new BlockPos(16,-44,5),(open?Blocks.AIR:Blocks.GLASS).defaultBlockState(),3);level.setBlock(new BlockPos(16,-43,5),open?Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP):Blocks.GLASS.defaultBlockState(),3);}
    private JsonObject frame(ArchitectEntity a){var o=new JsonObject();o.addProperty("actor",actor.toString());o.addProperty("host",host.toString());o.addProperty("guest",guest==null?"":guest.toString());o.addProperty("gameTime",level.getGameTime());o.addProperty("phase",phase);o.addProperty("phaseTimer",phaseTicks);o.addProperty("timer",ticks);o.addProperty("actorPresent",a!=null);o.addProperty("geometryIntact",LabBlindMeleeObstruction.geometry(level,!phase.equals("OPEN")));o.addProperty("sourcesClean",LabBlindIsolation.unloadSourcesClean(level));o.addProperty("published",server.isPublished());o.addProperty("port",server.getPort());o.addProperty("paused",server.isPaused());o.addProperty("frozen",server.tickRateManager().isFrozen());o.addProperty("sprinting",server.tickRateManager().isSprinting());o.addProperty("stepping",server.tickRateManager().isSteppingForward());
        if(a!=null){o.addProperty("health",a.getHealth());o.addProperty("noAI",a.isNoAi());o.add("position",JSON.toJsonTree(a.position()));o.add("tracks",JSON.toJsonTree(a.blindController().tracks()));o.add("selected",JSON.toJsonTree(a.blindController().signal()));o.addProperty("diagnostics",a.blindController().diagnostics());o.addProperty("targetPresent",a.getTarget()!=null);o.addProperty("mining",a.isMiningBlock());o.addProperty("breaks",a.successfulBreakCount());}
        var roster=new JsonArray();for(var p:server.getPlayerList().getPlayers()){var x=new JsonObject();x.addProperty("uuid",p.getUUID().toString());x.addProperty("name",p.getGameProfile().getName());x.addProperty("gameMode",p.gameMode.getGameModeForPlayer().getName());x.addProperty("health",p.getHealth());x.addProperty("onGround",p.onGround());x.add("position",JSON.toJsonTree(p.position()));roster.add(x);}o.add("players",roster);o.add("hostKnowledge",JSON.toJsonTree(MaeveDirector.worldSnapshot(server,host)));if(guest!=null)o.add("guestKnowledge",JSON.toJsonTree(MaeveDirector.worldSnapshot(server,guest)));return o;}
    private void event(String kind,ArchitectEntity a){var o=frame(a);o.addProperty("event",kind);try{Files.writeString(directory.resolve("events.jsonl"),o+"\n",StandardOpenOption.APPEND);}catch(Exception e){failure="EVENT_ARCHIVE_FAILED";}}
    private boolean acoustic(UUID player,long since){return MaeveDirector.worldSnapshot(server,player).stream().anyMatch(x->x.label().equals("ACOUSTIC_CONTACT")&&x.provenance().stream().anyMatch(e->e.observer().equals(actor)&&e.time()>=since&&e.action().startsWith("ACOUSTIC_ECHO")));}
    public static synchronized void tick(MinecraftServer s){var c=current;if(c==null||c.server!=s)return;if(Set.of("STAGED","GROUND","OPEN","SEALED").contains(c.phase)){if(active(s))c.tick();try{c.gate(Set.of("STAGED","GROUND","OPEN","SEALED").contains(c.phase));}catch(Exception e){c.finish("GATE_WRITE_FAILED");}}}
    private void tick(){long now=level.getGameTime();if(now<=lastTick)return;lastTick=now;phaseTicks++;var a=level.getEntity(actor) instanceof ArchitectEntity x?x:null;var input=latest();
        if(phase.equals("GROUND"))readiness.add(frame(a));else{ticks++;trace.add(frame(a));}
        if(failure!=null){finish(failure);return;}if(!roster()||score(level,"#stage")!=4||score(level,"#lan")!=1||score(level,"#run")!=53||score(level,"#persist")!=5){finish("ROSTER_OR_SCENE_CHANGED");return;}
        if(a!=original||!a.isAlive()||!a.isBlindArchitect()||a.getHealth()!=40||a.position().distanceToSqr(ACTOR)>.001||a.getTarget()!=null||a.isMiningBlock()||a.successfulBreakCount()!=0){finish("ACTOR_OR_HEALTH_CHANGED");return;}
        if(!LabBlindMeleeObstruction.geometry(level,!phase.equals("OPEN"))||!LabBlindIsolation.unloadSourcesClean(level)||!level.getForcedChunks().isEmpty()||server.tickRateManager().isFrozen()||server.tickRateManager().isSprinting()||server.tickRateManager().isSteppingForward()){finish("GEOMETRY_SOURCE_OR_CLOCK_CHANGED");return;}
        if(phase.equals("GROUND")){boolean ready=a.isNoAi()&&a.blindController().tracks().isEmpty()&&a.blindController().signal()==null&&server.getPlayerList().getPlayer(guest).onGround()&&freshInput(input,now)&&quiet(input)&&homeInput(input)&&input.get("onGround").getAsBoolean();stable=ready?stable+1:0;
            if(stable>=40){opened=now;gap(true);a.setNoAi(false);phase="OPEN";phaseTicks=0;event("GUEST_APERTURE_OPENED",a);server.getPlayerList().getPlayer(guest).sendSystemMessage(Component.literal("VIEW STARTED: stay still. Watch it find you, then lose you when glass seals."));}else if(phaseTicks>=3200)finish("GUEST_READINESS_TIMEOUT");return;}
        if(!freshInput(input,now)||!quiet(input)||!homeInput(input)){finish("GUEST_INPUT_STALE_OR_NOT_QUIET");return;}if(a.isNoAi()){finish("AI_HELD_DURING_VIEW");return;}if(acoustic(host,opened)){finish("HOST_KNOWLEDGE_MISATTRIBUTED");return;}
        if(a.blindController().tracks().stream().anyMatch(x->x.subject()!=null&&!x.subject().equals(guest))){finish("FOREIGN_CONTACT_SUBJECT");return;}
        if(phase.equals("OPEN")){var signal=a.blindController().signal();boolean fresh=signal!=null&&signal.sense()==Sense.ECHO&&guest.equals(signal.subject())&&signal.lock(now)&&signal.observed()>opened&&now-signal.observed()<=25&&signal.travel()<=8;
            if(fresh&&!acquired){acquired=true;event("ACTUAL_GUEST_CONTACT",a);}if(acoustic(guest,opened)){if(!attributed)event("GUEST_MAEVE_ACOUSTIC_CONTACT",a);attributed=true;}
            if(phaseTicks>=240){if(!acquired||!attributed){finish("NO_GUEST_CONTACT_OR_KNOWLEDGE");return;}sealed=now;gap(false);phase="SEALED";phaseTicks=0;event("GLASS_SEALED",a);server.getPlayerList().getPlayer(guest).sendSystemMessage(Component.literal("GLASS SEALED: keep still until VIEW COMPLETE."));}}
        else{if(a.blindController().tracks().stream().anyMatch(x->x.observed()>sealed)){finish("POST_SEAL_CONTACT_REFRESH");return;}boolean idle=a.blindController().tracks().isEmpty()&&a.blindController().signal()==null&&a.blindController().diagnostics().startsWith("blind=WANDER ");idleTail=idle?idleTail+1:0;if(phaseTicks>=360)finish(idleTail>=40?null:"NO_NATURAL_IDLE_TAIL");}
    }
    private void finish(String why){if(Set.of("COMPLETE","INCONCLUSIVE").contains(phase))return;String priorPhase=phase;phase=why==null?"COMPLETE":"INCONCLUSIVE";failure=why;gap(false);var a=level.getEntity(actor) instanceof ArchitectEntity x?x:null;if(a!=null)a.setNoAi(true);var g=guest==null?null:server.getPlayerList().getPlayer(guest);if(g!=null){g.setGameMode(GameType.CREATIVE);g.sendSystemMessage(Component.literal(why==null?"VIEW COMPLETE: actor held. Give your first account. Esc does not pause LAN.":"INCONCLUSIVE: actor held. Preserve this attempt. "+why));}score(level,"#stage",why==null?3:4);score(level,"#lan",why==null?2:3);event(phase,a);
        try{gate(false);for(var e:Map.of("server.jsonl",trace,"server-readiness.jsonl",readiness,"host-client.jsonl",hostClient,"host-keys.jsonl",hostKeys).entrySet())Files.writeString(directory.resolve(e.getKey()),String.join("\n",e.getValue().asList().stream().map(Object::toString).toList())+(e.getValue().isEmpty()?"":"\n"),StandardOpenOption.CREATE_NEW);
            var out=new JsonObject();out.addProperty("status",phase);out.addProperty("reason",why);out.addProperty("lastPhase",priorPhase);out.addProperty("actor",actor.toString());out.addProperty("host",host.toString());out.addProperty("guest",guest==null?"":guest.toString());out.addProperty("captureSession",session);out.addProperty("process",PROCESS);out.addProperty("ticks",ticks);out.addProperty("readinessTicks",readiness.size());out.addProperty("actualGuestContact",acquired);out.addProperty("guestMaeveContact",attributed);out.addProperty("idleTail",idleTail);out.addProperty("opened",opened);out.addProperty("sealed",sealed);out.addProperty("end",level.getGameTime());out.addProperty("note","Summary is mechanical evidence only. Guest must acknowledge stopped writers; independently qualify real whole guest input/clock/roster/knowledge and owner account before acceptance. Host stream can be unfocused.");var hashes=new JsonObject();for(String n:List.of("start.json","predecessor.json","players-before-staging.json","events.jsonl","server.jsonl","server-readiness.jsonl","host-client.jsonl","host-keys.jsonl"))if(Files.exists(directory.resolve(n)))hashes.addProperty(n,hash(directory.resolve(n)));out.add("hashes",hashes);write("summary.json",out);
        }catch(Exception e){System.err.println("[BlindLan] Archive failed; preserve: "+e);}}
    public static synchronized void stopping(MinecraftServer s){var c=current;if(c!=null&&c.server==s){if(Set.of("STAGED","GROUND","OPEN","SEALED").contains(c.phase))c.finish("SERVER_STOPPED");current=null;}}
}
