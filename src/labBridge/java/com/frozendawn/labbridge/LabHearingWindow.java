package com.frozendawn.labbridge;

import com.google.gson.JsonObject;
import java.util.List;

/** Qualifies the stationary mining window of legacy32; preserves its original startup flags. */
public final class LabHearingWindow {
    private LabHearingWindow() { }
    public static boolean qualifies(JsonObject summary, List<JsonObject> frames, List<JsonObject> decisions) {
        return qualifies(summary,frames,decisions,32,2,false);
    }
    public static boolean qualifiesMiningPredecessor(JsonObject summary,List<JsonObject> frames,List<JsonObject> decisions) {
        try { int run=summary.get("run").getAsInt();
        return run==33 ? qualifies(summary,frames,decisions,33,1,false)
                :run==34 && qualifies(summary,frames,decisions,34,0,true);
        } catch(RuntimeException malformed) { return false; }
    }
    public static boolean qualifiesMiningView(JsonObject summary,List<JsonObject> frames,List<JsonObject> decisions) {
        try { int run=summary.get("run").getAsInt();
            return (run==34 || run==35) && qualifies(summary,frames,decisions,run,0,true);
        } catch(RuntimeException malformed) { return false; }
    }
    private static boolean qualifies(JsonObject summary,List<JsonObject> frames,List<JsonObject> decisions,int run,int flags,boolean vibration) {
        try {
            if (summary.get("run").getAsInt() != run || summary.get("complete").getAsBoolean() != (flags==0)
                    || summary.get("inconclusiveFrames").getAsInt() != flags || summary.get("frames").getAsInt() != 480
                    || frames.size() != 480) return false;
            long first = frames.getFirst().get("gameTime").getAsLong();
            for (int i = 0; i < frames.size(); i++) {
                var row = frames.get(i); var position = row.getAsJsonObject("playerPosition");
                var isolation = row.getAsJsonObject("isolation");
                if (row.get("timer").getAsInt() != i + 1 || row.get("gameTime").getAsLong() != first + i
                        || row.get("run").getAsInt() != run || row.get("case").getAsInt() != run
                        || !row.get("actor").equals(summary.get("actor")) || !row.get("inputProbe").equals(summary.get("inputProbe"))
                        || row.get("inconclusiveFrames").getAsInt() != Math.min(i + 1, flags)
                        || row.get("playerOnGround").getAsBoolean() != (i >= flags)
                        || position.get("x").getAsDouble() != (vibration ? 17.5 : 13.5) || position.get("y").getAsDouble() != -44
                        || position.get("z").getAsDouble() != 5.5 || row.get("playerSneaking").getAsBoolean()
                        || row.get("playerSprinting").getAsBoolean() || !row.get("playerPose").getAsString().equals("STANDING")
                        || !row.get("playerMode").getAsString().equals("survival")
                        || !row.get("geometryIntact").getAsBoolean() || !row.get("actorPresent").getAsBoolean()
                        || !row.get("actorAlive").getAsBoolean() || !row.get("playerAlive").getAsBoolean()
                        || row.get("actorHealth").getAsDouble() != 40 || row.get("playerHealth").getAsDouble() != 20
                        || row.get("actorNoAi").getAsBoolean() || row.get("vanillaTargetPresent").getAsBoolean()
                        || row.get("journalDropped").getAsInt() != 0 || !isolation.get("enabled").getAsBoolean()
                        || !isolation.get("sourcesClean").getAsBoolean() || isolation.get("sourceAuditFailures").getAsInt() != 0
                        || !isolation.getAsJsonArray("unexpectedMobs").isEmpty()
                        || !isolation.getAsJsonArray("unexpectedSourceObjects").isEmpty()) return false;
                for (var entry : row.getAsJsonArray("tracks")) {
                    var track = entry.getAsJsonObject();
                    if (run==33 || run==35 || !track.get("sense").getAsString().equals(vibration ? "VIBRATION" : "HEARING")
                            || track.has("subject") && !track.get("subject").isJsonNull()) return false;
                }
            }
            int tools = 0, heard = 0;
            for (var event : decisions) {
                String kind = event.get("event").getAsString(), detail = event.get("detail").getAsString();
                long frame = event.get("tick").getAsLong() - first + 1;
                if (kind.equals("BLIND_VIBRATION") || kind.equals("BLIND_AIR")) {
                    // The original STOP response may span one second, but mining must stay fully grounded.
                    if (frame <= 80 || frame > 260 || !detail.contains("kind=TOOL")) return false;
                    if (kind.equals("BLIND_VIBRATION")) { if (!detail.contains(run==34 ? "outcome=DETECTED" : "outcome=NO_PATH")) return false; tools++; }
                    else { if (!detail.contains(run==32 ? "outcome=RECEIVED" : "outcome=NO_PATH_WITHIN_RANGE")) return false; heard++; }
                }
                if (kind.equals("BLIND_LOCK") || kind.equals("BLIND_ECHO_RETURN")
                        || kind.equals("BLIND_ECHO_QUERY") && !detail.contains("outcome=NO_DIRECT_RETURN")) return false;
            }
            return tools >= 2 && heard == tools;
        } catch (RuntimeException malformed) { return false; }
    }
}
