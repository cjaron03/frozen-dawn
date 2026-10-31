package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.architect.ArchitectBlockEnvironment;
import com.frozendawn.init.ModBlocks;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** A real audible door behind an intact Acheronite barrier; no injected sensing or route. */
public final class LabBlindProtectedWall {
    private LabBlindProtectedWall() {}
    public static boolean geometry(ServerLevel level) {return LabBlindBreach.geometry(level,true);}
    public static boolean fresh(ServerLevel level) {return geometry(level)&&!LabBlindDoor.state(level).get("open").getAsBoolean();}
    public static JsonObject state(ServerLevel level,ArchitectEntity actor) {
        var out=new JsonObject();var cells=new JsonArray();
        for(int y=-44;y<=-43;y++)for(int z=4;z<=6;z++) {
            if(y==-43&&z<=5)continue; // Adjacent one-high sound/sight slots, with solid floor and ceiling.
            var pos=new BlockPos(13,y,z);var row=new JsonObject();row.addProperty("position",pos.toShortString());
            row.addProperty("intact",level.getBlockState(pos).is(ModBlocks.ACHERONITE_BLOCK.get()));
            // Production policy rejects this registered material as TOO_HARD (hardness50)
            // before its named PROTECTED branch. Keep the actual reason in the evidence.
            row.addProperty("breakRejection",ArchitectBlockEnvironment.breakRejection(level,pos,List.of()));cells.add(row);
        }
        out.add("cells",cells);out.addProperty("successfulBreaks",actor.successfulBreakCount());
        out.add("mining",new Gson().toJsonTree(actor.debugSnapshot(com.frozendawn.debug.architect.ArchitectDebugSnapshot.Lab.field(),false).mining()));
        out.add("door",LabBlindDoor.state(level));return out;
    }
    public static boolean performed(Path directory,JsonArray decisions) {return performed(directory,decisions,false);}
    static boolean performed(Path directory,JsonArray decisions,boolean griefingDisabled) {
        try {
            var frames=Files.readAllLines(directory.resolve("trace.jsonl")).stream().map(s->JsonParser.parseString(s).getAsJsonObject()).toList();
            var doors=Files.readAllLines(directory.resolve("doors.jsonl")).stream().map(s->JsonParser.parseString(s).getAsJsonObject()).toList();
            if(frames.size()!=480||doors.size()!=1)return false;
            var door=doors.getFirst();int opened=door.get("timer").getAsInt();
            if(opened<80||opened>140||!door.get("valid").getAsBoolean()||!door.get("actualDoorEvent").getAsBoolean()
                    ||!door.get("emptyHand").getAsBoolean()||!door.get("open").getAsBoolean()||!door.get("kind").getAsString().equals("block_open"))return false;
            boolean selectedHearing=false,investigated=false,received=false;
            for(var f:frames) {
                var wall=f.getAsJsonObject(griefingDisabled?"griefing":"protectedWall");var mining=wall.getAsJsonObject("mining");
                if(!f.get("geometryIntact").getAsBoolean()||wall.get("successfulBreaks").getAsLong()!=0||f.get("noiseStunPose").getAsBoolean()
                        ||f.get("actorHealth").getAsFloat()!=40||f.get("playerHealth").getAsFloat()!=20
                        ||f.getAsJsonObject("actorPosition").get("x").getAsDouble()>=13
                        ||mining.has("block")&&!mining.get("block").isJsonNull()||mining.get("progressTicks").getAsInt()!=0)return false;
                if(wall.getAsJsonArray("cells").size()!=(griefingDisabled?6:4))return false;
                if(griefingDisabled) {
                    if(wall.get("mobGriefing").getAsBoolean()||wall.get("removed").getAsInt()!=0)return false;
                    for(var value:wall.getAsJsonArray("cells"))if(value.getAsJsonObject().get("air").getAsBoolean())return false;
                } else for(var value:wall.getAsJsonArray("cells"))if(!value.getAsJsonObject().get("intact").getAsBoolean()
                    ||!value.getAsJsonObject().get("breakRejection").getAsString().equals("TOO_HARD"))return false;
                if(f.get("timer").getAsInt()<=opened&&!f.getAsJsonArray("tracks").isEmpty())return false;
                if(f.has("selected")&&!f.get("selected").isJsonNull()) {
                    var signal=f.getAsJsonObject("selected");
                    if(!signal.get("sense").getAsString().equals("HEARING")||signal.has("subject")&&!signal.get("subject").isJsonNull())return false;
                    selectedHearing=true;investigated|=f.get("diagnostics").getAsString().startsWith("blind=INVESTIGATE")||f.get("diagnostics").getAsString().startsWith("blind=SEARCH");
                }
            }
            var last=frames.getLast();if(!last.getAsJsonArray("tracks").isEmpty()||last.has("selected")&&!last.get("selected").isJsonNull())return false;
            for(var value:decisions) {
                var e=value.getAsJsonObject();String event=e.get("event").getAsString();
                if(Set.of("BLIND_LOCK","BLIND_ECHO_RETURN","BLIND_DIG","BLIND_NOISE_PAIN","BLIND_BUDGET").contains(event))return false;
                received|=event.equals("BLIND_AIR")&&e.get("detail").getAsString().contains("block_open")&&e.get("detail").getAsString().contains("outcome=RECEIVED");
            }
            return received&&selectedHearing&&investigated;
        }catch(Exception invalid){return false;}
    }
}
