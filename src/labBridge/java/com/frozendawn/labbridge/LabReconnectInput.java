package com.frozendawn.labbridge;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/** Remote input has its own sample time; the published host tick can briefly lag during catch-up. */
public final class LabReconnectInput {
    private static final String GUEST = "347c4be4-4dda-3d32-a649-477c0bff527c";
    public static final long MAX_SAMPLE_AGE_MS = 250, MAX_ANCHOR_AGE_MS = 250, MAX_HOST_TICK_LAG = 8;
    private LabReconnectInput() {}

    public static JsonObject assess(JsonObject sample, String session, int port, long now, long millis) {
        var out = new JsonObject();
        out.addProperty("evaluatedGameTime", now);
        out.addProperty("evaluatedAtMillis", millis);
        out.add("sample", sample == null ? JsonNull.INSTANCE : sample.deepCopy());
        String reason = "GUEST_INPUT_SAMPLE_MISSING";
        try {
            if (sample != null) {
                boolean identity = sample.get("captureSession").getAsString().equals(session)
                        && sample.get("player").getAsString().equals(GUEST)
                        && sample.get("playerName").getAsString().equals("ArchitectGuest")
                        && sample.get("remoteConnection").getAsBoolean()
                        && sample.get("port").getAsInt() == port
                        && sample.get("clockSource").getAsString().equals("HOST_FILE_POST_TICK");
                long tick = sample.get("gameTime").getAsLong();
                long sampled = sample.get("sampledAtMillis").getAsLong();
                long anchor = sample.get("hostSampledAtMillis").getAsLong();
                long tickLag = now - tick, sampleAge = millis - sampled, anchorAge = millis - anchor;
                boolean ordered = now >= 0 && tick >= 0 && tick <= now && anchor >= 0
                        && sampled >= anchor && millis >= sampled;
                boolean fresh = ordered && tickLag <= MAX_HOST_TICK_LAG
                        && sampleAge <= MAX_SAMPLE_AGE_MS && anchorAge <= MAX_ANCHOR_AGE_MS;
                boolean home = sample.get("health").getAsFloat() == 20 && sample.get("onGround").getAsBoolean()
                        && Math.abs(sample.get("x").getAsDouble() - 18.5) < .000001
                        && Math.abs(sample.get("y").getAsDouble() + 44) < .000001
                        && Math.abs(sample.get("z").getAsDouble() - 5.5) < .000001;
                boolean quiet = sample.get("focused").getAsBoolean() && !sample.get("screenOpen").getAsBoolean()
                        && !sample.get("paused").getAsBoolean() && !sample.get("sneaking").getAsBoolean()
                        && !sample.get("flying").getAsBoolean() && !sample.get("useDown").getAsBoolean()
                        && !sample.get("attackDown").getAsBoolean()
                        && !sample.getAsJsonObject("mapped").entrySet().stream().anyMatch(e -> e.getValue().getAsBoolean())
                        && sample.getAsJsonObject("windowKeys").keySet().equals(java.util.Set.of(
                                "340", "344", "65", "68", "87", "83", "32", "341", "345", "mouse0", "mouse1", "mouse2"))
                        && !sample.getAsJsonObject("windowKeys").entrySet().stream().anyMatch(e -> e.getValue().getAsBoolean())
                        && sample.getAsJsonObject("input").get("forwardImpulse").getAsDouble() == 0
                        && sample.getAsJsonObject("input").get("leftImpulse").getAsDouble() == 0;
                out.addProperty("identityMatches", identity);
                out.addProperty("clockOrdered", ordered);
                out.addProperty("clockFresh", fresh);
                out.addProperty("homeHealthyGrounded", home);
                out.addProperty("quiet", quiet);
                out.addProperty("hostTickLag", tickLag);
                out.addProperty("sampleAgeMillis", sampleAge);
                out.addProperty("anchorAgeMillis", anchorAge);
                reason = !identity ? "GUEST_INPUT_IDENTITY_CHANGED" : !ordered ? "GUEST_INPUT_CLOCK_INVALID"
                        : !fresh ? "GUEST_INPUT_CLOCK_DELAYED" : !home ? "GUEST_INPUT_SCENE_CHANGED"
                        : !quiet ? "GUEST_INPUT_NOT_QUIET" : null;
            }
        } catch (RuntimeException malformed) {
            reason = "GUEST_INPUT_SAMPLE_INVALID";
        }
        out.addProperty("accepted", reason == null);
        out.addProperty("reason", reason);
        return out;
    }
}
