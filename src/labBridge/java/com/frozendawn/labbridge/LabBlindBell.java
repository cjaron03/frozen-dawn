package com.frozendawn.labbridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BellBlock;

/** Rings an actual supported vanilla bell. The production block supplies sound and events. */
public final class LabBlindBell {
    public static final BlockPos BELL=new BlockPos(14,-44,5);
    private LabBlindBell() {}
    public static boolean geometry(ServerLevel level) {
        return level.getBlockState(BELL).is(Blocks.BELL)&&level.getBlockState(BELL.below()).is(Blocks.STONE)
                &&level.getBlockState(LabBlindDoor.DOOR).isAir()&&level.getBlockState(LabBlindDoor.DOOR.above()).isAir();
    }
    public static JsonObject state(ServerLevel level) {
        var row=new JsonObject();row.addProperty("present",level.getBlockState(BELL).is(Blocks.BELL));
        row.addProperty("supported",level.getBlockState(BELL.below()).is(Blocks.STONE));return row;
    }
    public static boolean control(CommandSourceStack source,Path directory,int timer) {
        if(!geometry(source.getLevel()))return false;
        if(!(timer>=80&&timer<=109||timer==160||timer==340))return true;
        var level=source.getLevel();var row=state(level);row.addProperty("timer",timer);row.addProperty("gameTime",level.getGameTime());
        row.addProperty("rang",((BellBlock)Blocks.BELL).attemptToRing(level,BELL,Direction.WEST));
        try {Files.writeString(directory.resolve("bells.jsonl"),row+"\n",StandardOpenOption.APPEND);return row.get("rang").getAsBoolean();}
        catch(Exception failure){return false;}
    }
    public static boolean performed(Path directory,JsonArray decisions) {
        try {
            var rings=Files.readAllLines(directory.resolve("bells.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
            if(rings.size()!=32)return false;
            for(int i=0;i<32;i++)if(rings.get(i).get("timer").getAsInt()!=(i<30?80+i:i==30?160:340)
                    ||!rings.get(i).get("rang").getAsBoolean())return false;
            var pain=java.util.stream.StreamSupport.stream(decisions.spliterator(),false).map(x->x.getAsJsonObject())
                    .filter(x->x.get("event").getAsString().equals("BLIND_NOISE_PAIN")).toList();
            if(pain.size()!=2||pain.stream().anyMatch(x->!x.get("detail").getAsString().contains("explosion=false")))return false;
            var frames=Files.readAllLines(directory.resolve("trace.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
            if(frames.size()!=480||frames.getLast().get("actorHealth").getAsFloat()!=36)return false;
            // A ring within cooldown cannot renew stun or add damage; recovery precedes the later ring.
            return frames.stream().filter(x->x.get("timer").getAsInt()<80).allMatch(x->x.get("actorHealth").getAsFloat()==40)
                    &&frames.stream().filter(x->x.get("timer").getAsInt()>=110&&x.get("timer").getAsInt()<340).allMatch(x->x.get("actorHealth").getAsFloat()==38&&!x.get("diagnostics").getAsString().startsWith("blind=STUN"))
                    &&frames.stream().anyMatch(x->x.get("timer").getAsInt()>205&&x.get("timer").getAsInt()<340&&x.get("diagnostics").getAsString().startsWith("blind=WANDER"))
                    &&frames.stream().filter(x->x.get("diagnostics").getAsString().startsWith("blind=STUN")).count()==50
                    &&frames.stream().filter(x->x.get("diagnostics").getAsString().startsWith("blind=FLEE")).count()==200;
        }catch(Exception malformed){return false;}
    }
}
