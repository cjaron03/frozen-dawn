package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.ScoreHolder;

/** Close real echo/melee through a viewing slit. A physical shutter ends exposure after one hit. */
@net.neoforged.fml.common.EventBusSubscriber(modid="frozendawn")
public final class LabBlindMelee {
    private LabBlindMelee() {}
    private static int score(ServerLevel l,String n) {var o=l.getScoreboard().getObjective("blind_vib");if(o==null)return -1;var v=l.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(n),o);return v==null?-1:v.value();}
    private static void score(ServerLevel l,String n,int v) {l.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(n),l.getScoreboard().getObjective("blind_vib")).set(v);}
    public static boolean geometry(ServerLevel l) {
        if(l.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)||l.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION))return false;
        boolean closed=score(l,"#decoy_cover")>0;
        for(int x=3;x<=20;x++)for(int y=-45;y<=-40;y++)for(int z=3;z<=7;z++) {
            var wanted=Blocks.WHITE_WOOL;
            if(z==5&&y>=-44&&y<=-41&&(x==15||x==17))wanted=Blocks.AIR;
            if(z==5&&x==16&&y==-43)wanted=closed?Blocks.GLASS:Blocks.AIR;
            if(z==5&&y==-45&&(x==15||x==17))wanted=Blocks.STONE;
            if(x==15&&y==-40&&z==5)wanted=Blocks.SEA_LANTERN;
            if(!l.getBlockState(new BlockPos(x,y,z)).is(wanted))return false;
        }
        return l.getBlockState(new BlockPos(16,-46,5)).is(Blocks.GLASS);
    }
    static void equip(ServerPlayer p) {
        p.getPersistentData().putDouble("BlindLabMeleeKnockbackBase",p.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getBaseValue());
        p.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
    }
    static void restore(ServerPlayer p) {
        if(p.getPersistentData().contains("BlindLabMeleeKnockbackBase")) {
            p.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(p.getPersistentData().getDouble("BlindLabMeleeKnockbackBase"));
            p.getPersistentData().remove("BlindLabMeleeKnockbackBase");
        }
    }
    public static JsonObject state(ServerLevel l,ArchitectEntity a,ServerPlayer p) {
        var o=new JsonObject();o.addProperty("distance",a.distanceTo(p));o.addProperty("lineOfSight",a.hasLineOfSight(p));o.addProperty("shutterClosed",score(l,"#decoy_cover")>0);
        o.addProperty("knockbackResistance",p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        o.addProperty("successfulBreaks",a.successfulBreakCount());
        o.add("mining",new Gson().toJsonTree(a.debugSnapshot(com.frozendawn.debug.architect.ArchitectDebugSnapshot.Lab.field(),false).mining()));return o;
    }
    static boolean control(CommandSourceStack s,Path d,int timer) {
        if(timer==160&&score(s.getLevel(),"#decoy_cover")==0)close(s.getLevel(),d,timer,"TIMEOUT_NO_HIT");
        return geometry(s.getLevel());
    }
    private static void close(ServerLevel l,Path d,int timer,String reason) {
        try {
            l.setBlock(new BlockPos(16,-43,5),Blocks.GLASS.defaultBlockState(),3);score(l,"#decoy_cover",Math.max(1,timer));
            var o=new JsonObject();o.addProperty("timer",timer);o.addProperty("gameTime",l.getGameTime());o.addProperty("reason",reason);
            Files.writeString(d.resolve("melee-shutter.jsonl"),o+"\n",StandardOpenOption.APPEND);
        }catch(Exception failed){score(l,"#source_audit_failed",1);}
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void damage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e) {
        if(!(e.getEntity() instanceof ServerPlayer p)||!(p.level() instanceof ServerLevel l)||score(l,"#run")!=50||score(l,"#stage")!=2||!p.getTags().contains("blind_lab_player")||!LabBlindIsolation.enabled(l))return;
        try {
            UUID id=p.getPersistentData().getUUID("BlindLabDecoyActor");var d=l.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-results/run-50-"+id);
            var o=new JsonObject();o.addProperty("actor",id.toString());o.addProperty("player",p.getUUID().toString());o.addProperty("timer",score(l,"#timer"));o.addProperty("gameTime",l.getGameTime());
            o.addProperty("originalDamage",e.getOriginalDamage());o.addProperty("healthDamage",e.getNewDamage());o.addProperty("healthAfter",p.getHealth());o.addProperty("damageType",e.getSource().getMsgId());
            o.addProperty("actualActorSource",e.getSource().getEntity() instanceof ArchitectEntity a&&a.getUUID().equals(id)&&e.getSource().getDirectEntity()==a);
            var a=(ArchitectEntity)l.getEntity(id);o.add("strikeGeometry",state(l,a,p));o.add("selected",new Gson().toJsonTree(a.blindController().signal()));o.addProperty("selectedLock",a.blindController().signal()!=null&&a.blindController().signal().lock(l.getGameTime()));
            Files.writeString(d.resolve("melee-damage.jsonl"),o+"\n",StandardOpenOption.APPEND);
            if(score(l,"#decoy_cover")==0)close(l,d,score(l,"#timer"),"FIRST_DAMAGE");
        }catch(Exception failed){score(l,"#source_audit_failed",1);}
    }
    public static boolean performed(Path d,JsonArray decisions) {
        try {
            var damage=Files.readAllLines(d.resolve("melee-damage.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
            var shutter=Files.readAllLines(d.resolve("melee-shutter.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
            var frames=Files.readAllLines(d.resolve("trace.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
            if(damage.size()!=1||shutter.size()!=1||frames.size()!=480||!shutter.getFirst().get("reason").getAsString().equals("FIRST_DAMAGE"))return false;
            var hit=damage.getFirst();var g=hit.getAsJsonObject("strikeGeometry");var signal=hit.getAsJsonObject("selected");long now=hit.get("gameTime").getAsLong();
            if(!hit.get("selectedLock").getAsBoolean()||!hit.get("actualActorSource").getAsBoolean()||hit.get("healthDamage").getAsFloat()<=0||hit.get("healthAfter").getAsFloat()<=0||hit.get("healthAfter").getAsFloat()>=20
                    ||g.get("shutterClosed").getAsBoolean()||!g.get("lineOfSight").getAsBoolean()||g.get("distance").getAsDouble()>2.8
                    ||!signal.get("sense").getAsString().equals("ECHO")||!signal.get("subject").equals(hit.get("player"))
                    ||signal.get("travel").getAsDouble()>8||now-signal.get("observed").getAsLong()>25)return false;
            for(var f:frames) {
                var m=f.getAsJsonObject("melee");var mining=m.getAsJsonObject("mining");
                if(!f.get("geometryIntact").getAsBoolean()||f.get("actorHealth").getAsFloat()!=40||f.get("playerHealth").getAsFloat()<=0||m.get("knockbackResistance").getAsDouble()!=1
                        ||m.get("successfulBreaks").getAsLong()!=0||mining.has("block")&&!mining.get("block").isJsonNull()||mining.get("progressTicks").getAsInt()!=0)return false;
                if(f.get("gameTime").getAsLong()>=now&&(!m.get("shutterClosed").getAsBoolean()||f.get("playerHealth").getAsFloat()!=hit.get("healthAfter").getAsFloat()))return false;
            }
            boolean echo=false,lock=false,melee=false;
            for(var v:decisions){var e=v.getAsJsonObject();String event=e.get("event").getAsString();echo|=event.equals("BLIND_ECHO_RETURN");lock|=event.equals("BLIND_LOCK");melee|=event.equals("MELEE_HIT");if(Set.of("BLIND_NOISE_PAIN","BLIND_DIG","BLIND_BUDGET").contains(event))return false;}
            return echo&&lock&&melee&&frames.getLast().getAsJsonArray("tracks").isEmpty()&&frames.getLast().get("selected").isJsonNull();
        }catch(Exception invalid){return false;}
    }
}
