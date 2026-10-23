package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.ScoreHolder;

/** Passive recording of the real controller during owner movement. Never injects a sensory signal. */
public final class LabBlindBehavior {
    private static final Gson JSON = new Gson();
    private static final AABB ROOM = new AABB(0,-49,-12,31,-37,11);
    private LabBlindBehavior() { }
    private static int score(CommandSourceStack source, String holder) {
        var board=source.getServer().getScoreboard(); var objective=board.getObjective("blind_vib");
        if(objective==null)return -1;
        var value=board.getPlayerScoreInfo(ScoreHolder.forNameOnly(holder),objective);
        return value==null ? -1 : value.value();
    }
    private static void score(CommandSourceStack source,String holder,int value) {
        source.getServer().getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder),
                source.getServer().getScoreboard().getObjective("blind_vib")).set(value);
    }
    private static ArchitectEntity actor(CommandSourceStack source) {
        if(LabBlindWorkbench.access(source)!=1 || score(source,"#run")!=score(source,"#case")
                || score(source,"#case")<4 || score(source,"#case")>10
                || !(source.getEntity() instanceof ServerPlayer p) || !p.getTags().contains("blind_lab_player"))return null;
        var actors=source.getLevel().getEntitiesOfClass(ArchitectEntity.class,ROOM);
        if(actors.size()!=1)return null;
        var a=actors.getFirst();
        return a.isAlive() && a.isBlindArchitect() && a.getTags().contains("blind_lab_actor") ? a : null;
    }
    private static Path directory(CommandSourceStack source,ArchitectEntity actor) {
        return source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-results/run-"+score(source,"#run")+"-"+actor.getUUID());
    }
    private static boolean aperture(CommandSourceStack source,net.minecraft.world.level.block.Block block) {
        for(int z=4;z<=6;z++)if(!source.getLevel().getBlockState(new net.minecraft.core.BlockPos(11,-43,z)).is(block))return false;
        return true;
    }
    private static JsonObject frame(CommandSourceStack source,ArchitectEntity actor) {
        var p=(ServerPlayer)source.getEntity(); var value=new JsonObject();
        value.addProperty("run",score(source,"#run"));value.addProperty("case",score(source,"#case"));
        value.addProperty("timer",score(source,"#timer"));value.addProperty("gameTime",source.getLevel().getGameTime());
        value.addProperty("actor",actor.getUUID().toString());value.add("actorPosition",JSON.toJsonTree(actor.position()));
        value.addProperty("actorOnGround",actor.onGround());value.addProperty("actorNoAi",actor.isNoAi());
        value.addProperty("actorYaw",actor.getYRot());value.addProperty("headYaw",actor.getYHeadRot());
        value.addProperty("bodyYaw",actor.yBodyRot);
        value.addProperty("navigationDone",actor.getNavigation().isDone());
        value.addProperty("diagnostics",actor.blindController().diagnostics());
        value.add("selected",JSON.toJsonTree(actor.blindController().signal()));
        value.add("tracks",JSON.toJsonTree(actor.blindController().tracks()));
        value.add("vibration",JSON.toJsonTree(actor.blindController().lastVibration()));
        value.add("echo",JSON.toJsonTree(actor.blindController().lastEcho()));
        value.add("playerPosition",JSON.toJsonTree(p.position()));value.addProperty("playerOnGround",p.onGround());
        value.addProperty("playerSprinting",p.isSprinting());value.addProperty("playerSneaking",p.isShiftKeyDown());
        value.addProperty("playerMode",p.gameMode.getGameModeForPlayer().getName());
        if(score(source,"#case")>=7)value.add("isolation",LabBlindIsolation.snapshot(source.getLevel()));
        if(score(source,"#case")>=9) {
            var closure=new JsonObject();closure.addProperty("closed",score(source,"#echo_closed"));
            closure.addProperty("closedAt",score(source,"#closed_at"));
            closure.addProperty("allAir",aperture(source,net.minecraft.world.level.block.Blocks.AIR));
            closure.addProperty("allGlass",aperture(source,net.minecraft.world.level.block.Blocks.GLASS));
            value.add("echoClosure",closure);
        }
        return value;
    }
    public static int start(CommandSourceStack source) {
        var actor=actor(source);var player=source.getEntity() instanceof ServerPlayer p ? p : null;
        if(actor==null || player==null || !player.isCreative() || score(source,"#stage")!=1
                || !actor.isNoAi() || !actor.onGround() || actor.position().distanceToSqr(new net.minecraft.world.phys.Vec3(9.5,-44,5.5))>.001
                || player.position().distanceToSqr(new net.minecraft.world.phys.Vec3(score(source,"#case")>=5?17.5:15.5,-44,5.5))>.01)return 0;
        if(score(source,"#case")>=7&&(!LabBlindIsolation.enabled(source.getLevel())
                ||!LabBlindIsolation.unrelated(source.getLevel()).isEmpty()))return 0;
        if(score(source,"#case")>=9&&(score(source,"#echo_closed")!=0||score(source,"#closed_at")!=-1
                ||!aperture(source,net.minecraft.world.level.block.Blocks.AIR)))return 0;
        try {
            var directory=directory(source,actor);Files.createDirectories(directory);
            var initial=frame(source,actor);initial.addProperty("world",source.getServer().getWorldData().getLevelName());
            Files.writeString(directory.resolve("start.json"),JSON.toJson(initial)+"\n",StandardOpenOption.CREATE_NEW);
            Files.writeString(directory.resolve("trace.jsonl"),"",StandardOpenOption.CREATE_NEW);
        } catch(java.io.IOException error) { source.sendFailure(Component.literal("Evidence setup failed. Preserve this scene; do not retry."));return 0; }
        score(source,"#stage",2);score(source,"#timer",0);score(source,"#last_frame",0);
        score(source,"#frames",0);score(source,"#archived",0);score(source,"#inconclusive",0);
        player.setGameMode(GameType.SURVIVAL);actor.setNoAi(false);
        return 1;
    }
    public static int record(CommandSourceStack source) {
        var actor=actor(source);int timer=score(source,"#timer");
        if(actor==null || score(source,"#stage")!=2 || timer<1 || timer>480 || timer<=score(source,"#last_frame"))return 0;
        score(source,"#last_frame",timer);
        if(score(source,"#case")>=7&&(!LabBlindIsolation.enabled(source.getLevel())
                ||!LabBlindIsolation.unrelated(source.getLevel()).isEmpty()))score(source,"#inconclusive",score(source,"#inconclusive")+1);
        if(score(source,"#case")>=8) {
            var p=(ServerPlayer)source.getEntity();
            boolean otherSense=actor.blindController().tracks().stream().anyMatch(t->t.sense()!=com.frozendawn.entity.blind.BlindSignalMemory.Sense.ECHO);
            if(otherSense||p.position().distanceToSqr(new net.minecraft.world.phys.Vec3(17.5,-44,5.5))>.04)
                score(source,"#inconclusive",score(source,"#inconclusive")+1);
        }
        if(score(source,"#case")>=9) {
            boolean valid=timer<120
                    ?score(source,"#echo_closed")==0&&score(source,"#closed_at")==-1&&aperture(source,net.minecraft.world.level.block.Blocks.AIR)
                    :score(source,"#echo_closed")==1&&score(source,"#closed_at")==120&&aperture(source,net.minecraft.world.level.block.Blocks.GLASS);
            if(!valid)score(source,"#inconclusive",score(source,"#inconclusive")+1);
        }
        try {
            // APPEND alone cannot silently recreate a lost trace. Timers never repeat.
            Files.writeString(directory(source,actor).resolve("trace.jsonl"),JSON.toJson(frame(source,actor))+"\n",StandardOpenOption.APPEND);
            score(source,"#frames",score(source,"#frames")+1);return 1;
        } catch(java.io.IOException error) {
            score(source,"#inconclusive",score(source,"#inconclusive")+1);
            source.sendFailure(Component.literal("Trace write failed; this view is incomplete."));return 0;
        }
    }
    public static int finish(CommandSourceStack source) {
        var actor=actor(source);
        if(actor==null || score(source,"#stage")!=2 || score(source,"#timer")!=480)return 0;
        var summary=frame(source,actor);summary.addProperty("frames",score(source,"#frames"));
        summary.addProperty("complete",score(source,"#frames")==480 && score(source,"#inconclusive")==0);
        summary.addProperty("receiverNbt",actor.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString());
        // Hold the encounter even if evidence I/O fails. No implicit replay or scene advance.
        score(source,"#stage",3);actor.getNavigation().stop();actor.setNoAi(true);
        ((ServerPlayer)source.getEntity()).setGameMode(GameType.CREATIVE);
        try {
            var directory=directory(source,actor);
            summary.addProperty("traceSha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(directory.resolve("trace.jsonl")))));
            Files.writeString(directory.resolve("summary.json"),JSON.toJson(summary)+"\n",StandardOpenOption.CREATE_NEW);
            score(source,"#archived",summary.get("complete").getAsBoolean()?1:0);return 1;
        } catch(Exception error) { source.sendFailure(Component.literal("Archive failed. Preserve this scene."));return 0; }
    }
    public static int archived(CommandSourceStack source) {
        var actor=actor(source);
        if(actor==null || !actor.isNoAi() || !((ServerPlayer)source.getEntity()).isCreative()
                || score(source,"#stage")!=3 || score(source,"#timer")!=480 || score(source,"#frames")!=480
                || score(source,"#archived")!=1 || score(source,"#inconclusive")!=0)return 0;
        try {
            var directory=directory(source,actor);
            var summary=com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("summary.json"))).getAsJsonObject();
            var start=com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("start.json"))).getAsJsonObject();
            String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(directory.resolve("trace.jsonl"))));
            return summary.get("complete").getAsBoolean() && summary.get("frames").getAsInt()==480
                    && summary.get("timer").getAsInt()==480 && summary.get("actor").getAsString().equals(actor.getUUID().toString())
                    && start.get("world").getAsString().equals(source.getServer().getWorldData().getLevelName())
                    && summary.get("traceSha256").getAsString().equals(hash) ? 1 : 0;
        } catch(Exception error) { return 0; }
    }
}
