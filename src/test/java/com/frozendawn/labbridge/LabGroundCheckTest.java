package com.frozendawn.labbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LabGroundCheckTest {
    @TempDir Path root;
    private final UUID player=UUID.randomUUID(),actor=UUID.randomUUID();
    private JsonObject participant(boolean grounded) {
        var p=new JsonObject();p.addProperty("uuid",player.toString());p.addProperty("gameMode","survival");p.addProperty("pose","STANDING");
        p.addProperty("onGround",grounded);p.addProperty("flying",false);p.addProperty("sneaking",false);p.addProperty("sprinting",false);
        p.addProperty("x",17.5);p.addProperty("y",-44);p.addProperty("z",5.5);return p;
    }
    private JsonObject server(long tick,boolean grounded) {
        var s=new JsonObject();s.addProperty("session","session");s.addProperty("gameTime",tick);s.addProperty("viewRun",34);s.addProperty("viewStage",1);s.addProperty("viewTimer",0);
        var p=new JsonArray();p.add(participant(grounded));s.add("players",p);var a=new JsonObject();a.addProperty("uuid",actor.toString());a.addProperty("health",40);a.addProperty("noAI",true);
        var actors=new JsonArray();actors.add(a);s.add("architects",actors);return s;
    }
    private JsonObject client(long tick,boolean grounded) {
        var c=participant(grounded);c.addProperty("session","session");c.addProperty("player",player.toString());c.addProperty("gameTime",tick);
        c.addProperty("focused",true);c.addProperty("screenOpen",false);c.addProperty("paused",false);c.add("mapped",new JsonObject());c.add("windowKeys",new JsonObject());return c;
    }
    private LabGroundCheck check() throws Exception {
        var start=new JsonObject();start.addProperty("session","session");start.addProperty("player",player.toString());start.addProperty("actor",actor.toString());start.addProperty("viewRun",34);start.addProperty("x",17.5);
        return new LabGroundCheck(root,start);
    }
    @Test void bothClocksSettleBeforeAnyEncounterStartsAndRetainFalseFlags() throws Exception {
        var check=check();
        for(int i=1;i<=44;i++) {check.client(client(i,i>4),false);assertEquals(i==44,check.server(server(i,i>2)));}
        var status=check.status();assertEquals("GROUND_CONFIRMED",status.get("reason").getAsString());assertEquals(44,status.get("serverSamples").getAsInt());
        var id=UUID.fromString(status.get("check").getAsString());assertTrue(LabGroundCheck.retained(root,id,player,actor,34));
        var d=Path.of(status.get("directory").getAsString());assertTrue(Files.readAllLines(d.resolve("server.jsonl")).getFirst().contains("\"groundValid\":false"));
        String original=Files.readString(d.resolve("client.jsonl"));Files.writeString(d.resolve("client.jsonl"),original+" ");assertFalse(LabGroundCheck.retained(root,id,player,actor,34));
    }
    @Test void staleClientAndMovementRestartTheContinuousWindow() throws Exception {
        var check=check();check.client(client(1,true),false);
        for(int i=1;i<=10;i++) assertFalse(check.server(server(i,true)));assertEquals(0,check.status().get("stableTicks").getAsInt());
        for(int i=11;i<=30;i++) {check.client(client(i,true),false);assertFalse(check.server(server(i,true)));}
        var moving=client(31,true);moving.getAsJsonObject("mapped").addProperty("forward",true);check.client(moving,false);assertFalse(check.server(server(31,true)));
        for(int i=32;i<=71;i++) {check.client(client(i,true),false);assertEquals(i==71,check.server(server(i,true)));}
    }
    @Test void missingClientTimesOutWithoutStartingAndWrongIdentityCannotReuseReadiness() throws Exception {
        var check=check();for(int i=1;i<=2400;i++)assertFalse(check.server(server(i,true)));assertEquals("TIME_LIMIT",check.status().get("reason").getAsString());
        assertFalse(LabGroundCheck.retained(root,UUID.fromString(check.status().get("check").getAsString()),player,actor,34));
        check=check();for(int i=1;i<=40;i++){check.client(client(i,true),false);check.server(server(i,true));}
        assertFalse(LabGroundCheck.retained(root,UUID.fromString(check.status().get("check").getAsString()),UUID.randomUUID(),actor,34));
    }
}
