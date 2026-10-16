package com.frozendawn.labbridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LabPerformanceTraceTest {
    @TempDir Path root;
    private final AtomicLong time = new AtomicLong();
    private final StringWriter output = new StringWriter();
    private LabPerformanceTrace trace(int capacity, long limit) {
        return new LabPerformanceTrace(output, "test.jsonl", time::get, capacity, limit, false);
    }
    private void ms(long value) { time.set(value * 1_000_000); }
    private List<JsonObject> rows() {
        return output.toString().lines().map(s -> JsonParser.parseString(s).getAsJsonObject()).toList();
    }
    @Test void passiveEvidenceIsOwnedBoundedAndRejectedAfterClose() {
        var trace=trace(1,100000);var row=new JsonObject();row.addProperty("type","airlock_frame");row.addProperty("gas",79);
        trace.record(row);row.addProperty("gas",0);trace.record(row);
        assertEquals(1,trace.status().get("droppedEvents").getAsInt());trace.service();trace.close();trace.record(row);
        assertEquals(79,rows().getFirst().get("gas").getAsInt());
        assertEquals(2,rows().size());
    }
    @Test void fastTicksAndLongIdleNeverBecomeStalls() {
        var trace = trace(256, 100_000);
        trace.begin(1); ms(20); trace.end(); ms(50); trace.begin(2); ms(90); trace.end();
        ms(20_000); trace.service(); trace.close();
        assertTrue(rows().stream().noneMatch(r -> r.get("type").getAsString().matches("slow_tick|stall_sample")));
        assertEquals(2, trace.status().get("ticks").getAsInt());
        assertEquals(30, trace.status().get("meanWindowMs").getAsDouble());
        assertEquals(40, trace.status().get("maxWindowMs").getAsDouble());
    }
    @Test void stuckTickSamplesWithoutBridgeAndStopsAfterCompletionOrClose() {
        var trace = trace(256, 100_000); trace.begin(42);
        ms(249); trace.service(); assertTrue(rows().isEmpty());
        ms(250); trace.service(); ms(600); trace.service();
        ms(1250); trace.service(); ms(1500); trace.end();
        ms(30_000); trace.service(); trace.close(); trace.begin(43); trace.end();
        var samples = rows().stream().filter(r -> r.get("type").getAsString().equals("stall_sample")).toList();
        assertEquals(2, samples.size());
        for (var sample : samples) {
            assertEquals(42, sample.get("tick").getAsInt());
            assertTrue(sample.getAsJsonArray("stack").toString().contains("stuckTickSamplesWithoutBridge"));
        }
        var slow = rows().stream().filter(r -> r.get("type").getAsString().equals("slow_tick")).findFirst().orElseThrow();
        assertEquals(1500, slow.get("durationMs").getAsDouble());
        assertEquals(1, trace.status().get("slowTicks").getAsInt());
        assertEquals("closed", rows().getLast().get("type").getAsString());
    }
    @Test void watchdogCanReadAnotherBlockedThreadWithoutItsTaskQueue() throws Exception {
        var trace = trace(256, 100_000);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var server = new Thread(() -> {
            trace.begin(77); entered.countDown();
            try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            trace.end();
        }, "blocked-test-server");
        server.start();
        try {
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            ms(500); trace.service();
            var sample = rows().stream().filter(row -> row.get("type").getAsString().equals("stall_sample")).findFirst().orElseThrow();
            assertEquals("blocked-test-server", sample.get("thread").getAsString());
            assertEquals(77, sample.get("tick").getAsInt());
            assertFalse(sample.getAsJsonArray("stack").isEmpty());
        } finally { release.countDown(); server.join(2000); trace.close(); }
        assertFalse(server.isAlive());
        assertEquals(1, trace.status().get("ticks").getAsInt());
    }
    @Test void fullQueueDropsEvidenceRatherThanBlockingAndFileCapDisables() {
        var trace = trace(2, 100_000);
        for (int i = 0; i < 10; i++) { trace.begin(i); ms((i + 1) * 200); trace.end(); }
        assertEquals(8, trace.status().get("droppedEvents").getAsInt());
        trace.service(); trace.close();
        assertEquals(3, rows().size()); // Two retained ticks and explicit closed totals.
        var capped = trace(2, 1); capped.begin(1); ms(3000); capped.end(); capped.service();
        assertEquals("FILE_LIMIT_REACHED", capped.status().get("disabledReason").getAsString());
        assertEquals(0, capped.status().get("bytes").getAsInt()); capped.close();
    }
    @Test void blockedDiagnosticDiskNeverHoldsUpTickOrWorldClose() throws Exception {
        var writing = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var finished = new java.util.concurrent.CountDownLatch(1);
        var blockedDisk = new Writer() {
            public void write(char[] b, int off, int len) throws IOException {
                throw new IOException("disk failed");
            }
            public void flush() { }
            public void close() throws IOException {
                writing.countDown();
                try { release.await(); } catch (InterruptedException error) {
                    Thread.currentThread().interrupt(); throw new IOException(error);
                }
                finished.countDown();
            }
        };
        var trace = new LabPerformanceTrace(blockedDisk, "blocked", time::get, 2, 100_000, true);
        try {
            trace.begin(1); ms(200); trace.end();
            assertTrue(writing.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> {
                trace.begin(2); ms(400); trace.end(); trace.close();
            });
        } finally { release.countDown(); trace.close(); }
        assertTrue(finished.await(2, java.util.concurrent.TimeUnit.SECONDS));
    }
    @Test void ioFailureIsContainedAndSessionFilesCannotBeOverwritten() throws Exception {
        var broken = new Writer() {
            public void write(char[] b, int off, int len) throws IOException { throw new IOException("test disk unavailable"); }
            public void flush() { }
            public void close() { }
        };
        var trace = new LabPerformanceTrace(broken, "bad", time::get, 2, 100_000, false);
        trace.begin(1); ms(200); trace.end(); assertDoesNotThrow(trace::service);
        assertTrue(trace.status().get("disabledReason").getAsString().contains("test disk unavailable"));
        assertDoesNotThrow(trace::close);
        Files.writeString(root.resolve("same.jsonl"), "earlier replay\n");
        assertThrows(IOException.class, () -> LabPerformanceTrace.start(root, "same", "world"));
        assertEquals("earlier replay\n", Files.readString(root.resolve("same.jsonl")));
    }
}
