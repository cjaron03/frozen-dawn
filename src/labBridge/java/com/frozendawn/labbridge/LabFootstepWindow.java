package com.frozendawn.labbridge;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;

/** Qualifies a standing material comparison without rewriting its whole-view outcome. */
public final class LabFootstepWindow {
    private LabFootstepWindow() { }

    public static boolean qualifies(JsonObject summary, List<JsonObject> frames, List<JsonObject> decisions) {
        try {
            int run = summary.get("run").getAsInt();
            if ((run != 29 && run != 30) || summary.get("frames").getAsInt() != 480 || frames.size() != 480
                    || !summary.get("walkingSurface").getAsString().equals(run == 29 ? "STONE" : "WHITE_WOOL")) return false;
            double previousX = 17.5, previousZ = 5.5, walkingDistance = 0;
            boolean walkingStarted = false;
            int startupPostureFrames = 0;
            long firstTick = frames.getFirst().get("gameTime").getAsLong();
            var byTick = new HashMap<Long, JsonObject>();
            for (int i = 0; i < frames.size(); i++) {
                var frame = frames.get(i); int timer = i + 1;
                if (frame.get("timer").getAsInt() != timer || frame.get("gameTime").getAsLong() != firstTick + i
                        || frame.get("run").getAsInt() != run || frame.get("case").getAsInt() != run
                        || !frame.get("actor").equals(summary.get("actor"))
                        || !frame.get("inputProbe").equals(summary.get("inputProbe"))
                        || !frame.get("geometryIntact").getAsBoolean() || frame.get("journalDropped").getAsInt() != 0
                        || frame.get("vanillaTargetPresent").getAsBoolean() || !frame.get("actorPresent").getAsBoolean()
                        || !frame.get("actorAlive").getAsBoolean() || !frame.get("playerAlive").getAsBoolean()
                        || frame.get("actorNoAi").getAsBoolean() || frame.get("actorHealth").getAsDouble() != 40
                        || frame.get("playerHealth").getAsDouble() != 20
                        || !frame.get("playerMode").getAsString().equals("survival")
                        || frame.get("playerSprinting").getAsBoolean()) return false;
                var isolation = frame.getAsJsonObject("isolation");
                if (!isolation.get("enabled").getAsBoolean() || !isolation.get("sourcesClean").getAsBoolean()
                        || isolation.get("sourceAuditFailures").getAsInt() != 0
                        || !isolation.getAsJsonArray("unexpectedMobs").isEmpty()
                        || !isolation.getAsJsonArray("unexpectedSourceObjects").isEmpty()) return false;
                for (var element : frame.getAsJsonArray("tracks")) {
                    var track = element.getAsJsonObject();
                    if (!track.get("sense").getAsString().equals("VIBRATION") || track.get("travel").getAsDouble() != 0
                            || track.has("subject") && !track.get("subject").isJsonNull()) return false;
                }
                var position = frame.getAsJsonObject("playerPosition");
                double x = position.get("x").getAsDouble(), y = position.get("y").getAsDouble(), z = position.get("z").getAsDouble();
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                        || x < 16.3 || x > 19.7 || z < 3.3 || z > 7.7 || Math.abs(y + 44) > .05) return false;
                double distance = Math.hypot(x - previousX, z - previousZ); previousX = x; previousZ = z;
                if (distance > .6 || timer > 260 && distance > .025
                        || timer <= 80 && (Math.pow(x - 17.5, 2) + Math.pow(z - 5.5, 2) + Math.pow(y + 44, 2)) > .04) return false;
                boolean standing = standing(frame);
                if (distance > .00001) {
                    walkingStarted = true;
                    if (timer <= 80 || !frame.get("playerOnGround").getAsBoolean()) return false;
                }
                if (walkingStarted && !standing) return false;
                if (timer > 80 && !standing) startupPostureFrames++;
                if (frame.get("inconclusiveFrames").getAsInt() != startupPostureFrames) return false;
                if (timer > 80 && timer <= 240) walkingDistance += distance;
                byTick.put(frame.get("gameTime").getAsLong(), frame);
            }
            if (!walkingStarted || walkingDistance < 2 || !Double.isFinite(summary.get("walkingDistance").getAsDouble())
                    || Math.abs(walkingDistance - summary.get("walkingDistance").getAsDouble()) > .00001
                    || summary.get("inconclusiveFrames").getAsInt() != startupPostureFrames
                    || summary.get("complete").getAsBoolean() != (startupPostureFrames == 0)) return false;
            boolean measuredFootstep = false;
            for (var decision : decisions) {
                if (!decision.get("event").getAsString().equals("BLIND_VIBRATION")) continue;
                var frame = byTick.get(decision.get("tick").getAsLong()); String detail = decision.get("detail").getAsString();
                if (frame == null || !standing(frame) || !frame.get("playerOnGround").getAsBoolean()
                        || !detail.contains("kind=FOOTSTEP")) return false;
                if (detail.contains("outcome=DETECTED") || run == 30 && detail.contains("outcome=BELOW_THRESHOLD"))
                    measuredFootstep = true;
            }
            return measuredFootstep;
        } catch (RuntimeException malformed) { return false; }
    }

    private static boolean standing(JsonObject frame) {
        return frame.get("playerPose").getAsString().equals("STANDING") && !frame.get("playerSneaking").getAsBoolean();
    }
}
