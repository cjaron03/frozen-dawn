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
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.ScoreHolder;

/** Real lower-body echo within reach while a top slab blocks the ordinary eye ray. */
@net.neoforged.fml.common.EventBusSubscriber(modid="frozendawn")
public final class LabBlindMeleeObstruction {
    private LabBlindMeleeObstruction() {}
    private static int score(ServerLevel l,String n) {var o=l.getScoreboard().getObjective("blind_vib");if(o==null)return -1;var v=l.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(n),o);return v==null?-1:v.value();}
    public static boolean geometry(ServerLevel l) {
        return geometry(l,score(l,"#decoy_cover")>0);
    }
    public static boolean geometry(ServerLevel l,boolean closed) {
        if(l.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)||l.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION))return false;
        for(int x=3;x<=20;x++)for(int y=-45;y<=-40;y++)for(int z=3;z<=7;z++) {
            var wanted=Blocks.WHITE_WOOL.defaultBlockState();
            if(z==5&&y>=-44&&y<=-41&&(x==15||x==17))wanted=Blocks.AIR.defaultBlockState();
            if(z==5&&x==16&&y==-44)wanted=(closed?Blocks.GLASS:Blocks.AIR).defaultBlockState();
            if(z==5&&x==16&&y==-43)wanted=closed?Blocks.GLASS.defaultBlockState():Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP);
            if(z==5&&y==-45&&(x==15||x==17))wanted=Blocks.STONE.defaultBlockState();
            if(x==15&&y==-40&&z==5)wanted=Blocks.SEA_LANTERN.defaultBlockState();
            if(!l.getBlockState(new BlockPos(x,y,z)).equals(wanted))return false;
        }
        return l.getBlockState(new BlockPos(16,-46,5)).is(Blocks.GLASS);
    }
    static boolean control(CommandSourceStack s,Path d,int timer) {
        var l=s.getLevel();
        if(timer==160&&score(l,"#decoy_cover")==0) {
            try {
                l.setBlock(new BlockPos(16,-44,5),Blocks.GLASS.defaultBlockState(),3);
                l.setBlock(new BlockPos(16,-43,5),Blocks.GLASS.defaultBlockState(),3);
                l.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly("#decoy_cover"),l.getScoreboard().getObjective("blind_vib")).set(timer);
                var o=new JsonObject();o.addProperty("timer",timer);o.addProperty("gameTime",l.getGameTime());o.addProperty("reason","BODY_APERTURE_SEALED");
                Files.writeString(d.resolve("obstruction-shutter.jsonl"),o+"\n",StandardOpenOption.APPEND);
            }catch(Exception failed){return false;}
        }
        return geometry(l);
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void damage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e) {
        if(!(e.getEntity() instanceof ServerPlayer p)||!(p.level() instanceof ServerLevel l)||score(l,"#run")!=53||score(l,"#stage")!=2||!p.getTags().contains("blind_lab_player")||!LabBlindIsolation.enabled(l))return;
        try {
            var id=p.getPersistentData().getUUID("BlindLabDecoyActor");var d=l.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-results/run-53-"+id);
            var o=new JsonObject();o.addProperty("timer",score(l,"#timer"));o.addProperty("gameTime",l.getGameTime());o.addProperty("healthDamage",e.getNewDamage());o.addProperty("healthAfter",p.getHealth());o.addProperty("damageType",e.getSource().getMsgId());
            Files.writeString(d.resolve("obstruction-damage.jsonl"),o+"\n",StandardOpenOption.APPEND);
        }catch(Exception failed){l.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly("#source_audit_failed"),l.getScoreboard().getObjective("blind_vib")).set(1);}
    }
    public static boolean performed(Path d,JsonArray decisions) {
        try {
            if(!Files.readAllLines(d.resolve("obstruction-damage.jsonl")).isEmpty())return false;
            var shutter=Files.readAllLines(d.resolve("obstruction-shutter.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
            var frames=Files.readAllLines(d.resolve("trace.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
            if(frames.size()!=480||shutter.size()!=1||shutter.getFirst().get("timer").getAsInt()!=160||!shutter.getFirst().get("reason").getAsString().equals("BODY_APERTURE_SEALED"))return false;
            int blockedFreshOpportunities=0;
            for(var f:frames) {
                int timer=f.get("timer").getAsInt();long now=f.get("gameTime").getAsLong();var m=f.getAsJsonObject("meleeObstruction");var mining=m.getAsJsonObject("mining");
                if(!f.get("geometryIntact").getAsBoolean()||f.get("actorHealth").getAsFloat()!=40||f.get("playerHealth").getAsFloat()!=20||m.get("distance").getAsDouble()>2.8||m.get("lineOfSight").getAsBoolean()
                        ||m.get("successfulBreaks").getAsLong()!=0||mining.has("block")&&!mining.get("block").isJsonNull()||mining.get("progressTicks").getAsInt()!=0||m.get("shutterClosed").getAsBoolean()!=(timer>=160))return false;
                if(timer<160&&f.get("selectedLock").getAsBoolean()&&!f.get("selected").isJsonNull()) {
                    var signal=f.getAsJsonObject("selected");long age=now-signal.get("observed").getAsLong();
                    if(signal.get("sense").getAsString().equals("ECHO")&&signal.get("subject").getAsString().equals(f.get("player").getAsString())&&signal.get("travel").getAsDouble()<=8&&age>=0&&age<=25)blockedFreshOpportunities++;
                }
            }
            boolean echo=false,lock=false;
            for(var v:decisions){var e=v.getAsJsonObject();String event=e.get("event").getAsString();echo|=event.equals("BLIND_ECHO_RETURN");lock|=event.equals("BLIND_LOCK");if(Set.of("MELEE_HIT","BLIND_NOISE_PAIN","BLIND_DIG","BLIND_BUDGET").contains(event))return false;}
            return blockedFreshOpportunities>0&&echo&&lock&&frames.getLast().getAsJsonArray("tracks").isEmpty()&&frames.getLast().get("selected").isJsonNull();
        }catch(Exception invalid){return false;}
    }
}
