package com.frozendawn.labbridge;

import com.google.gson.JsonObject;
import java.util.List;

/** Qualifies retained independent clocks, without padding rows or changing old replay outcomes. */
public final class LabInputTimeline {
    private LabInputTimeline() { }
    public static boolean qualifies(JsonObject start, JsonObject summary, List<JsonObject> server,
            List<JsonObject> client, List<JsonObject> keys) {
        try {
            int limit = start.get("serverTickLimit").getAsInt();
            if (limit != 480 || !summary.get("captureComplete").getAsBoolean()
                    || summary.get("active").getAsBoolean() || summary.get("dropped").getAsInt() != 0
                    || !summary.get("reason").getAsString().equals("TIME_LIMIT")
                    || summary.get("serverTickLimit").getAsInt() != limit
                    || server.size() != limit || summary.get("serverFrames").getAsInt() != limit
                    || client.size() < limit - 1 || client.size() > 1024
                    || summary.get("clientFrames").getAsInt() != client.size()
                    || summary.get("keyEvents").getAsInt() != keys.size() || keys.size() > 256
                    || !start.get("session").equals(summary.get("session"))
                    || !start.get("player").equals(summary.get("player"))
                    || !start.get("probe").equals(summary.get("probe"))) return false;
            long first = server.getFirst().get("gameTime").getAsLong();
            long lastMillis = Long.MIN_VALUE;
            for (int i = 0; i < server.size(); i++) {
                var row = server.get(i); var players = row.getAsJsonArray("players"); var actors = row.getAsJsonArray("architects");
                boolean ended = i == limit - 1;
                if (!row.get("session").equals(start.get("session"))
                        || row.get("viewRun").getAsInt() != start.get("viewRun").getAsInt()
                        || row.get("viewTimer").getAsInt() != i + 1 || row.get("gameTime").getAsLong() != first + i
                        || row.get("viewStage").getAsInt() != (ended ? 3 : 2)
                        || players.size() != 1 || actors.size() != 1
                        || !players.get(0).getAsJsonObject().get("uuid").equals(start.get("player"))
                        || !actors.get(0).getAsJsonObject().get("uuid").equals(start.get("actor"))
                        || actors.get(0).getAsJsonObject().get("noAI").getAsBoolean() != ended
                        || !players.get(0).getAsJsonObject().get("gameMode").getAsString().equals(ended ? "creative" : "survival")
                        || row.get("sampledAtMillis").getAsLong() < lastMillis) return false;
                lastMillis = row.get("sampledAtMillis").getAsLong();
            }
            long firstClient = client.getFirst().get("gameTime").getAsLong();
            long lastClient = client.getLast().get("gameTime").getAsLong();
            // One start/end boundary callback may lie on either side of the server callback.
            // Continuous client player ticks and bounded world-time gaps rule out a sparse record.
            if (Math.abs(firstClient - first) > 2 || Math.abs(lastClient - (first + limit - 1)) > 2) return false;
            long tick = client.getFirst().get("playerTick").getAsLong() - 1, worldTick = firstClient, millis = Long.MIN_VALUE;
            for (var row : client) {
                long time = row.get("gameTime").getAsLong();
                if (!identity(start, row) || row.get("playerTick").getAsLong() != ++tick
                        || time < worldTick || time - worldTick > 2 || row.get("sampledAtMillis").getAsLong() < millis) return false;
                worldTick = time; millis = row.get("sampledAtMillis").getAsLong();
            }
            millis = Long.MIN_VALUE;
            for (var row : keys) {
                int key = row.get("key").getAsInt(), action = row.get("action").getAsInt();
                if (!identity(start, row) || (action != 0 && action != 1)
                        || (key != 340 && key != 344 && key != 65 && key != 68 && key != 87 && key != 83)
                        || row.get("gameTime").getAsLong() < first - 2 || row.get("gameTime").getAsLong() > first + limit + 1
                        || row.get("sampledAtMillis").getAsLong() < millis) return false;
                millis = row.get("sampledAtMillis").getAsLong();
            }
            return true;
        } catch (RuntimeException malformed) { return false; }
    }
    private static boolean identity(JsonObject start, JsonObject row) {
        return start.get("session").equals(row.get("session")) && start.get("player").equals(row.get("player"))
                && row.get("focused").getAsBoolean() && !row.get("paused").getAsBoolean() && !row.get("screenOpen").getAsBoolean();
    }
}
