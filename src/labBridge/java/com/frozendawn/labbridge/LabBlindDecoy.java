package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;

/** Real one-snowball decoy; diagnostics never inject a sensory event or assign a destination. */
@net.neoforged.fml.common.EventBusSubscriber(modid="frozendawn")
public final class LabBlindDecoy {
    private static final Gson JSON=new Gson();
    private static final Vec3 PLAYER=new Vec3(17.5,-44,5.5), RECEIVER=new Vec3(9.5,-44,5.5);
    private static final AABB ROOM=new AABB(0,-49,-12,31,-37,11);
    private static final String ACTOR="BlindLabDecoyActor";
    private LabBlindDecoy() {}
    private static int score(ServerLevel level,String name) {
        var objective=level.getScoreboard().getObjective("blind_vib");
        if(objective==null)return -1;
        var value=level.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(name),objective);
        return value==null?-1:value.value();
    }
    private static void score(ServerLevel level,String name,int value) {
        level.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(name),level.getScoreboard().getObjective("blind_vib")).set(value);
    }
    private static ServerPlayer player(CommandSourceStack source) {
        return LabBlindWorkbench.access(source)==1 && (score(source.getLevel(),"#run")>=36 && score(source.getLevel(),"#run")<=53) && score(source.getLevel(),"#case")==score(source.getLevel(),"#run")
                && source.getEntity() instanceof ServerPlayer p && p.getTags().contains("blind_lab_player") ? p:null;
    }
    private static ArchitectEntity actor(CommandSourceStack source,ServerPlayer p) {
        if(p==null || !p.getPersistentData().hasUUID(ACTOR))return null;
        var entity=source.getLevel().getEntity(p.getPersistentData().getUUID(ACTOR));
        return entity instanceof ArchitectEntity a && a.isBlindArchitect() && a.getTags().contains("blind_lab_actor")?a:null;
    }
    private static Path directory(ServerLevel level,UUID id) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-results/run-"+score(level,"#run")+"-"+id);
    }
    public static boolean allowedProjectile(ServerLevel level,net.minecraft.world.entity.Entity entity) {
        return (score(level,"#run")>=36 && score(level,"#run")<=38) && score(level,"#stage")==2 && entity instanceof Snowball ball
                && ball.getOwner() instanceof ServerPlayer p && p.getTags().contains("blind_lab_player") && ROOM.contains(ball.position());
    }
    static void joined(ServerLevel level,Snowball ball) {
        var p=(ServerPlayer)ball.getOwner();
        int count=score(level,"#decoy_balls")+1;score(level,"#decoy_balls",count);
        if(count>16){score(level,"#source_audit_failed",1);return;}
        var row=new JsonObject();row.addProperty("uuid",ball.getUUID().toString());row.addProperty("owner",p.getUUID().toString());
        row.addProperty("gameTime",level.getGameTime());row.addProperty("timer",score(level,"#timer"));row.add("position",JSON.toJsonTree(ball.position()));row.add("velocity",JSON.toJsonTree(ball.getDeltaMovement()));
        row.addProperty("playerYaw",p.getYRot());row.addProperty("playerPitch",p.getXRot());row.addProperty("selectedSlot",p.getInventory().selected);
        try {Files.writeString(directory(level,p.getPersistentData().getUUID(ACTOR)).resolve("projectiles.jsonl"),row+"\n",StandardOpenOption.APPEND);}
        catch(Exception failure){score(level,"#source_audit_failed",1);}
    }
    @net.neoforged.bus.api.SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public static void impact(net.neoforged.neoforge.event.VanillaGameEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level)||event.getVanillaEvent()!=net.minecraft.world.level.gameevent.GameEvent.PROJECTILE_LAND
                ||!allowedProjectile(level,event.getContext().sourceEntity()))return;
        var ball=(Snowball)event.getContext().sourceEntity();var p=(ServerPlayer)ball.getOwner();
        int count=score(level,"#decoy_impacts")+1;score(level,"#decoy_impacts",count);
        if(count>16){score(level,"#source_audit_failed",1);return;}
        var row=new JsonObject();row.addProperty("projectile",ball.getUUID().toString());row.addProperty("owner",p.getUUID().toString());
        row.addProperty("gameTime",level.getGameTime());row.addProperty("timer",score(level,"#timer"));row.add("position",JSON.toJsonTree(event.getEventPosition()));row.add("physicalPosition",JSON.toJsonTree(ball.position()));
        row.add("velocity",JSON.toJsonTree(ball.getDeltaMovement()));
        try {Files.writeString(directory(level,p.getPersistentData().getUUID(ACTOR)).resolve("impacts.jsonl"),row+"\n",StandardOpenOption.APPEND);}
        catch(Exception failure){score(level,"#source_audit_failed",1);}
    }
    public static int equip(CommandSourceStack source) {
        var p=player(source);if(p==null||!p.isCreative()||score(source.getLevel(),"#stage")!=9)return 0;
        p.getInventory().selected=0;if((score(source.getLevel(),"#run")==51||score(source.getLevel(),"#run")==52||score(source.getLevel(),"#run")==53))p.setHealth(20);if(score(source.getLevel(),"#run")==50)LabBlindMelee.equip(p);p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));return 1;
    }
    /** Preserve completed36 as recorded; trajectory success is deliberately not a predecessor requirement. */
    public static int repeatReady(CommandSourceStack source) { return completedReady(source,36); }
    public static int contactReady(CommandSourceStack source) { return completedReady(source,37); }
    public static int doorReady(CommandSourceStack source) { return completedReady(source,38); }
    public static int doorRepeatReady(CommandSourceStack source) { return completedReady(source,39); }
    public static int machineReady(CommandSourceStack source) { return completedReady(source,40); }
    public static int doorContactReady(CommandSourceStack source) { return completedReady(source,41); }
    public static int doorContactRepeatReady(CommandSourceStack source) {
        if(completedReady(source,42)==1)return 1;
        var p=player(source);var level=source.getLevel();var a=actor(source,p);
        if(p==null||a==null||score(level,"#run")!=42||score(level,"#case")!=42||score(level,"#stage")!=3
                ||score(level,"#timer")!=480||score(level,"#frames")!=480||score(level,"#last_frame")!=480
                ||score(level,"#archived")!=0||score(level,"#inconclusive")!=1||!p.isCreative()
                ||!a.isAlive()||!a.isNoAi()||a.getHealth()!=40||!geometry(level)||!LabBlindIsolation.sourcesClean(level)
                ||!LabBlindIsolation.unrelated(level).isEmpty())return 0;
        return LabBlindDoorRepeat.ready(source,p,a)?1:0;
    }
    public static int bellReady(CommandSourceStack source) {return completedReady(source,43);}
    public static int explosionReady(CommandSourceStack source) {return completedReady(source,44);}
    public static int heatReady(CommandSourceStack source) {return completedReady(source,45);}
    public static int breachReady(CommandSourceStack source) {return completedReady(source,46);}
    public static int protectedWallReady(CommandSourceStack source) {return completedReady(source,47);}
    public static int griefingReady(CommandSourceStack source) {return completedReady(source,48);}
    public static int meleeReady(CommandSourceStack source) {return completedReady(source,49);}
    public static int meleeRangeReady(CommandSourceStack source) {return completedReady(source,50);}
    public static int meleeRangeRepeatReady(CommandSourceStack source) {return completedReady(source,51);}
    public static int meleeObstructionReady(CommandSourceStack source) {return completedReady(source,52);}
    public static int unloadReady(CommandSourceStack source) {return completedReady(source,53);}
    public static int unloadRepeatReady(CommandSourceStack source) {return completedReady(source,53,true);}
    private static int completedReady(CommandSourceStack source,int expected) {
        return completedReady(source,expected,false);
    }
    private static int completedReady(CommandSourceStack source,int expected,boolean unloadRepeat) {
        var p=player(source);var level=source.getLevel();var a=actor(source,p);
        if(p==null||a==null||score(level,"#run")!=expected||score(level,"#stage")!=(unloadRepeat?4:3)||unloadRepeat&&score(level,"#unload")!=3||score(level,"#timer")!=480
                ||score(level,"#frames")!=480||score(level,"#last_frame")!=480||score(level,"#archived")!=1||score(level,"#inconclusive")!=0
                ||!p.isCreative()||!a.isAlive()||!a.isNoAi()||(expected>=45?(a.getHealth()<=0||a.getHealth()>40):a.getHealth()!=(expected==44?36:40))||!geometry(level)||!(unloadRepeat?LabBlindIsolation.unloadSourcesClean(level):LabBlindIsolation.sourcesClean(level))
                ||!LabBlindIsolation.unrelated(level).isEmpty())return 0;
        try {
            var d=directory(level,a.getUUID());var summary=com.google.gson.JsonParser.parseString(Files.readString(d.resolve("summary.json"))).getAsJsonObject();
            var input=source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-input/probe-"+p.getPersistentData().getUUID("BlindLabInputProbe"));
            var initial=com.google.gson.JsonParser.parseString(Files.readString(input.resolve("start.json"))).getAsJsonObject();
            var capture=com.google.gson.JsonParser.parseString(Files.readString(input.resolve("summary.json"))).getAsJsonObject();
            if(!summary.get("complete").getAsBoolean()||summary.get("inconclusiveFrames").getAsInt()!=0||!(expected==53?summary.get("meleeObstructionPerformed").getAsBoolean():(expected==51||expected==52)?summary.get("meleeRangePerformed").getAsBoolean():expected==50?summary.get("meleePerformed").getAsBoolean():expected==49?summary.get("griefingDisabledPerformed").getAsBoolean():expected==48?summary.get("protectedWallPerformed").getAsBoolean():expected==47?summary.get("breachPerformed").getAsBoolean():expected==46?summary.get("heatCyclePerformed").getAsBoolean():expected==45?summary.get("explosionCyclePerformed").getAsBoolean():expected==44?summary.get("bellCyclePerformed").getAsBoolean():expected==41?summary.get("machineCyclePerformed").getAsBoolean():expected>=39?summary.get("doorPairPerformed").getAsBoolean():summary.get("oneProjectilePerformed").getAsBoolean())
                    ||summary.get("run").getAsInt()!=expected||summary.get("frames").getAsInt()!=480||!summary.get("actor").getAsString().equals(a.getUUID().toString())
                    ||!summary.get("inputProbe").equals(capture.get("probe"))||!initial.get("actor").equals(summary.get("actor"))
                    ||!initial.get("player").getAsString().equals(p.getUUID().toString())||initial.get("viewRun").getAsInt()!=expected)return 0;
            if(expected>=45&&a.getHealth()!=summary.get("actorHealth").getAsFloat())return 0;
            if(expected>=37&&expected<=38&&!summary.get("remoteLanding").getAsBoolean())return 0;
            if((expected==38||expected==42||expected==43)&&(!summary.get("freshContactDecoyExercised").getAsBoolean()||!summary.get("freshContactPriorityHeld").getAsBoolean()))return 0;
            for(String name:java.util.List.of("trace.jsonl","decisions.json","projectiles.jsonl","impacts.jsonl"))if(!hash(d.resolve(name)).equals(summary.get(name.replace('.','_')+"Sha256").getAsString()))return 0;
            if((expected>=39&&expected<=40||expected==42||expected==43)&&!hash(d.resolve("doors.jsonl")).equals(summary.get("doors_jsonlSha256").getAsString()))return 0;
            if(expected==51||expected==52){for(String name:java.util.List.of("range-damage.jsonl","range-shutter.jsonl"))if(!hash(d.resolve(name)).equals(summary.get(name.replace('.','_')+"Sha256").getAsString()))return 0;if(!LabBlindMeleeRange.performed(d,com.google.gson.JsonParser.parseString(Files.readString(d.resolve("decisions.json"))).getAsJsonArray()))return 0;}
            if(expected==53){for(String name:java.util.List.of("obstruction-damage.jsonl","obstruction-shutter.jsonl"))if(!hash(d.resolve(name)).equals(summary.get(name.replace('.','_')+"Sha256").getAsString()))return 0;if(!LabBlindMeleeObstruction.performed(d,com.google.gson.JsonParser.parseString(Files.readString(d.resolve("decisions.json"))).getAsJsonArray()))return 0;}
            if(expected==50){for(String name:java.util.List.of("melee-damage.jsonl","melee-shutter.jsonl"))if(!hash(d.resolve(name)).equals(summary.get(name.replace('.','_')+"Sha256").getAsString()))return 0;if(!LabBlindMelee.performed(d,com.google.gson.JsonParser.parseString(Files.readString(d.resolve("decisions.json"))).getAsJsonArray()))return 0;}
            if(expected==49&&(!hash(d.resolve("doors.jsonl")).equals(summary.get("doors_jsonlSha256").getAsString())||!LabBlindGriefing.performed(d,com.google.gson.JsonParser.parseString(Files.readString(d.resolve("decisions.json"))).getAsJsonArray())))return 0;
            if(expected==48&&(!hash(d.resolve("doors.jsonl")).equals(summary.get("doors_jsonlSha256").getAsString())
                    ||!LabBlindProtectedWall.performed(d,com.google.gson.JsonParser.parseString(Files.readString(d.resolve("decisions.json"))).getAsJsonArray())))return 0;
            if(expected==47&&(!hash(d.resolve("doors.jsonl")).equals(summary.get("doors_jsonlSha256").getAsString())
                    ||!LabBlindBreach.performed(d,com.google.gson.JsonParser.parseString(Files.readString(d.resolve("decisions.json"))).getAsJsonArray())))return 0;
            if(expected==41&&(!hash(d.resolve("machines.jsonl")).equals(summary.get("machines_jsonlSha256").getAsString())
                    ||!LabBlindMachine.performed(d,com.google.gson.JsonParser.parseString(Files.readString(d.resolve("decisions.json"))).getAsJsonArray())))return 0;
            if(expected==44&&(!hash(d.resolve("bells.jsonl")).equals(summary.get("bells_jsonlSha256").getAsString())||!LabBlindBell.performed(d,com.google.gson.JsonParser.parseString(Files.readString(d.resolve("decisions.json"))).getAsJsonArray())))return 0;
            if(expected==46){for(String name:java.util.List.of("heaters.jsonl","heat-damage.jsonl"))if(!hash(d.resolve(name)).equals(summary.get(name.replace('.','_')+"Sha256").getAsString()))return 0;if(!LabBlindHeat.performed(d,com.google.gson.JsonParser.parseString(Files.readString(d.resolve("decisions.json"))).getAsJsonArray()))return 0;}
            if(expected==45){for(String name:java.util.List.of("blasts.jsonl","blast-damage.jsonl"))if(!hash(d.resolve(name)).equals(summary.get(name.replace('.','_')+"Sha256").getAsString()))return 0;if(!LabBlindExplosion.performed(d,com.google.gson.JsonParser.parseString(Files.readString(d.resolve("decisions.json"))).getAsJsonArray()))return 0;}
            for(var entry:capture.getAsJsonObject("hashes").entrySet())if(!hash(input.resolve(entry.getKey())).equals(entry.getValue().getAsString()))return 0;
            java.util.function.Function<Path,java.util.List<JsonObject>> rows=file->{try{return Files.readAllLines(file).stream().map(line->com.google.gson.JsonParser.parseString(line).getAsJsonObject()).toList();}catch(Exception failed){return java.util.List.of();}};
            if(!LabInputTimeline.qualifies(initial,capture,rows.apply(input.resolve("server.jsonl")),rows.apply(input.resolve("client.jsonl")),rows.apply(input.resolve("keys.jsonl")))
                    && !(expected==38&&LabBlindDoor.preservedHuman38(summary,capture,input))
                    && !(expected==45&&LabBlindHeat.reviewed45(source.getServer().getWorldPath(LevelResource.ROOT)))
                    && !(expected==51&&LabBlindMeleeRange.reviewed51(source.getServer().getWorldPath(LevelResource.ROOT))))return 0;
            if(!p.getPersistentData().hasUUID("BlindLabGroundCheck")||!LabGroundCheck.retained(source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-ground"),p.getPersistentData().getUUID("BlindLabGroundCheck"),p.getUUID(),a.getUUID(),expected))return 0;
            return 1;
        }catch(Exception malformed){return 0;}
    }
    public static boolean geometry(ServerLevel level) {
        if(score(level,"#run")==53)return LabBlindMeleeObstruction.geometry(level);
        if((score(level,"#run")==51||score(level,"#run")==52))return LabBlindMeleeRange.geometry(level);
        if(score(level,"#run")==50)return LabBlindMelee.geometry(level);
        if(score(level,"#run")==49)return LabBlindGriefing.geometry(level);
        if(score(level,"#run")==48)return LabBlindProtectedWall.geometry(level);
        if(score(level,"#run")==47)return LabBlindBreach.geometry(level);
        if(level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))return false;
        for(int x=4;x<=19;x++)for(int z=4;z<=6;z++) {
            boolean heaterGap=score(level,"#run")==41&&(x==18&&z==5||x==19&&z!=5);
            var floor=heaterGap?Blocks.AIR:score(level,"#run")>=40&&x<=(score(level,"#run")>=44?9:12)?Blocks.GLASS:x<=14 || x>=17?Blocks.STONE:Blocks.AIR;
            if(!level.getBlockState(new BlockPos(x,-45,z)).is(floor))return false;
            if(!level.getBlockState(new BlockPos(x,-40,z)).is(score(level,"#run")==41&&x==16?Blocks.AIR:x==(score(level,"#run")>=37?11:9)&&z==5?Blocks.SEA_LANTERN:Blocks.GLASS))return false;
        }
        for(int y=-44;y<=-41;y++)for(int x=3;x<=20;x++) {
            if(!level.getBlockState(new BlockPos(x,y,3)).is(score(level,"#run")==41&&x==16?Blocks.AIR:Blocks.GLASS) || !level.getBlockState(new BlockPos(x,y,7)).is(score(level,"#run")==41&&x==16?Blocks.AIR:Blocks.GLASS))return false;
        }
        for(int y=-44;y<=-41;y++)for(int z=4;z<=6;z++) {
            if(!level.getBlockState(new BlockPos(3,y,z)).is(Blocks.GLASS)||!level.getBlockState(new BlockPos(20,y,z)).is(score(level,"#run")==41&&y==-44&&z==5?Blocks.AIR:Blocks.GLASS))return false;
            if(!level.getBlockState(new BlockPos(15,y,z)).is((y<=-43 || score(level,"#run")==46) && !(((score(level,"#run")==51||score(level,"#run")==52||score(level,"#run")==53)||score(level,"#run")==50||score(level,"#run")==38||(score(level,"#run")==42||score(level,"#run")==43)) && score(level,"#decoy_cover")==0)?Blocks.GLASS:Blocks.AIR))return false;
        }
        if(score(level,"#run")>=40)for(int x=4;x<=(score(level,"#run")>=44?9:12);x++)for(int y=-44;y<=-41;y++)for(int z=4;z<=6;z++)
            if(!level.getBlockState(new BlockPos(x,y,z)).is(Blocks.GLASS))return false;
        if(score(level,"#run")==41)for(int y=-45;y<=-40;y++)for(int z=3;z<=7;z++)if(!level.getBlockState(new BlockPos(16,y,z)).isAir())return false;
        return score(level,"#run")==46?LabBlindHeat.geometry(level):score(level,"#run")==45?LabBlindExplosion.geometry(level):score(level,"#run")==44?LabBlindBell.geometry(level):score(level,"#run")==41?LabBlindMachine.geometry(level):score(level,"#run")<39 || LabBlindDoor.geometry(level);
    }
    public static UUID inputViewActor(CommandSourceStack source) {
        var p=player(source);if(p==null || score(source.getLevel(),"#stage")!=1 || score(source.getLevel(),"#timer")!=0
                || (score(source.getLevel(),"#run")==51||score(source.getLevel(),"#run")==52||score(source.getLevel(),"#run")==53)&&(p.getHealth()!=20||p.getPersistentData().contains("BlindLabMeleeKnockbackBase")) || score(source.getLevel(),"#run")==50&&(p.getHealth()!=20||p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)!=1) || !geometry(source.getLevel()) || score(source.getLevel(),"#run")==49&&!LabBlindGriefing.fresh(source.getLevel()) || score(source.getLevel(),"#run")==48&&!LabBlindProtectedWall.fresh(source.getLevel()) || score(source.getLevel(),"#run")==47&&!LabBlindBreach.fresh(source.getLevel()) || score(source.getLevel(),"#run")==41&&LabBlindMachine.state(source.getLevel()).get("lit").getAsBoolean() || !LabBlindIsolation.enabled(source.getLevel()) || !LabBlindIsolation.sourcesClean(source.getLevel())
                || !LabBlindIsolation.unrelated(source.getLevel()).isEmpty() || p.position().distanceToSqr(PLAYER)>.000001)return null;
        var actors=source.getLevel().getEntitiesOfClass(ArchitectEntity.class,ROOM);
        if(actors.size()!=1)return null;var a=actors.getFirst();
        return a.isBlindArchitect()&&a.getTags().contains("blind_lab_actor")&&a.isAlive()&&a.isNoAi()&&a.onGround()
                &&a.getHealth()==40&&a.position().distanceToSqr(score(source.getLevel(),"#run")==53?new Vec3(15.5,-44,5.5):(score(source.getLevel(),"#run")==51||score(source.getLevel(),"#run")==52)?new Vec3(14.5,-44,5.5):score(source.getLevel(),"#run")==50?new Vec3(15.5,-44,5.5):score(source.getLevel(),"#run")>=47?new Vec3(12.5,-44,5.5):score(source.getLevel(),"#run")>=40?new Vec3(13.5,-44,5.5):RECEIVER)<.001&&a.blindController().tracks().isEmpty()?a.getUUID():null;
    }
    private static JsonObject frame(CommandSourceStack source,ServerPlayer p,ArchitectEntity a) {
        var level=source.getLevel();var out=new JsonObject();out.addProperty("run",score(level,"#run"));out.addProperty("case",score(level,"#run"));
        out.addProperty("timer",score(level,"#timer"));out.addProperty("gameTime",level.getGameTime());out.addProperty("actor",p.getPersistentData().getUUID(ACTOR).toString());
        out.addProperty("inputProbe",p.getPersistentData().getUUID("BlindLabInputProbe").toString());out.addProperty("actorPresent",a!=null);
        if(a!=null) {
            out.addProperty("actorAlive",a.isAlive());out.addProperty("noiseStunPose",a.isBlindNoiseStunned());out.addProperty("actorHealth",a.getHealth());out.addProperty("actorNoAi",a.isNoAi());out.addProperty("actorOnGround",a.onGround());
            out.add("actorPosition",JSON.toJsonTree(a.position()));out.addProperty("headYaw",a.getYHeadRot());out.addProperty("actorPitch",a.getXRot());out.addProperty("bodyYaw",a.yBodyRot);
            out.addProperty("navigationDone",a.getNavigation().isDone());out.addProperty("vanillaTargetPresent",a.getTarget()!=null);
            out.addProperty("diagnostics",a.blindController().diagnostics());out.add("selected",JSON.toJsonTree(a.blindController().signal()));
            out.addProperty("selectedLock",a.blindController().signal()!=null&&a.blindController().signal().lock(level.getGameTime()));
            out.add("tracks",JSON.toJsonTree(a.blindController().tracks()));out.add("echo",JSON.toJsonTree(a.blindController().lastEcho()));
            out.addProperty("journalDropped",a.decisionJournal().dropped());out.addProperty("journalEntries",a.decisionJournal().entries().size());
        }
        out.addProperty("player",p.getUUID().toString());out.add("playerPosition",JSON.toJsonTree(p.position()));out.addProperty("playerOnGround",p.onGround());out.addProperty("playerHealth",p.getHealth());
        out.addProperty("playerYaw",p.getYRot());out.addProperty("playerPitch",p.getXRot());out.addProperty("selectedSlot",p.getInventory().selected);
        var flight=new com.google.gson.JsonArray();for(var ball:level.getEntitiesOfClass(Snowball.class,ROOM)) {var b=new JsonObject();b.addProperty("uuid",ball.getUUID().toString());b.add("position",JSON.toJsonTree(ball.position()));b.add("velocity",JSON.toJsonTree(ball.getDeltaMovement()));flight.add(b);}out.add("physicalProjectiles",flight);
        out.addProperty("playerMode",p.gameMode.getGameModeForPlayer().getName());out.addProperty("playerPose",p.getPose().name());out.addProperty("playerSneaking",p.isShiftKeyDown());out.addProperty("playerSprinting",p.isSprinting());
        out.addProperty("playerFlying",p.getAbilities().flying);out.addProperty("geometryIntact",geometry(level));out.add("isolation",LabBlindIsolation.snapshot(level));
        if((score(level,"#run")>=39&&score(level,"#run")<=40||(score(level,"#run")==42||score(level,"#run")==43)))out.add("door",LabBlindDoor.state(level));
        if(score(level,"#run")==44)out.add("bell",LabBlindBell.state(level));
        if(score(level,"#run")==41)out.add("machine",LabBlindMachine.state(level));
        if(score(level,"#run")==46&&a!=null){out.add("heater",LabBlindHeat.state(level));out.addProperty("heaterDistance",a.position().distanceTo(LabBlindHeat.MACHINE.getCenter()));out.addProperty("fireTicks",a.getRemainingFireTicks());}
        if((score(level,"#run")==51||score(level,"#run")==52)&&a!=null)out.add("meleeRange",LabBlindMelee.state(level,a,p));
        if(score(level,"#run")==53&&a!=null)out.add("meleeObstruction",LabBlindMelee.state(level,a,p));
        if(score(level,"#run")==50&&a!=null)out.add("melee",LabBlindMelee.state(level,a,p));
        if(score(level,"#run")==49&&a!=null)out.add("griefing",LabBlindGriefing.state(level,a));
        if(score(level,"#run")==48&&a!=null)out.add("protectedWall",LabBlindProtectedWall.state(level,a));
        if(score(level,"#run")==47&&a!=null)out.add("breach",LabBlindBreach.state(level,a));
        out.addProperty("coverClosedAt",score(level,"#decoy_cover"));out.addProperty("projectilesLaunched",score(level,"#decoy_balls"));out.addProperty("projectileImpacts",score(level,"#decoy_impacts"));out.addProperty("inconclusiveFrames",score(level,"#inconclusive"));return out;
    }
    public static int start(CommandSourceStack source) {
        var p=player(source);var id=inputViewActor(source);if(p==null||id==null||p.gameMode.getGameModeForPlayer()!=GameType.SURVIVAL||!p.onGround()
                ||!p.getPersistentData().hasUUID("BlindLabGroundCheck")||!p.getPersistentData().hasUUID("BlindLabInputProbe")
                ||!id.equals(p.getPersistentData().getUUID("BlindLabInputActor"))
                ||!LabGroundCheck.retained(source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-ground"),p.getPersistentData().getUUID("BlindLabGroundCheck"),p.getUUID(),id,score(source.getLevel(),"#run")))return 0;
        var a=(ArchitectEntity)source.getLevel().getEntity(id);p.getPersistentData().putUUID(ACTOR,id);
        try {
            var d=directory(source.getLevel(),id);Files.createDirectories(d);
            Files.writeString(d.resolve("start.json"),frame(source,p,a)+"\n",StandardOpenOption.CREATE_NEW);
            for(String name:java.util.List.of("trace.jsonl","projectiles.jsonl","impacts.jsonl"))Files.writeString(d.resolve(name),"",StandardOpenOption.CREATE_NEW);
            if((score(source.getLevel(),"#run")>=39&&score(source.getLevel(),"#run")<=40||(score(source.getLevel(),"#run")==42||score(source.getLevel(),"#run")==43||(score(source.getLevel(),"#run")==47||score(source.getLevel(),"#run")==48||score(source.getLevel(),"#run")==49))))Files.writeString(d.resolve("doors.jsonl"),"",StandardOpenOption.CREATE_NEW);
            if((score(source.getLevel(),"#run")==51||score(source.getLevel(),"#run")==52))for(String name:java.util.List.of("range-damage.jsonl","range-shutter.jsonl"))Files.writeString(d.resolve(name),"",StandardOpenOption.CREATE_NEW);
            if(score(source.getLevel(),"#run")==53)for(String name:java.util.List.of("obstruction-damage.jsonl","obstruction-shutter.jsonl"))Files.writeString(d.resolve(name),"",StandardOpenOption.CREATE_NEW);
            if(score(source.getLevel(),"#run")==50)for(String name:java.util.List.of("melee-damage.jsonl","melee-shutter.jsonl"))Files.writeString(d.resolve(name),"",StandardOpenOption.CREATE_NEW);
            if(score(source.getLevel(),"#run")==46)for(String name:java.util.List.of("heaters.jsonl","heat-damage.jsonl"))Files.writeString(d.resolve(name),"",StandardOpenOption.CREATE_NEW);
            if(score(source.getLevel(),"#run")==45)for(String name:java.util.List.of("blasts.jsonl","blast-damage.jsonl"))Files.writeString(d.resolve(name),"",StandardOpenOption.CREATE_NEW);
            if(score(source.getLevel(),"#run")==44)Files.writeString(d.resolve("bells.jsonl"),"",StandardOpenOption.CREATE_NEW);
            if(score(source.getLevel(),"#run")==41)Files.writeString(d.resolve("machines.jsonl"),"",StandardOpenOption.CREATE_NEW);
        }catch(Exception failure){source.sendFailure(net.minecraft.network.chat.Component.literal("Decoy evidence setup failed. Preserve; do not retry."));return 0;}
        p.getPersistentData().putLong("BlindLabDecoyTick",source.getLevel().getGameTime()-1);
        a.startDecisionRecording(null);a.decisionJournal().useExtendedLabBuffer();score(source.getLevel(),"#stage",2);a.setNoAi(false);return 1;
    }
    public static int tick(CommandSourceStack source) {
        var p=player(source);var level=source.getLevel();if(p==null||score(level,"#stage")!=2)return 0;
        long now=level.getGameTime();if(now<=p.getPersistentData().getLong("BlindLabDecoyTick"))return 0;p.getPersistentData().putLong("BlindLabDecoyTick",now);
        int timer=score(level,"#timer")+1;if(timer>490)return 0;score(level,"#timer",timer);var a=actor(source,p);
        // The fixture closes a physical aperture only after an ordinary fresh return.
        // This observes existing confidence; it never creates or boosts a signal.
        if((score(level,"#run")==38||(score(level,"#run")==42||score(level,"#run")==43))&&score(level,"#decoy_cover")==0&&a!=null) {
            var contact=a.blindController().signal();
            if(contact!=null&&contact.lock(now)&&now-contact.observed()<=10) {
                for(int y=-44;y<=-43;y++)for(int z=4;z<=6;z++)level.setBlock(new BlockPos(15,y,z),Blocks.GLASS.defaultBlockState(),3);
                score(level,"#decoy_cover",timer);
            }
        }
        boolean obstructionControlValid=score(level,"#run")!=53||LabBlindMeleeObstruction.control(source,directory(level,p.getPersistentData().getUUID(ACTOR)),timer);
        boolean rangeControlValid=(score(level,"#run")!=51&&score(level,"#run")!=52)||LabBlindMeleeRange.control(source,directory(level,p.getPersistentData().getUUID(ACTOR)),timer);
        boolean meleeControlValid=score(level,"#run")!=50||LabBlindMelee.control(source,directory(level,p.getPersistentData().getUUID(ACTOR)),timer);
        boolean heatControlValid=score(level,"#run")!=46||LabBlindHeat.control(source,directory(level,p.getPersistentData().getUUID(ACTOR)),timer);
        boolean blastControlValid=score(level,"#run")!=45||LabBlindExplosion.control(source,directory(level,p.getPersistentData().getUUID(ACTOR)),timer,a);
        boolean bellControlValid=score(level,"#run")!=44||LabBlindBell.control(source,directory(level,p.getPersistentData().getUUID(ACTOR)),timer);
        boolean machineControlValid=score(level,"#run")!=41||LabBlindMachine.control(source,directory(level,p.getPersistentData().getUUID(ACTOR)),timer);
        boolean invalid=!obstructionControlValid||!rangeControlValid||!meleeControlValid||!heatControlValid||!blastControlValid||!bellControlValid||!machineControlValid||a==null||!a.isAlive()||!p.isAlive()||a.isNoAi()||(score(level,"#run")>=47?a.getHealth()!=40:score(level,"#run")>=45?(a.getHealth()<=0||a.getHealth()>40):score(level,"#run")==44?(a.getHealth()<36||a.getHealth()>40):a.getHealth()!=40)||(score(level,"#run")==50?(p.getHealth()<=0||p.getHealth()>20):p.getHealth()!=20)||a.getTarget()!=null||!ROOM.contains(a.position())
                ||score(level,"#run")>=40&&score(level,"#run")!=50&&(score(level,"#run")!=51&&score(level,"#run")!=52&&score(level,"#run")!=53)&&a!=null&&!new AABB(score(level,"#run")>=47?12:score(level,"#run")>=44?10:13,-44,4,15,-40,7).contains(a.position())
                ||!geometry(level)||!LabBlindIsolation.enabled(level)||!LabBlindIsolation.unrelated(level).isEmpty()||!LabBlindIsolation.sourcesClean(level)
                ||p.position().distanceToSqr(PLAYER)>.000001||!p.onGround()||p.isShiftKeyDown()||p.isSprinting()||p.getAbilities().flying||p.gameMode.getGameModeForPlayer()!=GameType.SURVIVAL
                ||score(level,"#decoy_balls")>(score(level,"#run")>=39?0:1);
        if(a!=null)invalid|=score(level,"#run")==41&&a.blindController().lastVibration()!=null&&a.blindController().lastVibration().unknown()||a.decisionJournal().dropped()>0||a.blindController().tracks().stream().anyMatch(t->
                !((t.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.HEARING||score(level,"#run")==46&&t.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.VIBRATION)&&t.subject()==null)
                && !(((score(level,"#run")==51||score(level,"#run")==52||score(level,"#run")==53)||score(level,"#run")==50||score(level,"#run")==38||(score(level,"#run")==42||score(level,"#run")==43))&&t.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.ECHO&&p.getUUID().equals(t.subject())&&(score(level,"#decoy_cover")==0||t.observed()<=now-timer+score(level,"#decoy_cover"))));
        if(score(level,"#run")>=48&&score(level,"#run")!=50&&(score(level,"#run")!=51&&score(level,"#run")!=52&&score(level,"#run")!=53)&&a!=null)invalid|=a.successfulBreakCount()!=0||a.isBlindNoiseStunned()||a.getX()>=13;
        if(score(level,"#run")==47&&a!=null)invalid|=LabBlindBreach.state(level,a).get("removed").getAsLong()!=a.successfulBreakCount()||a.isBlindNoiseStunned();
        if(invalid)score(level,"#inconclusive",score(level,"#inconclusive")+1);
        var row=frame(source,p,a);
        try {Files.writeString(directory(level,p.getPersistentData().getUUID(ACTOR)).resolve("trace.jsonl"),row+"\n",StandardOpenOption.APPEND);}
        catch(Exception failure){score(level,"#stage",3);if(a!=null)a.setNoAi(true);p.setGameMode(GameType.CREATIVE);return 0;}
        score(level,"#frames",timer);score(level,"#last_frame",timer);
        if(timer<480)return 1;
        try {
            var d=directory(level,p.getPersistentData().getUUID(ACTOR));
            var entries=JSON.toJsonTree(a==null?java.util.List.of():a.decisionJournal().entries()).getAsJsonArray();
            long impacts=java.util.stream.StreamSupport.stream(entries.spliterator(),false).filter(e->e.getAsJsonObject().get("event").getAsString().equals("BLIND_AIR")&&e.getAsJsonObject().get("detail").getAsString().contains("projectile_land")).count();
            var joins=Files.readAllLines(d.resolve("projectiles.jsonl"));
            boolean performed=score(level,"#decoy_balls")==1&&joins.size()==1&&score(level,"#decoy_impacts")==1&&Files.readAllLines(d.resolve("impacts.jsonl")).size()==1;
            if(performed){int at=com.google.gson.JsonParser.parseString(joins.getFirst()).getAsJsonObject().get("timer").getAsInt();performed=score(level,"#run")==38?score(level,"#decoy_cover")>0&&at>=score(level,"#decoy_cover")&&at<=score(level,"#decoy_cover")+40:at>=80&&at<=260;}
            boolean remote=false;if(performed){var landing=com.google.gson.JsonParser.parseString(Files.readAllLines(d.resolve("impacts.jsonl")).getFirst()).getAsJsonObject();remote=landing.getAsJsonObject("position").get("x").getAsDouble()<=14&&landing.getAsJsonObject("physicalPosition").get("x").getAsDouble()<=14;}
            row.addProperty("remoteLanding",remote);
            if((score(level,"#run")>=39&&score(level,"#run")<=40||(score(level,"#run")==42||score(level,"#run")==43))) {
                performed=(score(level,"#run")==42||score(level,"#run")==43)?LabBlindDoor.performedContact(d,score(level,"#decoy_cover")):LabBlindDoor.performed(d);
                row.addProperty("doorPairPerformed",performed);
                row.addProperty("doors_jsonlSha256",hash(d.resolve("doors.jsonl")));
            }
            if(score(level,"#run")==41) {performed=LabBlindMachine.performed(d,entries);row.addProperty("machineCyclePerformed",performed);row.addProperty("machines_jsonlSha256",hash(d.resolve("machines.jsonl")));}
            if(score(level,"#run")==44) {performed=LabBlindBell.performed(d,entries);row.addProperty("bellCyclePerformed",performed);row.addProperty("bells_jsonlSha256",hash(d.resolve("bells.jsonl")));}
            if(score(level,"#run")==45) {performed=LabBlindExplosion.performed(d,entries);row.addProperty("explosionCyclePerformed",performed);for(String name:java.util.List.of("blasts.jsonl","blast-damage.jsonl"))row.addProperty(name.replace('.','_')+"Sha256",hash(d.resolve(name)));}
            if((score(level,"#run")==51||score(level,"#run")==52)) {performed=LabBlindMeleeRange.performed(d,entries);row.addProperty("meleeRangePerformed",performed);for(String name:java.util.List.of("range-damage.jsonl","range-shutter.jsonl"))row.addProperty(name.replace('.','_')+"Sha256",hash(d.resolve(name)));}
            if(score(level,"#run")==53) {performed=LabBlindMeleeObstruction.performed(d,entries);row.addProperty("meleeObstructionPerformed",performed);for(String name:java.util.List.of("obstruction-damage.jsonl","obstruction-shutter.jsonl"))row.addProperty(name.replace('.','_')+"Sha256",hash(d.resolve(name)));}
            if(score(level,"#run")==50) {performed=LabBlindMelee.performed(d,entries);row.addProperty("meleePerformed",performed);for(String name:java.util.List.of("melee-damage.jsonl","melee-shutter.jsonl"))row.addProperty(name.replace('.','_')+"Sha256",hash(d.resolve(name)));}
            if(score(level,"#run")==49) {performed=LabBlindGriefing.performed(d,entries);row.addProperty("griefingDisabledPerformed",performed);row.addProperty("doors_jsonlSha256",hash(d.resolve("doors.jsonl")));}
            if(score(level,"#run")==48) {performed=LabBlindProtectedWall.performed(d,entries);row.addProperty("protectedWallPerformed",performed);row.addProperty("doors_jsonlSha256",hash(d.resolve("doors.jsonl")));}
            if(score(level,"#run")==47) {performed=LabBlindBreach.performed(d,entries);row.addProperty("breachPerformed",performed);row.addProperty("doors_jsonlSha256",hash(d.resolve("doors.jsonl")));}
            if(score(level,"#run")==46) {performed=LabBlindHeat.performed(d,entries);row.addProperty("heatCyclePerformed",performed);for(String name:java.util.List.of("heaters.jsonl","heat-damage.jsonl"))row.addProperty(name.replace('.','_')+"Sha256",hash(d.resolve(name)));}
            if(!performed || score(level,"#run")>=37&&score(level,"#run")<=38&&!remote)score(level,"#inconclusive",score(level,"#inconclusive")+1);
            row.addProperty("inconclusiveFrames",score(level,"#inconclusive"));row.addProperty("frames",480);row.addProperty("complete",score(level,"#inconclusive")==0);
            row.addProperty("oneProjectilePerformed",performed&&score(level,"#run")<=38);row.addProperty("impactAirQueries",impacts);
            if(score(level,"#run")==38||(score(level,"#run")==42||score(level,"#run")==43)) {
                var frames=Files.readAllLines(d.resolve("trace.jsonl")).stream().map(line->com.google.gson.JsonParser.parseString(line).getAsJsonObject()).toList();
                long fresh=0, retained=0, anonymous=0;boolean priority=true;boolean exercised=false;
                long impactTick=performed&&(score(level,"#run")==42||score(level,"#run")==43)?com.google.gson.JsonParser.parseString(Files.readAllLines(d.resolve("doors.jsonl")).getFirst()).getAsJsonObject().get("gameTime").getAsLong():performed?com.google.gson.JsonParser.parseString(Files.readAllLines(d.resolve("impacts.jsonl")).getFirst()).getAsJsonObject().get("gameTime").getAsLong():Long.MAX_VALUE;
                for(var f:frames) {
                    long tick=f.get("gameTime").getAsLong();boolean hasLock=false,hasDecoy=false;
                    for(var value:f.getAsJsonArray("tracks")) {
                        var track=JSON.fromJson(value,com.frozendawn.entity.blind.BlindSignalMemory.Signal.class);
                        hasLock|=track.lock(tick);hasDecoy|=track.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.HEARING;
                    }
                    if(hasLock)fresh++;
                    if(tick>=impactTick&&hasLock&&hasDecoy) {
                        exercised=true;priority&=f.get("selectedLock").getAsBoolean();if(f.get("selectedLock").getAsBoolean())retained++;
                    }
                    if(tick>=impactTick&&!hasLock&&f.has("selected")&&!f.get("selected").isJsonNull()) {
                        var selected=JSON.fromJson(f.get("selected"),com.frozendawn.entity.blind.BlindSignalMemory.Signal.class);
                        if(selected.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.HEARING)anonymous++;
                    }
                }
                row.addProperty("freshEchoFrames",fresh);row.addProperty("freshContactDecoyExercised",exercised);
                row.addProperty("freshContactPriorityHeld",exercised&&priority);row.addProperty("retainedLockWithDecoyFrames",retained);
                row.addProperty("anonymousSelectionAfterLockFrames",anonymous);
            }
            Files.writeString(d.resolve("decisions.json"),JSON.toJson(entries)+"\n",StandardOpenOption.CREATE_NEW);
            for(String name:java.util.List.of("decisions.json","trace.jsonl","projectiles.jsonl","impacts.jsonl"))row.addProperty(name.replace('.','_')+"Sha256",hash(d.resolve(name)));
            Files.writeString(d.resolve("summary.json"),row+"\n",StandardOpenOption.CREATE_NEW);score(level,"#archived",row.get("complete").getAsBoolean()?1:0);
            if(a!=null)a.decisionJournal().finish(now,"CAPTURED","Decoy view ended");
        }catch(Exception failure){score(level,"#inconclusive",score(level,"#inconclusive")+1);score(level,"#archived",0);}
        score(level,"#stage",3);if(score(level,"#run")==50)LabBlindMelee.restore(p);if(a!=null){a.getNavigation().stop();a.setNoAi(true);}p.setGameMode(GameType.CREATIVE);return 1;
    }
    private static String hash(Path p)throws Exception {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
}
