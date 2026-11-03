package com.frozendawn.labbridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LabInputCaptureTest {
    @TempDir Path root;
    private JsonObject row(long tick) {
        var row = new JsonObject(); row.addProperty("session", "session");
        row.addProperty("player", "player"); row.addProperty("gameTime", tick); return row;
    }
    private LabInputCapture capture() throws Exception { return new LabInputCapture(root, "session", "player", row(100)); }
    @Test void streamsRejectStaleIdentityAndRepeatedServerTicks() throws Exception {
        var capture = capture(); var stale = row(100); stale.addProperty("session", "other");
        capture.client(stale, false); capture.server(stale);
        stale = row(100); stale.addProperty("player", "other"); capture.client(stale, false);
        assertEquals(0, capture.status().get("clientFrames").getAsInt());
        assertEquals(0, capture.status().get("serverFrames").getAsInt());
        assertFalse(capture.server(row(100))); assertFalse(capture.server(row(100))); assertFalse(capture.server(row(99)));
        assertEquals(1, capture.status().get("serverFrames").getAsInt());
    }
    @Test void completedFilesRetainIndependentSnapshotsAndVerifiedHashesAcrossRuns() throws Exception {
        var capture = capture(); var mutable = row(100); mutable.addProperty("crouch", true);
        capture.client(mutable, false); mutable.addProperty("crouch", false);
        for (int tick = 100; tick < 340; tick++) assertEquals(tick == 339, capture.server(row(tick)));
        var status = capture.status(); assertTrue(status.get("captureComplete").getAsBoolean());
        var directory = Path.of(status.get("directory").getAsString());
        assertTrue(JsonParser.parseString(Files.readString(directory.resolve("client.jsonl"))).getAsJsonObject().get("crouch").getAsBoolean());
        for (var hash : status.getAsJsonObject("hashes").entrySet()) assertEquals(hash.getValue().getAsString(),
                HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(directory.resolve(hash.getKey())))));
        String original = Files.readString(directory.resolve("summary.json"));
        assertNotEquals(status.get("directory"), capture().status().get("directory"));
        assertEquals(original, Files.readString(directory.resolve("summary.json")));
    }
    @Test void missingClientAndClosedWorldCannotClaimACompleteCapture() throws Exception {
        var missing = capture(); for (int tick = 100; tick < 340; tick++) missing.server(row(tick));
        assertFalse(missing.status().get("captureComplete").getAsBoolean());
        var partial = capture(); partial.client(row(100), false); partial.server(row(100)); partial.finish("WORLD_CLOSED");
        partial.client(row(101), false); partial.server(row(101)); partial.finish("TIME_LIMIT");
        assertEquals("WORLD_CLOSED", partial.status().get("reason").getAsString());
        assertEquals(1, partial.status().get("clientFrames").getAsInt());
        assertEquals(1, partial.status().get("serverFrames").getAsInt());
        assertFalse(partial.status().get("captureComplete").getAsBoolean());
    }
    @Test void overflowCannotGrowUnboundedOrSilentlyPass() throws Exception {
        var capture = capture();
        for (int i = 0; i < 1100; i++) capture.client(row(i), false);
        for (int i = 0; i < 300; i++) {
            var edge = row(i); edge.addProperty("action", i % 2); edge.addProperty("key", 65); capture.client(edge, true);
        }
        for (int tick = 100; tick < 340; tick++) capture.server(row(tick));
        assertEquals(1024, capture.status().get("clientFrames").getAsInt());
        assertEquals(256, capture.status().get("keyEvents").getAsInt());
        assertEquals(120, capture.status().get("dropped").getAsInt());
        assertFalse(capture.status().get("captureComplete").getAsBoolean());
    }
    @Test void walkingCaptureKeepsRecordingPastStationaryLimitAndStopsAt480() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new LabInputCapture(root, "session", "player", row(100), 481));
        var capture = new LabInputCapture(root, "session", "player", row(100), 480);
        for (int tick = 100; tick < 580; tick++) {
            capture.client(row(tick), false);
            assertEquals(tick == 579, capture.server(row(tick)));
            if (tick == 339) assertTrue(capture.active());
        }
        capture.client(row(580), false); capture.server(row(580));
        assertEquals(480, capture.status().get("serverFrames").getAsInt());
        assertEquals(480, capture.status().get("clientFrames").getAsInt());
        assertTrue(capture.status().get("captureComplete").getAsBoolean());
    }
    @Test void heldKeyRepeatsCannotDisplacePressAndReleaseEdges() throws Exception {
        var capture = new LabInputCapture(root, "session", "player", row(100), 480);
        for (int i = 0; i < 16; i++) {
            var edge = row(i); edge.addProperty("action", i % 2); edge.addProperty("key", 65);
            capture.client(edge, true);
            for (int j = 0; j < 100; j++) {
                var repeat = row(i); repeat.addProperty("action", 2); repeat.addProperty("key", 65);
                capture.client(repeat, true);
            }
        }
        var stale = row(100); stale.addProperty("session", "other"); stale.addProperty("action", 2);
        capture.client(stale, true);
        for (int tick = 100; tick < 580; tick++) { capture.client(row(tick), false); capture.server(row(tick)); }
        var status = capture.status();
        assertEquals(16, status.get("keyEvents").getAsInt());
        assertEquals(1600, status.get("keyRepeatsIgnored").getAsInt());
        assertEquals(0, status.get("dropped").getAsInt());
        assertTrue(status.get("captureComplete").getAsBoolean());
        var edges = Files.readAllLines(Path.of(status.get("directory").getAsString()).resolve("keys.jsonl"));
        assertEquals(16, edges.size());
        for (int i = 0; i < 16; i++) assertEquals(i % 2, JsonParser.parseString(edges.get(i)).getAsJsonObject().get("action").getAsInt());
    }

}
