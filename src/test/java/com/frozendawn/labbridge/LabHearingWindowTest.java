package com.frozendawn.labbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LabHearingWindowTest {
    private record Fixture(JsonObject summary, List<JsonObject> frames, List<JsonObject> decisions) {
        boolean qualifies() { return LabHearingWindow.qualifies(summary, frames, decisions); }
    }
    private Fixture fixture() {
        var summary=new JsonObject();summary.addProperty("run",32);summary.addProperty("complete",false);
        summary.addProperty("inconclusiveFrames",2);summary.addProperty("frames",480);summary.addProperty("actor","actor");summary.addProperty("inputProbe","probe");
        var frames=new ArrayList<JsonObject>();
        for(int i=0;i<480;i++) {
            var row=new JsonObject();row.addProperty("run",32);row.addProperty("case",32);row.addProperty("timer",i+1);row.addProperty("gameTime",1000+i);
            row.addProperty("actor","actor");row.addProperty("inputProbe","probe");row.addProperty("inconclusiveFrames",Math.min(i+1,2));row.addProperty("playerOnGround",i>=2);
            var pos=new JsonObject();pos.addProperty("x",13.5);pos.addProperty("y",-44);pos.addProperty("z",5.5);row.add("playerPosition",pos);
            row.addProperty("playerPose","STANDING");row.addProperty("playerMode","survival");row.addProperty("actorHealth",40);row.addProperty("playerHealth",20);row.addProperty("journalDropped",0);
            for(String name:List.of("actorPresent","actorAlive","playerAlive","geometryIntact"))row.addProperty(name,true);
            for(String name:List.of("playerSneaking","playerSprinting","actorNoAi","vanillaTargetPresent"))row.addProperty(name,false);
            var isolation=new JsonObject();isolation.addProperty("enabled",true);isolation.addProperty("sourcesClean",true);isolation.addProperty("sourceAuditFailures",0);
            isolation.add("unexpectedMobs",new JsonArray());isolation.add("unexpectedSourceObjects",new JsonArray());row.add("isolation",isolation);row.add("tracks",new JsonArray());frames.add(row);
        }
        var decisions=new ArrayList<JsonObject>();
        for(int time:new int[]{1102,1112})for(String event:List.of("BLIND_VIBRATION","BLIND_AIR")) {
            var e=new JsonObject();e.addProperty("tick",time);e.addProperty("event",event);e.addProperty("detail", "kind=TOOL outcome="+(event.equals("BLIND_AIR")?"RECEIVED":"NO_PATH"));decisions.add(e);
        }
        return new Fixture(summary,frames,decisions);
    }
    @Test void onlyLegacyStartupFlagsQualifyWithoutRewritingOutcome() {
        var f=fixture();String original=f.summary.toString();assertTrue(f.qualifies());assertEquals(original,f.summary.toString());assertFalse(f.summary.get("complete").getAsBoolean());
        f.summary.addProperty("inconclusiveFrames",3);assertFalse(f.qualifies());
    }
    @Test void movementPostureLateGroundLossAndMissingFramesReject() {
        var f=fixture();f.frames.get(110).getAsJsonObject("playerPosition").addProperty("x",13.6);assertFalse(f.qualifies());
        f=fixture();f.frames.get(110).addProperty("playerSneaking",true);assertFalse(f.qualifies());
        f=fixture();f.frames.get(110).addProperty("playerOnGround",false);assertFalse(f.qualifies());
        f=fixture();f.frames.removeLast();assertFalse(f.qualifies());
    }
    @Test void onlyAcceptedGroundedMiningWithoutCompetingSensesQualifies() {
        var f=fixture();f.decisions.get(0).addProperty("tick",1001);assertFalse(f.qualifies());
        f=fixture();f.decisions.get(0).addProperty("tick",1261);assertFalse(f.qualifies());
        f=fixture();f.decisions.get(1).addProperty("detail","kind=TOOL outcome=UNKNOWN_BUDGET");assertFalse(f.qualifies());
        f=fixture();f.decisions.get(0).addProperty("detail","kind=FOOTSTEP outcome=NO_PATH");assertFalse(f.qualifies());
        f=fixture();f.decisions.clear();assertFalse(f.qualifies());
        f=fixture();var contact=new JsonObject();contact.addProperty("sense","ECHO");f.frames.get(150).getAsJsonArray("tracks").add(contact);assertFalse(f.qualifies());
    }
    private Fixture miningFixture(int run) {
        var f=fixture();int flags=run==33?1:0;f.summary.addProperty("run",run);f.summary.addProperty("complete",flags==0);f.summary.addProperty("inconclusiveFrames",flags);
        for(int i=0;i<480;i++) {var row=f.frames.get(i);row.addProperty("run",run);row.addProperty("case",run);row.addProperty("inconclusiveFrames",Math.min(i+1,flags));
            row.addProperty("playerOnGround",i>=flags);row.getAsJsonObject("playerPosition").addProperty("x",run==33?13.5:17.5);}
        for(var e:f.decisions)e.addProperty("detail","kind=TOOL outcome="+(e.get("event").getAsString().equals("BLIND_AIR")?"NO_PATH_WITHIN_RANGE":run==33?"NO_PATH":"DETECTED"));return f;
    }
    @Test void sealedOriginalFlagQualifiesOnlyWithoutAnyContacts() {
        var f=miningFixture(33);String original=f.summary.toString();assertTrue(LabHearingWindow.qualifiesMiningPredecessor(f.summary,f.frames,f.decisions));assertEquals(original,f.summary.toString());
        var contact=new JsonObject();contact.addProperty("sense","HEARING");f.frames.get(150).getAsJsonArray("tracks").add(contact);assertFalse(LabHearingWindow.qualifiesMiningPredecessor(f.summary,f.frames,f.decisions));
        f=miningFixture(33);f.frames.get(20).addProperty("playerOnGround",false);assertFalse(LabHearingWindow.qualifiesMiningPredecessor(f.summary,f.frames,f.decisions));
    }
    @Test void connectedPredecessorRequiresGroundedDetectedToolsWithoutAir() {
        var f=miningFixture(34);assertTrue(LabHearingWindow.qualifiesMiningPredecessor(f.summary,f.frames,f.decisions));
        f.decisions.get(0).addProperty("detail","kind=TOOL outcome=NO_PATH");assertFalse(LabHearingWindow.qualifiesMiningPredecessor(f.summary,f.frames,f.decisions));
        f=miningFixture(34);f.decisions.get(1).addProperty("detail","kind=TOOL outcome=RECEIVED");assertFalse(LabHearingWindow.qualifiesMiningPredecessor(f.summary,f.frames,f.decisions));
    }

    @Test void wholeMiningViewRequiresAcceptedWindowAndKnownRejection() {
        var f=miningFixture(34);assertTrue(LabHearingWindow.qualifiesMiningView(f.summary,f.frames,f.decisions));
        f.decisions.get(0).addProperty("tick",1001);assertFalse(LabHearingWindow.qualifiesMiningView(f.summary,f.frames,f.decisions));
        f=miningFixture(34);f.decisions.get(1).addProperty("detail","kind=TOOL outcome=UNKNOWN_BUDGET");assertFalse(LabHearingWindow.qualifiesMiningView(f.summary,f.frames,f.decisions));
        f=miningFixture(34);f.summary.addProperty("run",35);for(var row:f.frames){row.addProperty("run",35);row.addProperty("case",35);}
        for(var e:f.decisions)if(e.get("event").getAsString().equals("BLIND_VIBRATION"))e.addProperty("detail","kind=TOOL outcome=NO_PATH");
        assertTrue(LabHearingWindow.qualifiesMiningView(f.summary,f.frames,f.decisions));
        f.decisions.get(0).addProperty("tick",1261);assertFalse(LabHearingWindow.qualifiesMiningView(f.summary,f.frames,f.decisions));
    }

}
