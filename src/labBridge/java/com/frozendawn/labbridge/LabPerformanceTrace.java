package com.frozendawn.labbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Lab-only, read-only server-tick evidence. The watchdog never reads Minecraft state. */
public final class LabPerformanceTrace implements AutoCloseable {
    private static final long SLOW = 100_000_000L, STALL = 250_000_000L;
    private static final long SAMPLE_INTERVAL = 1_000_000_000L, SUMMARY_INTERVAL = 5_000_000_000L;
    private static final long FILE_LIMIT = 16L * 1024 * 1024;
    private record Active(Thread thread, long started, long tick) { }
    private record Totals(long ticks, long slow, long nanos, long max) { }
    private final ArrayBlockingQueue<JsonObject> queue;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final LongSupplier clock;
    private final Writer writer;
    private final String path;
    private final long limit;
    private final ScheduledExecutorService worker;
    private volatile Active active;
    private volatile Totals totals = new Totals(0, 0, 0, 0);
    private volatile String disabledReason;
    private volatile long bytes;
    private long lastSample = Long.MIN_VALUE, lastSummary;

    public static LabPerformanceTrace start(Path directory, String session, String world) throws IOException {
        Files.createDirectories(directory);
        Path path = directory.resolve(session + ".jsonl");
        var trace = new LabPerformanceTrace(Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), path.toAbsolutePath().toString(),
                System::nanoTime, 256, FILE_LIMIT, true);
        var row = event("session"); row.addProperty("session", session); row.addProperty("world", world);
        row.addProperty("window", "NeoForge ServerTick Pre HIGHEST through Post LOWEST; excludes work outside those events");
        row.addProperty("slowThresholdMs", 100); row.addProperty("stallThresholdMs", 250);
        row.addProperty("stackIntervalMs", 1000); row.addProperty("maxBytes", FILE_LIMIT);
        trace.offer(row);
        return trace;
    }

    /** A fake clock and synchronous service keep evidence tests deterministic. */
    LabPerformanceTrace(Writer writer, String path, LongSupplier clock, int capacity, long limit, boolean background) {
        this.writer = writer; this.path = path; this.clock = clock; this.limit = limit;
        queue = new ArrayBlockingQueue<>(capacity); lastSummary = clock.getAsLong();
        worker = background ? Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "Frozen Dawn lab slow-tick watchdog"); thread.setDaemon(true); return thread;
        }) : null;
        if (worker != null) worker.scheduleWithFixedDelay(this::service, 100, 100, TimeUnit.MILLISECONDS);
    }

    public void begin(long tick) {
        if (!closed.get() && disabledReason == null) active = new Active(Thread.currentThread(), clock.getAsLong(), tick);
    }

    public void end() {
        var sample = active; active = null;
        if (sample == null || closed.get() || disabledReason != null) return;
        long elapsed = Math.max(0, clock.getAsLong() - sample.started);
        var previous = totals;
        totals = new Totals(previous.ticks + 1, previous.slow + (elapsed >= SLOW ? 1 : 0),
                previous.nanos + elapsed, Math.max(previous.max, elapsed));
        if (elapsed >= SLOW) {
            var row = event("slow_tick"); row.addProperty("tick", sample.tick);
            row.addProperty("durationMs", elapsed / 1_000_000.0); offer(row);
        }
    }

    public JsonObject status() {
        var value = totals; var out = new JsonObject(); out.addProperty("path", path);
        out.addProperty("ticks", value.ticks); out.addProperty("slowTicks", value.slow);
        out.addProperty("meanWindowMs", value.ticks == 0 ? 0 : value.nanos / 1_000_000.0 / value.ticks);
        out.addProperty("maxWindowMs", value.max / 1_000_000.0); out.addProperty("droppedEvents", dropped.get());
        out.addProperty("bytes", bytes); out.addProperty("closed", closed.get());
        if (disabledReason != null) out.addProperty("disabledReason", disabledReason);
        return out;
    }

    private static JsonObject event(String type) {
        var row = new JsonObject(); row.addProperty("type", type);
        row.addProperty("recordedAt", Instant.now().toString()); return row;
    }
    private void offer(JsonObject row) { if (!queue.offer(row)) dropped.incrementAndGet(); }

    /** Runs on the daemon, independently of the command bridge and server task queue. */
    void service() {
        if (disabledReason != null) return;
        try {
            long now = clock.getAsLong(); var sample = active;
            if (sample != null && now - sample.started >= STALL
                    && (lastSample == Long.MIN_VALUE || now - lastSample >= SAMPLE_INTERVAL)) {
                var stack = sample.thread.getStackTrace();
                // A completed/replaced tick must never inherit another tick's stack.
                if (active == sample && !closed.get()) {
                    var row = event("stall_sample"); row.addProperty("tick", sample.tick);
                    row.addProperty("elapsedMs", (now - sample.started) / 1_000_000.0);
                    row.addProperty("thread", sample.thread.getName()); row.addProperty("state", sample.thread.getState().name());
                    var frames = new JsonArray();
                    for (int i = 0; i < Math.min(128, stack.length); i++) frames.add(stack[i].toString());
                    row.add("stack", frames); offer(row); lastSample = now;
                }
            }
            if (now - lastSummary >= SUMMARY_INTERVAL) {
                var row = event("summary"); row.add("totals", status()); offer(row); lastSummary = now;
            }
            drain(); if (disabledReason == null) writer.flush();
        } catch (Exception error) { disable("IO_OR_SAMPLING_FAILURE: " + error); }
    }

    private void drain() throws IOException {
        JsonObject row;
        while ((row = queue.poll()) != null) {
            String line = row + "\n"; int size = line.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > limit) { disable("FILE_LIMIT_REACHED"); return; }
            writer.write(line); bytes += size;
        }
    }
    private void disable(String reason) {
        disabledReason = reason; active = null; queue.clear();
        if (worker != null) worker.shutdown();
        try { writer.close(); } catch (IOException ignored) { }
        System.err.println("[LabPerformance] " + reason + " (" + path + ")");
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        active = null;
        Runnable finish = () -> {
            if (disabledReason != null) return;
            try {
                drain(); var row = event("closed"); row.add("totals", status()); offer(row);
                drain(); writer.close();
            } catch (IOException error) { disable("CLOSE_FAILURE: " + error); }
        };
        // Server shutdown never waits on diagnostic disk I/O.
        if (worker == null) finish.run();
        else {
            try { worker.execute(finish); }
            catch (java.util.concurrent.RejectedExecutionException ignored) {
                // A concurrent failure/cap already shut down the sole writer and closed its sink.
            } finally { worker.shutdown(); }
        }
    }
}
