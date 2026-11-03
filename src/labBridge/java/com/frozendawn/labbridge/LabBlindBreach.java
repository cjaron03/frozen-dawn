package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Audits an ordinary real door sound and production wall breaking; never supplies a sensing destination. */
public final class LabBlindBreach {
    private LabBlindBreach() {}
    private static BlockState original(int y) {
        return y==-44?Blocks.STONE.defaultBlockState():Blocks.STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE,SlabType.BOTTOM);
    }
    public static boolean geometry(ServerLevel level) {return geometry(level,false);}
    static boolean geometry(ServerLevel level,boolean protectedWall) {return geometry(level,protectedWall,true);}
    static boolean geometry(ServerLevel level,boolean protectedWall,boolean griefing) {
        if(level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)!=griefing||!LabBlindDoor.geometry(level))return false;
        for(int x=4;x<=19;x++)for(int z=4;z<=6;z++) {
            var floor=x<=11?Blocks.GLASS:x<=14||x>=17?Blocks.STONE:Blocks.AIR;
            if(!level.getBlockState(new BlockPos(x,-45,z)).is(floor)
                    ||!level.getBlockState(new BlockPos(x,-40,z)).is(x==9&&z==5?Blocks.SEA_LANTERN:Blocks.GLASS))return false;
        }
        for(int y=-44;y<=-41;y++)for(int x=3;x<=20;x++) {
            if(!level.getBlockState(new BlockPos(x,y,3)).is(Blocks.GLASS))return false;
            if(!level.getBlockState(new BlockPos(x,y,7)).is(Blocks.GLASS))return false;
        }
        for(int y=-44;y<=-41;y++)for(int z=4;z<=6;z++) {
            for(int x:new int[]{3,15,20})if(!level.getBlockState(new BlockPos(x,y,z)).is(x==15&&y==-43&&z==4?Blocks.AIR:Blocks.GLASS))return false;
            if(y>=-42&&!level.getBlockState(new BlockPos(13,y,z)).is(Blocks.GLASS))return false;
        }
        for(int x=4;x<=11;x++)for(int y=-44;y<=-41;y++)for(int z=4;z<=6;z++)if(!level.getBlockState(new BlockPos(x,y,z)).is(Blocks.GLASS))return false;
        for(int x=15;x<=16;x++)for(int z=4;z<=6;z++)if(!level.getBlockState(new BlockPos(x,-46,z)).is(Blocks.GLASS))return false;
        for(int y=-44;y<=-43;y++)for(int z=4;z<=6;z++) {
            var state=level.getBlockState(new BlockPos(13,y,z));
            if(protectedWall) {if(!state.is(y==-43&&z<=5?Blocks.AIR:com.frozendawn.init.ModBlocks.ACHERONITE_BLOCK.get()))return false;}
            else if(!state.isAir()&&!state.equals(original(y)))return false;
        }
        return true;
    }
    public static boolean fresh(ServerLevel level) {
        if(!geometry(level)||LabBlindDoor.state(level).get("open").getAsBoolean())return false;
        return intact(level);
    }
    static boolean intact(ServerLevel level) {
        for(int y=-44;y<=-43;y++)for(int z=4;z<=6;z++)if(!level.getBlockState(new BlockPos(13,y,z)).equals(original(y)))return false;
        return true;
    }
    public static JsonObject state(ServerLevel level,ArchitectEntity actor) {
        var out=new JsonObject();var cells=new JsonArray();int removed=0;
        for(int y=-44;y<=-43;y++)for(int z=4;z<=6;z++) {
            var pos=new BlockPos(13,y,z);var row=new JsonObject();row.addProperty("position",pos.toShortString());
            row.addProperty("air",level.getBlockState(pos).isAir());if(level.getBlockState(pos).isAir())removed++;
            int ordinary=Math.min(300,Math.max(4,(int)Math.ceil(ArchitectBlockBreaker.getEffectiveBreakTime(original(y),pos,level)*20F)));
            row.addProperty("ordinaryRequiredTicks",ordinary);row.addProperty("blindRequiredTicks",ordinary*2);cells.add(row);
        }
        out.add("cells",cells);out.addProperty("removed",removed);
        out.addProperty("successfulBreaks",actor.successfulBreakCount());
        out.add("mining",new Gson().toJsonTree(actor.debugSnapshot(com.frozendawn.debug.architect.ArchitectDebugSnapshot.Lab.field(),false).mining()));
        out.add("door",LabBlindDoor.state(level));return out;
    }
    public static boolean performed(Path directory,JsonArray decisions) {
        try {
            var frames=Files.readAllLines(directory.resolve("trace.jsonl")).stream().map(s->JsonParser.parseString(s).getAsJsonObject()).toList();
            var doors=Files.readAllLines(directory.resolve("doors.jsonl")).stream().map(s->JsonParser.parseString(s).getAsJsonObject()).toList();
            if(frames.size()!=480||doors.size()!=1)return false;
            var door=doors.getFirst();int opened=door.get("timer").getAsInt();
            if(opened<80||opened>140||!door.get("valid").getAsBoolean()||!door.get("actualDoorEvent").getAsBoolean()
                    ||!door.get("emptyHand").getAsBoolean()||!door.get("open").getAsBoolean()||!door.get("kind").getAsString().equals("block_open"))return false;
            var sessions=new HashMap<String,long[]>();var broken=new HashSet<String>();long lastBreaks=0;boolean passed=false;
            for(var f:frames) {
                long at=f.get("gameTime").getAsLong();var wall=f.getAsJsonObject("breach");long breaks=wall.get("successfulBreaks").getAsLong();
                if(breaks!=wall.get("removed").getAsInt()||breaks<lastBreaks||breaks>lastBreaks+1||f.get("noiseStunPose").getAsBoolean()
                        ||f.get("actorHealth").getAsFloat()!=40||f.get("playerHealth").getAsFloat()!=20)return false;
                lastBreaks=breaks;var mining=wall.getAsJsonObject("mining");
                if(mining.has("block")&&!mining.get("block").isJsonNull()) {
                    var block=mining.getAsJsonObject("block");String key=(int)block.get("x").getAsDouble()+", "+(int)block.get("y").getAsDouble()+", "+(int)block.get("z").getAsDouble();
                    JsonObject cell=null;for(var value:wall.getAsJsonArray("cells"))if(value.getAsJsonObject().get("position").getAsString().equals(key))cell=value.getAsJsonObject();
                    if(cell==null||mining.get("requiredTicks").getAsInt()!=cell.get("blindRequiredTicks").getAsInt()||f.get("timer").getAsInt()<=opened)return false;
                    var session=sessions.computeIfAbsent(key,k->new long[]{at,-1,mining.get("requiredTicks").getAsInt()});
                    if(mining.get("progressTicks").getAsInt()>0&&session[1]<0){session[1]=at;if(at-session[0]<12)return false;}
                }
                for(var value:wall.getAsJsonArray("cells")) {
                    var cell=value.getAsJsonObject();String key=cell.get("position").getAsString();
                    if(cell.get("air").getAsBoolean()&&broken.add(key)) {
                        var session=sessions.get(key);if(session==null||session[1]<0||at-session[1]+1<session[2])return false;
                    }
                }
                for(int z=4;z<=6;z++)passed|=broken.contains("13, -44, "+z)&&broken.contains("13, -43, "+z)
                        &&f.getAsJsonObject("actorPosition").get("x").getAsDouble()>=14;
            }
            boolean received=false,tell=false;
            for(var value:decisions) {
                var e=value.getAsJsonObject();String event=e.get("event").getAsString();
                if(Set.of("BLIND_LOCK","BLIND_ECHO_RETURN","BLIND_NOISE_PAIN","BLIND_BUDGET").contains(event))return false;
                received|=event.equals("BLIND_AIR")&&e.get("detail").getAsString().contains("block_open")&&e.get("detail").getAsString().contains("outcome=RECEIVED");
                tell|=event.equals("BLIND_DIG");
            }
            return received&&tell&&passed&&broken.size()>=2;
        }catch(Exception invalid){return false;}
    }
}
