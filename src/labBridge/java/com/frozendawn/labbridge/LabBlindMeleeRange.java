package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.google.gson.*;
import java.nio.file.*;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.ScoreHolder;

/** Genuine visible echo outside ordinary strike range; no supplied signal or damage immunity. */
@net.neoforged.fml.common.EventBusSubscriber(modid="frozendawn")
public final class LabBlindMeleeRange {
    private LabBlindMeleeRange() {}
    private static int score(ServerLevel l,String n) {var o=l.getScoreboard().getObjective("blind_vib");if(o==null)return -1;var v=l.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(n),o);return v==null?-1:v.value();}
    public static boolean geometry(ServerLevel l) {
        if(l.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)||l.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION))return false;
        boolean closed=score(l,"#decoy_cover")>0;
        for(int x=3;x<=20;x++)for(int y=-45;y<=-40;y++)for(int z=3;z<=7;z++) {
            var wanted=Blocks.WHITE_WOOL;
            if(z==5&&y>=-44&&y<=-41&&(x==14||x==17))wanted=Blocks.AIR;
            if(z==5&&x>=15&&x<=16&&y==-43)wanted=x==16&&closed?Blocks.GLASS:Blocks.AIR;
            if(z==5&&y==-45&&(x==14||x==17))wanted=Blocks.STONE;
            if(x==14&&y==-40&&z==5)wanted=Blocks.SEA_LANTERN;
            if(!l.getBlockState(new BlockPos(x,y,z)).is(wanted))return false;
        }
        return l.getBlockState(new BlockPos(16,-46,5)).is(Blocks.GLASS);
    }
    static boolean control(CommandSourceStack s,Path d,int timer) {
        var l=s.getLevel();
        if(timer==160&&score(l,"#decoy_cover")==0) {
            try {
                l.setBlock(new BlockPos(16,-43,5),Blocks.GLASS.defaultBlockState(),3);
                l.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly("#decoy_cover"),l.getScoreboard().getObjective("blind_vib")).set(timer);
                var o=new JsonObject();o.addProperty("timer",timer);o.addProperty("gameTime",l.getGameTime());o.addProperty("reason","RANGE_WINDOW_COMPLETE");
                Files.writeString(d.resolve("range-shutter.jsonl"),o+"\n",StandardOpenOption.APPEND);
            }catch(Exception failed){return false;}
        }
        return geometry(l);
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void damage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e) {
        if(!(e.getEntity() instanceof ServerPlayer p)||!(p.level() instanceof ServerLevel l)||(score(l,"#run")!=51&&score(l,"#run")!=52)||score(l,"#stage")!=2||!p.getTags().contains("blind_lab_player")||!LabBlindIsolation.enabled(l))return;
        try {
            var id=p.getPersistentData().getUUID("BlindLabDecoyActor");var d=l.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-results/run-"+score(l,"#run")+"-"+id);
            var o=new JsonObject();o.addProperty("timer",score(l,"#timer"));o.addProperty("gameTime",l.getGameTime());o.addProperty("healthDamage",e.getNewDamage());o.addProperty("healthAfter",p.getHealth());o.addProperty("damageType",e.getSource().getMsgId());
            Files.writeString(d.resolve("range-damage.jsonl"),o+"\n",StandardOpenOption.APPEND);
        }catch(Exception failed){l.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly("#source_audit_failed"),l.getScoreboard().getObjective("blind_vib")).set(1);}
    }
    public static boolean performed(Path d,JsonArray decisions) {
        try {
            if(!Files.readAllLines(d.resolve("range-damage.jsonl")).isEmpty())return false;
            var shutter=Files.readAllLines(d.resolve("range-shutter.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
            var frames=Files.readAllLines(d.resolve("trace.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
            if(frames.size()!=480||shutter.size()!=1||shutter.getFirst().get("timer").getAsInt()!=160||!shutter.getFirst().get("reason").getAsString().equals("RANGE_WINDOW_COMPLETE"))return false;
            int genuineStrikeOpportunities=0;
            for(var f:frames) {
                int timer=f.get("timer").getAsInt();long now=f.get("gameTime").getAsLong();var m=f.getAsJsonObject("meleeRange");var mining=m.getAsJsonObject("mining");
                if(!f.get("geometryIntact").getAsBoolean()||f.get("actorHealth").getAsFloat()!=40||f.get("playerHealth").getAsFloat()!=20||m.get("distance").getAsDouble()<=2.8
                        ||m.get("successfulBreaks").getAsLong()!=0||mining.has("block")&&!mining.get("block").isJsonNull()||mining.get("progressTicks").getAsInt()!=0
                        ||m.get("shutterClosed").getAsBoolean()!=(timer>=160)||m.get("lineOfSight").getAsBoolean()!=(timer<160))return false;
                if(timer<160&&f.get("selectedLock").getAsBoolean()&&!f.get("selected").isJsonNull()) {
                    var signal=f.getAsJsonObject("selected");long age=now-signal.get("observed").getAsLong();
                    if(signal.get("sense").getAsString().equals("ECHO")&&signal.get("subject").getAsString().equals(f.get("player").getAsString())&&signal.get("travel").getAsDouble()<=8&&age>=0&&age<=25)genuineStrikeOpportunities++;
                }
            }
            boolean echo=false,lock=false;
            for(var v:decisions){var e=v.getAsJsonObject();String event=e.get("event").getAsString();echo|=event.equals("BLIND_ECHO_RETURN");lock|=event.equals("BLIND_LOCK");if(Set.of("MELEE_HIT","BLIND_NOISE_PAIN","BLIND_DIG","BLIND_BUDGET").contains(event))return false;}
            return genuineStrikeOpportunities>0&&echo&&lock&&frames.getLast().getAsJsonArray("tracks").isEmpty()&&frames.getLast().get("selected").isJsonNull();
        }catch(Exception invalid){return false;}
    }
    /** Exact archived human51 replacement permission; its whole input remains unqualified. */
    public static boolean reviewed51(Path root) {
        try {for(var e:REVIEWED51.entrySet())if(!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(e.getKey())))).equals(e.getValue()))return false;return true;}catch(Exception failed){return false;}
    }
    private static final java.util.Map<String,String> REVIEWED51=java.util.Map.ofEntries(
        java.util.Map.entry("blind-lab-ground/check-96e1b731-8f26-4a94-a9fd-e1f564997131/client.jsonl","adc313e4967dedc7635aed9825245cf4165c94e9017dccd8669a5b0d12c8f292"),
        java.util.Map.entry("blind-lab-ground/check-96e1b731-8f26-4a94-a9fd-e1f564997131/keys.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        java.util.Map.entry("blind-lab-ground/check-96e1b731-8f26-4a94-a9fd-e1f564997131/server.jsonl","ca55334a7129063b7d83d808f87b2720ee7fa06b4a7b241c6ab80e49832d3dec"),
        java.util.Map.entry("blind-lab-ground/check-96e1b731-8f26-4a94-a9fd-e1f564997131/start.json","dd79e0bb16cad9705774940379f5f66631a43e5972a8aac03e6a7576199d1ed8"),
        java.util.Map.entry("blind-lab-ground/check-96e1b731-8f26-4a94-a9fd-e1f564997131/summary.json","7b544fdcbf34e7deac0ffd6d4e6087961a8a2b0a0ffb7e4eca54de227d4f68f5"),
        java.util.Map.entry("blind-lab-input/probe-0a691194-fe08-4a1c-9221-85e6c9771dcc/client.jsonl","ceb31fe68648f60f6c615a0e33149b6d601875a778290a5873f07e6dd55e8cde"),
        java.util.Map.entry("blind-lab-input/probe-0a691194-fe08-4a1c-9221-85e6c9771dcc/keys.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        java.util.Map.entry("blind-lab-input/probe-0a691194-fe08-4a1c-9221-85e6c9771dcc/server.jsonl","f9549b94e2304ac4901a88c4beba20f164c19f9424cca95298731e7b915f77ad"),
        java.util.Map.entry("blind-lab-input/probe-0a691194-fe08-4a1c-9221-85e6c9771dcc/start.json","a90338d73543f11e5f1264db3b096b61c7615a2691c82671276b6cfb835140a6"),
        java.util.Map.entry("blind-lab-input/probe-0a691194-fe08-4a1c-9221-85e6c9771dcc/summary.json","3e4bcd04a23ff59fe159d911af1fef5564405b72f8289a0e7be5f055e5435d4d"),
        java.util.Map.entry("blind-lab-results/run-51-a5ab4e99-0c34-4bf7-ba30-a6be5ae19ed6/decisions.json","b72d232c47e43b4717c65aa43e980dcaa2595e99d846b79021d75f519e1e8f38"),
        java.util.Map.entry("blind-lab-results/run-51-a5ab4e99-0c34-4bf7-ba30-a6be5ae19ed6/impacts.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        java.util.Map.entry("blind-lab-results/run-51-a5ab4e99-0c34-4bf7-ba30-a6be5ae19ed6/projectiles.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        java.util.Map.entry("blind-lab-results/run-51-a5ab4e99-0c34-4bf7-ba30-a6be5ae19ed6/range-damage.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        java.util.Map.entry("blind-lab-results/run-51-a5ab4e99-0c34-4bf7-ba30-a6be5ae19ed6/range-shutter.jsonl","02ac9fd1dad877a6e8ffdc4e006869b23aeaf8e123d888eaa8d651271df0b07d"),
        java.util.Map.entry("blind-lab-results/run-51-a5ab4e99-0c34-4bf7-ba30-a6be5ae19ed6/start.json","976783083f3f9fccdbc7d7a0970b51c541108aa2944fdba3fab5c1fbf5ad9a11"),
        java.util.Map.entry("blind-lab-results/run-51-a5ab4e99-0c34-4bf7-ba30-a6be5ae19ed6/summary.json","9cfaf37552e1a8553e6b8bdf8724c18341dc5da6cde51174c96dbb86faa0f21e"),
        java.util.Map.entry("blind-lab-results/run-51-a5ab4e99-0c34-4bf7-ba30-a6be5ae19ed6/trace.jsonl","f921ddf3a8b0ddc0e186c8db506f914026c9fa81e59f8e55d9c5126b57cf40e0")
    );
}
