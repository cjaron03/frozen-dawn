package com.frozendawn.labbridge;

import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.block.ThermalHeaterBlock;
import com.frozendawn.init.ModBlocks;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Supplies and removes real heater fuel; ordinary block ticks alone emit machine sensing. */
@net.neoforged.fml.common.EventBusSubscriber(modid="frozendawn")
public final class LabBlindHeat {
    public static final BlockPos MACHINE = new BlockPos(14,-44,5);
    private LabBlindHeat() {}
    public static boolean geometry(ServerLevel level) {
        if(!level.getBlockState(MACHINE.below()).is(net.minecraft.world.level.block.Blocks.STONE))return false;
        return level.getBlockState(MACHINE).is(ModBlocks.THERMAL_HEATER.get())
                && level.getBlockEntity(MACHINE) instanceof ThermalHeaterBlockEntity
                && level.getBlockState(LabBlindDoor.DOOR).isAir()
                && level.getBlockState(LabBlindDoor.DOOR.above()).isAir();
    }
    public static JsonObject state(ServerLevel level) {
        var row=new JsonObject();row.addProperty("position",MACHINE.toShortString());row.addProperty("valid",geometry(level));
        if(geometry(level)) {
            var heater=(ThermalHeaterBlockEntity)level.getBlockEntity(MACHINE);
            row.addProperty("lit",heater.isLit());row.addProperty("blockLit",level.getBlockState(MACHINE).getValue(ThermalHeaterBlock.LIT));
            row.addProperty("burnTime",heater.saveWithoutMetadata(level.registryAccess()).getInt("BurnTime"));
        }
        return row;
    }
    static boolean control(CommandSourceStack source,Path directory,int timer) {
        var level=source.getLevel();if(!geometry(level))return false;
        var heater=(ThermalHeaterBlockEntity)level.getBlockEntity(MACHINE);
        try {
            if(timer==80||timer==240) {
                if(timer==80)heater.addFuel(10000);else heater.extinguish();
                var row=state(level);row.addProperty("timer",timer);row.addProperty("gameTime",level.getGameTime());
                row.addProperty("action",timer==80?"SUPPLY_FUEL":"EXTINGUISH");
                Files.writeString(directory.resolve("heaters.jsonl"),row+"\n",StandardOpenOption.APPEND);
            }
            boolean expected=timer>=80&&timer<240;
            return heater.isLit()==expected && level.getBlockState(MACHINE).getValue(ThermalHeaterBlock.LIT)==expected;
        }catch(Exception failure){return false;}
    }
    public static boolean performed(Path directory,JsonArray decisions) {
        try {
        var changes=Files.readAllLines(directory.resolve("heaters.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
        var frames=Files.readAllLines(directory.resolve("trace.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
        var damage=Files.readAllLines(directory.resolve("heat-damage.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
        if(changes.size()!=2||frames.size()!=480||damage.isEmpty()||changes.getFirst().get("timer").getAsInt()!=80||changes.getLast().get("timer").getAsInt()!=240)return false;
        boolean exposed=false,escaped=false,burned=false;float finalHealth=frames.getLast().get("actorHealth").getAsFloat();
        for(var f:frames) {
            int at=f.get("timer").getAsInt();var heater=f.getAsJsonObject("heater");
            if(!heater.get("valid").getAsBoolean()||heater.get("lit").getAsBoolean()!=(at>=80&&at<240)||f.get("noiseStunPose").getAsBoolean()||!f.get("actorAlive").getAsBoolean())return false;
            double distance=f.get("heaterDistance").getAsDouble();
            exposed|=at>=80&&at<240&&distance<4;escaped|=at>=80&&at<240&&distance>=4;
            burned|=at>=80&&at<240&&heater.get("burnTime").getAsInt()<10000;
            if(at>=320&&f.get("actorHealth").getAsFloat()!=finalHealth)return false;
        }
        boolean primary=false;
        for(var d:damage){if(!d.get("fire").getAsBoolean()||d.get("healthDamage").getAsFloat()<=0)return false;primary|=d.get("onFire").getAsBoolean()&&d.get("originalDamage").getAsFloat()==4.5F;}
        return exposed&&escaped&&burned&&primary&&finalHealth>0&&finalHealth<40
                &&java.util.stream.StreamSupport.stream(decisions.spliterator(),false).map(x->x.getAsJsonObject()).noneMatch(x->x.get("event").getAsString().equals("BLIND_NOISE_PAIN"));
        }catch(Exception malformed){return false;}
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void damage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if(!(event.getEntity() instanceof com.frozendawn.entity.ArchitectEntity actor)||!(actor.level() instanceof ServerLevel level)||!actor.getTags().contains("blind_lab_actor")||!LabBlindIsolation.enabled(level))return;
        var objective=level.getScoreboard().getObjective("blind_vib");if(objective==null)return;
        java.util.function.Function<String,Integer> score=n->{var v=level.getScoreboard().getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly(n),objective);return v==null?-1:v.value();};
        if(score.apply("#run")!=46||score.apply("#stage")!=2)return;
        var row=new JsonObject();row.addProperty("actor",actor.getUUID().toString());row.addProperty("timer",score.apply("#timer"));row.addProperty("gameTime",level.getGameTime());
        row.addProperty("fire",event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE));row.addProperty("onFire",event.getSource().is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE));row.addProperty("originalDamage",event.getOriginalDamage());row.addProperty("healthDamage",event.getNewDamage());row.addProperty("healthAfter",actor.getHealth());
        try {Files.writeString(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-results/run-46-"+actor.getUUID()+"/heat-damage.jsonl"),row+"\n",StandardOpenOption.APPEND);}
        catch(Exception failure){level.getScoreboard().getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#source_audit_failed"),objective).set(1);}
    }
    /** Exact reviewed human45 clock-only exception; never qualifies or rewrites that old input. */
    public static boolean reviewed45(Path root) {
        try {for(var e:REVIEWED.entrySet())if(!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(e.getKey())))).equals(e.getValue()))return false;return true;}catch(Exception failed){return false;}
    }
    private static final java.util.Map<String,String> REVIEWED=java.util.Map.ofEntries(
        java.util.Map.entry("blind-lab-results/run-45-33da5d6d-52da-4ed8-9e73-40887cf7fe18/summary.json","38839aa2a96dc1b8b96108f6977ed28c9c7ed6bd04fe46e67f3eb2e43aadd147"),
        java.util.Map.entry("blind-lab-results/run-45-33da5d6d-52da-4ed8-9e73-40887cf7fe18/blast-damage.jsonl","bb33d82e501aa271efbc0c5670d531fd672382528f824a57db009db7c3a5f62b"),
        java.util.Map.entry("blind-lab-results/run-45-33da5d6d-52da-4ed8-9e73-40887cf7fe18/projectiles.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        java.util.Map.entry("blind-lab-results/run-45-33da5d6d-52da-4ed8-9e73-40887cf7fe18/decisions.json","4d33ceeb42edf80e35fd4d29f020167796e3c9ceefc1b541f89e7522097ba426"),
        java.util.Map.entry("blind-lab-results/run-45-33da5d6d-52da-4ed8-9e73-40887cf7fe18/impacts.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        java.util.Map.entry("blind-lab-results/run-45-33da5d6d-52da-4ed8-9e73-40887cf7fe18/blasts.jsonl","a27a279a27e52bfdd32a60eca638f1b6a2de8764a18d0aa70c8e9522d6b7a1f0"),
        java.util.Map.entry("blind-lab-results/run-45-33da5d6d-52da-4ed8-9e73-40887cf7fe18/start.json","1d67a0799c7a029020ef563c4d7caaa8e85ce074fb6096f515ca9ee18b45e9e3"),
        java.util.Map.entry("blind-lab-results/run-45-33da5d6d-52da-4ed8-9e73-40887cf7fe18/trace.jsonl","00bf4b89a0c959b101867d97e00dd6d985b064668532bc9e73a7e84590b83cb1"),
        java.util.Map.entry("blind-lab-input/probe-b343607e-b6c8-4461-a049-a30eb676275f/summary.json","797b9d5b5e0101dfa4d11260f5fa6a6b91ab8aeaaf454f01bad43c093a2f008b"),
        java.util.Map.entry("blind-lab-input/probe-b343607e-b6c8-4461-a049-a30eb676275f/keys.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        java.util.Map.entry("blind-lab-input/probe-b343607e-b6c8-4461-a049-a30eb676275f/server.jsonl","8a5889851fbd6dc1712d7e54c350ddf58a14636245de632971ef3c726d5e4158"),
        java.util.Map.entry("blind-lab-input/probe-b343607e-b6c8-4461-a049-a30eb676275f/start.json","eb2fed0d9ca367f580c7387150ae57dc30ad76715a8f8fd2048c7432f6fbe97d"),
        java.util.Map.entry("blind-lab-input/probe-b343607e-b6c8-4461-a049-a30eb676275f/client.jsonl","8bd9042c5f85c2054795bfc232e16c9571e32767b9ec0452aa098e8187d14de0"),
        java.util.Map.entry("blind-lab-ground/check-e1fd2e09-e12a-4efd-8769-8948f02f3bfe/summary.json","b130bc1c40dd0accb1d326e3abf88484f811ce88a1d33d519080e29338514c45"),
        java.util.Map.entry("blind-lab-ground/check-e1fd2e09-e12a-4efd-8769-8948f02f3bfe/keys.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        java.util.Map.entry("blind-lab-ground/check-e1fd2e09-e12a-4efd-8769-8948f02f3bfe/server.jsonl","f644461a58ace90e986533d1e2086d50e39ac43fce2f155940bf0675e9091de4"),
        java.util.Map.entry("blind-lab-ground/check-e1fd2e09-e12a-4efd-8769-8948f02f3bfe/start.json","92f363ef2c7c26f4ab815fc2c57b125d0364c6199229f7c9ee62a88fcafb5f43"),
        java.util.Map.entry("blind-lab-ground/check-e1fd2e09-e12a-4efd-8769-8948f02f3bfe/client.jsonl","d57a07300e4b7f148de2b6d92438a3d0b6250fe336410a151d97b26bb8d09aa8")
    );
}
