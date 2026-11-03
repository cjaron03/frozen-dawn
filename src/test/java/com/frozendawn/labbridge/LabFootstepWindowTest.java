package com.frozendawn.labbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LabFootstepWindowTest {
    private record Fixture(JsonObject summary, List<JsonObject> frames, List<JsonObject> decisions) { }
    private Fixture fixture(boolean initialCrouch) {
        var frames = new ArrayList<JsonObject>(); double z = 5.5, distance = 0; int direction = 1, invalid = 0;
        for (int timer = 1; timer <= 480; timer++) {
            var row = new JsonObject(); row.addProperty("run",29); row.addProperty("case",29); row.addProperty("timer",timer);
            row.addProperty("gameTime",1000+timer); row.addProperty("actor","actor"); row.addProperty("inputProbe","probe");
            boolean crouched = initialCrouch && timer >= 3 && timer <= 103;
            row.addProperty("playerPose",crouched?"CROUCHING":"STANDING"); row.addProperty("playerSneaking",crouched);
            row.addProperty("playerSprinting",false); row.addProperty("playerOnGround",true); row.addProperty("playerMode","survival");
            row.addProperty("actorPresent",true); row.addProperty("actorAlive",true); row.addProperty("playerAlive",true);
            row.addProperty("actorNoAi",false); row.addProperty("actorHealth",40); row.addProperty("playerHealth",20);
            row.addProperty("geometryIntact",true); row.addProperty("journalDropped",0); row.addProperty("vanillaTargetPresent",false);
            var isolation = new JsonObject(); isolation.addProperty("enabled",true); isolation.addProperty("sourcesClean",true);
            isolation.addProperty("sourceAuditFailures",0); isolation.add("unexpectedMobs",new JsonArray()); isolation.add("unexpectedSourceObjects",new JsonArray());
            row.add("isolation",isolation); row.add("tracks",new JsonArray());
            if (timer >= 106 && timer <= 255) {
                if (z >= 7.4) direction=-1; if (z <= 3.6) direction=1;
                z += direction*.14; if (timer <= 240) distance += .14;
            }
            var position=new JsonObject(); position.addProperty("x",17.5); position.addProperty("y",-44); position.addProperty("z",z); row.add("playerPosition",position);
            if (crouched && timer > 80) invalid++; row.addProperty("inconclusiveFrames",invalid); frames.add(row);
        }
        var summary = new JsonObject(); summary.addProperty("run",29); summary.addProperty("frames",480); summary.addProperty("actor","actor");
        summary.addProperty("inputProbe","probe"); summary.addProperty("walkingSurface","STONE"); summary.addProperty("walkingDistance",distance);
        summary.addProperty("inconclusiveFrames",invalid); summary.addProperty("complete",invalid==0);
        var decisions=new ArrayList<JsonObject>();
        for(int timer:new int[]{117,248}) { var event=new JsonObject(); event.addProperty("event","BLIND_VIBRATION");event.addProperty("tick",1000+timer);event.addProperty("detail","kind=FOOTSTEP outcome=DETECTED");decisions.add(event); }
        return new Fixture(summary,frames,decisions);
    }
    @Test void stationaryStartupCrouchQualifiesWithoutChangingWholeViewOutcome() {
        var f=fixture(true); var before=f.summary.toString();
        assertEquals(23,f.summary.get("inconclusiveFrames").getAsInt());
        assertTrue(LabFootstepWindow.qualifies(f.summary,f.frames,f.decisions));
        assertFalse(f.summary.get("complete").getAsBoolean()); assertEquals(before,f.summary.toString());
        var clean=fixture(false); assertTrue(LabFootstepWindow.qualifies(clean.summary,clean.frames,clean.decisions));
    }
    @Test void movingCrouchAndLateMovementRemainRejected() {
        var f=fixture(true); f.frames.get(134).addProperty("playerPose","CROUCHING");f.frames.get(134).addProperty("playerSneaking",true);
        assertFalse(LabFootstepWindow.qualifies(f.summary,f.frames,f.decisions));
        f=fixture(true);f.frames.get(270).getAsJsonObject("playerPosition").addProperty("z",f.frames.get(269).getAsJsonObject("playerPosition").get("z").getAsDouble()+.03);
        assertFalse(LabFootstepWindow.qualifies(f.summary,f.frames,f.decisions));
    }
    @Test void missingFramesMixedTracksAndUnmeasuredStepsCannotQualify() {
        var f=fixture(true);f.frames.removeLast();assertFalse(LabFootstepWindow.qualifies(f.summary,f.frames,f.decisions));
        f=fixture(true);var track=new JsonObject();track.addProperty("sense","ECHO");track.addProperty("travel",0);f.frames.get(150).getAsJsonArray("tracks").add(track);
        assertFalse(LabFootstepWindow.qualifies(f.summary,f.frames,f.decisions));
        f=fixture(true);for(var event:f.decisions)event.addProperty("detail","kind=FOOTSTEP outcome=NO_SOURCE_CONTACT");assertFalse(LabFootstepWindow.qualifies(f.summary,f.frames,f.decisions));
        f=fixture(true);f.frames.get(150).addProperty("geometryIntact",false);assertFalse(LabFootstepWindow.qualifies(f.summary,f.frames,f.decisions));
    }
}
