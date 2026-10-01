package com.frozendawn.labbridge;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.UUID;

/** Wall-clock expiry is independent of frozen or sprinted Minecraft time. */
public record LabRequest(String id, String session, String world, long issuedAt, long expiresAt,
                         String operation, JsonObject args) {
    private static final Set<String> OPERATIONS = Set.of("snapshot", "maeve_status", "maeve_dump",
            "architect_dump", "scores", "reload", "function", "freeze", "unfreeze", "step", "sprint");

    public static LabRequest parse(JsonObject json) {
        if (json.get("schema").getAsInt() != 1) throw new IllegalArgumentException("Unsupported schema");
        String id = json.get("id").getAsString();
        if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException("Noncanonical request ID");
        return new LabRequest(id, json.get("session").getAsString(), json.get("world").getAsString(),
                json.get("issuedAt").getAsLong(), json.get("expiresAt").getAsLong(),
                json.get("operation").getAsString(), json.getAsJsonObject("args"));
    }

    public void validate(String activeSession, String activeWorld, long now) {
        if (!session.equals(activeSession) || !world.equals(activeWorld))
            throw new IllegalArgumentException("STALE_WORLD: refresh status; world/session changed");
        if (issuedAt > now + 1000 || expiresAt <= now || expiresAt <= issuedAt || expiresAt - issuedAt > 30000)
            throw new IllegalArgumentException("EXPIRED: requests have a maximum 30-second lifetime");
        if (!OPERATIONS.contains(operation)) throw new IllegalArgumentException("Operation is not allowed");
        if (args == null) throw new IllegalArgumentException("Missing arguments");
    }
}
