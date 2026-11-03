package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;

/** Real small explosions apply ordinary damage, knockback, particles and game events. */
@net.neoforged.fml.common.EventBusSubscriber(modid="frozendawn")
public final class LabBlindExplosion {
    private static final Gson JSON=new Gson();
    private LabBlindExplosion() {}
    private static int score(ServerLevel level,String holder) {
        var objective=level.getScoreboard().getObjective("blind_vib");
        if(objective==null)return -1;var value=level.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(holder),objective);return value==null?-1:value.value();
    }
    public static boolean geometry(ServerLevel level) {
        return level.getBlockState(LabBlindBell.BELL).isAir()&&level.getBlockState(LabBlindDoor.DOOR).isAir()&&level.getBlockState(LabBlindDoor.DOOR.above()).isAir();
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void damage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if(!(event.getEntity() instanceof ArchitectEntity actor)||!(actor.level() instanceof ServerLevel level)
                ||score(level,"#run")!=45||score(level,"#stage")!=2||!actor.getTags().contains("blind_lab_actor")||!LabBlindIsolation.enabled(level))return;
        var row=new JsonObject();row.addProperty("actor",actor.getUUID().toString());row.addProperty("timer",score(level,"#timer"));row.addProperty("gameTime",level.getGameTime());
        row.addProperty("explosion",event.getSource().is(DamageTypeTags.IS_EXPLOSION));row.addProperty("magic",event.getSource().is(DamageTypes.MAGIC));
        row.addProperty("originalDamage",event.getOriginalDamage());row.addProperty("healthDamage",event.getNewDamage());row.addProperty("healthAfter",actor.getHealth());
        var directory=level.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-results/run-45-"+actor.getUUID());
        try {Files.writeString(directory.resolve("blast-damage.jsonl"),row+"\n",StandardOpenOption.APPEND);}
        catch(Exception failure){level.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly("#source_audit_failed"),level.getScoreboard().getObjective("blind_vib")).set(1);}
    }
    public static boolean control(CommandSourceStack source,Path directory,int timer,ArchitectEntity actor) {
        if(!geometry(source.getLevel())||actor==null)return false;
        if(timer!=80&&timer!=340)return true;
        var level=source.getLevel();Vec3 origin=timer==80?actor.position().add(.75,1,0):new Vec3(14.5,-41.5,5.5);
        var row=new JsonObject();row.addProperty("timer",timer);row.addProperty("gameTime",level.getGameTime());row.add("origin",JSON.toJsonTree(origin));
        row.add("actorPositionBefore",JSON.toJsonTree(actor.position()));row.addProperty("healthBefore",actor.getHealth());row.addProperty("power",1);row.addProperty("blocksDestroyed",false);
        level.explode(null,origin.x,origin.y,origin.z,1.0F,Level.ExplosionInteraction.NONE);
        row.addProperty("healthAfterPhysicalBlast",actor.getHealth());
        try {Files.writeString(directory.resolve("blasts.jsonl"),row+"\n",StandardOpenOption.APPEND);return true;}catch(Exception failure){return false;}
    }
    public static boolean performed(Path directory,JsonArray decisions) {
        try {
            var blasts=Files.readAllLines(directory.resolve("blasts.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
            var damage=Files.readAllLines(directory.resolve("blast-damage.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
            if(blasts.size()!=2||blasts.getFirst().get("timer").getAsInt()!=80||blasts.getLast().get("timer").getAsInt()!=340||damage.size()!=2)return false;
            var physical=damage.getFirst();var acoustic=damage.getLast();
            if(!physical.get("explosion").getAsBoolean()||physical.get("magic").getAsBoolean()||physical.get("timer").getAsInt()!=80||physical.get("healthDamage").getAsFloat()<=0
                    ||acoustic.get("explosion").getAsBoolean()||!acoustic.get("magic").getAsBoolean()||acoustic.get("timer").getAsInt()<340||acoustic.get("timer").getAsInt()>342||acoustic.get("healthDamage").getAsFloat()!=2)return false;
            float after=physical.get("healthAfter").getAsFloat();
            if(after!=40-physical.get("healthDamage").getAsFloat()||blasts.getFirst().get("healthAfterPhysicalBlast").getAsFloat()!=after||blasts.getLast().get("healthBefore").getAsFloat()!=after||blasts.getLast().get("healthAfterPhysicalBlast").getAsFloat()!=after)return false;
            var frames=Files.readAllLines(directory.resolve("trace.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
            var pain=java.util.stream.StreamSupport.stream(decisions.spliterator(),false).map(x->x.getAsJsonObject()).filter(x->x.get("event").getAsString().equals("BLIND_NOISE_PAIN")).toList();
            return frames.size()==480&&pain.size()==2&&pain.stream().allMatch(x->x.get("detail").getAsString().contains("explosion=true"))
                    &&frames.getLast().get("actorHealth").getAsFloat()==after-2
                    &&frames.stream().filter(x->x.get("timer").getAsInt()>=80&&x.get("timer").getAsInt()<340).allMatch(x->x.get("actorHealth").getAsFloat()==after)
                    &&frames.stream().anyMatch(x->x.get("timer").getAsInt()>205&&x.get("timer").getAsInt()<340&&x.get("diagnostics").getAsString().startsWith("blind=WANDER"));
        }catch(Exception malformed){return false;}
    }
}
