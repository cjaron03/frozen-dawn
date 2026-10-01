package com.frozendawn.labbridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LabBridgeTest {
    @TempDir Path root;
    private JsonObject request(String id) {
        JsonObject value = new JsonObject(); value.addProperty("schema", 1); value.addProperty("id", id);
        value.addProperty("session", "session"); value.addProperty("world", "world");
        value.addProperty("issuedAt", 1000); value.addProperty("expiresAt", 31000);
        value.addProperty("operation", "freeze"); value.add("args", new JsonObject()); return value;
    }
    @Test void staleWorldExpiredAndUnlistedCommandsCannotExecute() {
        var json = request(UUID.randomUUID().toString()); var req = LabRequest.parse(json);
        assertDoesNotThrow(() -> req.validate("session", "world", 2000));
        assertThrows(IllegalArgumentException.class, () -> req.validate("new-session", "world", 2000));
        assertThrows(IllegalArgumentException.class, () -> req.validate("session", "other-world", 2000));
        assertThrows(IllegalArgumentException.class, () -> req.validate("session", "world", 31000));
        json.addProperty("operation", "command");
        assertThrows(IllegalArgumentException.class, () -> LabRequest.parse(json).validate("session", "world", 2000));
        json.addProperty("expiresAt", 40000);
        assertThrows(IllegalArgumentException.class, () -> LabRequest.parse(json).validate("session", "world", 2000));
    }
    @Test void filesystemClaimsSurviveRestartAndDoNotRepeatMutations() throws Exception {
        String id = UUID.randomUUID().toString(); var request = request(id); AtomicInteger calls = new AtomicInteger();
        var endpoint = new LabInbox.Endpoint() {
            public CompletableFuture<JsonObject> status() { return CompletableFuture.completedFuture(new JsonObject()); }
            public CompletableFuture<JsonObject> execute(LabRequest req) {
                calls.incrementAndGet(); return CompletableFuture.completedFuture(new JsonObject());
            }
        };
        try (LabInbox inbox = new LabInbox(root)) {
            inbox.bind(endpoint); Files.writeString(root.resolve("requests/" + id + ".json"), request.toString()); inbox.poll();
            assertEquals(1, calls.get());
            assertTrue(JsonParser.parseString(Files.readString(root.resolve("responses/" + id + ".json"))).getAsJsonObject().get("ok").getAsBoolean());
            Files.writeString(root.resolve("requests/" + id + ".json"), request.toString()); inbox.poll(); assertEquals(1, calls.get());
        }
        // Simulate the crash window: claimed persisted, response lost. A replacement client must not replay it.
        Files.delete(root.resolve("responses/" + id + ".json"));
        try (LabInbox inbox = new LabInbox(root)) {
            inbox.bind(endpoint); Files.writeString(root.resolve("requests/" + id + ".json"), request.toString()); inbox.poll();
            assertEquals(1, calls.get()); assertFalse(Files.exists(root.resolve("responses/" + id + ".json")));
        }
    }
    @Test void noWorldRejectsRequestsRatherThanQueueingForNextWorld() throws Exception {
        String id = UUID.randomUUID().toString();
        try (LabInbox inbox = new LabInbox(root)) {
            Files.writeString(root.resolve("requests/" + id + ".json"), request(id).toString()); inbox.poll();
            var response = JsonParser.parseString(Files.readString(root.resolve("responses/" + id + ".json"))).getAsJsonObject();
            assertFalse(response.get("ok").getAsBoolean());
        }
    }
    @Test void policyRequiresExactWorldStageAndFunction() throws Exception {
        Path policy = root.resolve("policy.json");
        Files.writeString(policy, """
                {"schema":1,"functions":{"lab:next":{"world":"Test","scores":[{"objective":"test","holder":"#stage","equals":2}]}}}
                """);
        assertDoesNotThrow(() -> LabFunctionPolicy.check(policy, "lab:next", "Test", (o, h) -> 2));
        assertThrows(IllegalArgumentException.class, () -> LabFunctionPolicy.check(policy, "lab:next", "Other", (o, h) -> 2));
        assertThrows(IllegalArgumentException.class, () -> LabFunctionPolicy.check(policy, "lab:next", "Test", (o, h) -> 3));
        assertThrows(IllegalArgumentException.class, () -> LabFunctionPolicy.check(policy, "lab:next", "Test", (o, h) -> null));
        assertThrows(IllegalArgumentException.class, () -> LabFunctionPolicy.check(policy, "lab:reset", "Test", (o, h) -> 2));
        assertThrows(IllegalArgumentException.class, () -> LabFunctionPolicy.check(policy, "lab:next\nkill @a", "Test", (o, h) -> 2));
    }
}
