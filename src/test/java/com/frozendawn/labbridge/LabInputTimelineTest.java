package com.frozendawn.labbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LabInputTimelineTest {
    private record Fixture(JsonObject start, JsonObject summary, List<JsonObject> server, List<JsonObject> client) {
        boolean qualifies() { return LabInputTimeline.qualifies(start, summary, server, client, List.of()); }
    }
    private Fixture fixture(int samples) {
        var start = new JsonObject(); start.addProperty("session", "session"); start.addProperty("player", "player");
        start.addProperty("actor", "actor"); start.addProperty("probe", "probe"); start.addProperty("viewRun", 31); start.addProperty("serverTickLimit", 480);
        var summary = start.deepCopy(); summary.addProperty("captureComplete", true); summary.addProperty("active", false);
        summary.addProperty("dropped", 0); summary.addProperty("reason", "TIME_LIMIT"); summary.addProperty("serverFrames", 480);
        summary.addProperty("clientFrames", samples); summary.addProperty("keyEvents", 0);
        var server = new ArrayList<JsonObject>(); var client = new ArrayList<JsonObject>();
        for (int i = 0; i < 480; i++) {
            var row = new JsonObject(); row.addProperty("session", "session"); row.addProperty("viewRun", 31);
            row.addProperty("viewTimer", i + 1); row.addProperty("viewStage", i == 479 ? 3 : 2);
            row.addProperty("gameTime", 1000 + i); row.addProperty("sampledAtMillis", 100000 + i * 50);
            var player = new JsonObject(); player.addProperty("uuid", "player"); player.addProperty("gameMode", i == 479 ? "creative" : "survival");
            var actor = new JsonObject(); actor.addProperty("uuid", "actor"); actor.addProperty("noAI", i == 479);
            var players = new JsonArray(); players.add(player); row.add("players", players);
            var actors = new JsonArray(); actors.add(actor); row.add("architects", actors); server.add(row);
        }
        for (int i = 0; i < samples; i++) {
            var row = new JsonObject(); row.addProperty("session", "session"); row.addProperty("player", "player");
            row.addProperty("focused", true); row.addProperty("paused", false); row.addProperty("screenOpen", false);
            row.addProperty("gameTime", 1000 + Math.min(i, 479)); row.addProperty("playerTick", 50 + i);
            row.addProperty("sampledAtMillis", 100001 + i * 50); client.add(row);
        }
        return new Fixture(start, summary, server, client);
    }
    @Test void boundaryCountsQualifyWithoutPaddingOrChangingCounts() {
        for (int count : new int[]{479, 480, 481}) {
            var f = fixture(count); String before = f.summary.toString(); assertTrue(f.qualifies());
            assertEquals(count, f.client.size()); assertEquals(before, f.summary.toString());
        }
    }
    @Test void countAloneCannotQualifySparseMissingOrStaleRows() {
        assertFalse(fixture(478).qualifies());
        var f = fixture(479); f.client.get(240).addProperty("playerTick", 9999); assertFalse(f.qualifies());
        f = fixture(479); f.client.get(240).addProperty("gameTime", 1250); assertFalse(f.qualifies());
        f = fixture(479); f.client.get(240).addProperty("session", "stale"); assertFalse(f.qualifies());
        f = fixture(479); f.client.get(0).addProperty("gameTime", 900); assertFalse(f.qualifies());
        f = fixture(479); f.server.remove(100); assertFalse(f.qualifies());
    }
    @Test void changedParticipantHoldStateFocusAndDropsRemainRejected() {
        var f = fixture(479);
        f.server.get(200).getAsJsonArray("architects").get(0).getAsJsonObject().addProperty("uuid", "other"); assertFalse(f.qualifies());
        f = fixture(479); f.server.get(479).getAsJsonArray("architects").get(0).getAsJsonObject().addProperty("noAI", false); assertFalse(f.qualifies());
        f = fixture(479); f.client.get(300).addProperty("screenOpen", true); assertFalse(f.qualifies());
        f = fixture(479); f.summary.addProperty("dropped", 1); assertFalse(f.qualifies());
        f = fixture(479); f.summary.addProperty("captureComplete", false); assertFalse(f.qualifies());
    }
    @Test void packetClockCorrectionsAreRetainedAlongsidePublishedTicksWithoutRequalifyingLegacyRows() {
        var legacy=fixture(479);legacy.client.get(142).addProperty("gameTime",1140);legacy.client.get(159).addProperty("gameTime",1163);
        assertFalse(legacy.qualifies());
        var recorded=fixture(479);var clock=new LabClientClock();
        for(int i=0;i<479;i++) {
            clock.publish(1000+i);long raw=1000+i;
            if(i==142)raw-=2;if(i==159)raw+=4;clock.sample(recorded.client.get(i),raw);
        }
        assertTrue(recorded.qualifies());assertEquals(1140,recorded.client.get(142).get("clientWorldGameTime").getAsLong());
        assertEquals(1163,recorded.client.get(159).get("clientWorldGameTime").getAsLong());assertFalse(legacy.qualifies());
        recorded.client.get(200).addProperty("playerTick",9999);assertFalse(recorded.qualifies());
    }

}
