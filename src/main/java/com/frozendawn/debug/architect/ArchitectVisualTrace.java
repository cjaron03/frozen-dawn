package com.frozendawn.debug.architect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded, opt-in evidence. Never consulted by movement, targeting, or planning. Server thread only. */
public final class ArchitectVisualTrace {
    public static final int SAMPLE_INTERVAL = 5;
    public static final int FRAME_CAPACITY = 120;
    public static final int EVENT_CAPACITY = 512;
    public static final int TRAIL_CAPACITY = 64;
    private final ArrayDeque<ArchitectDebugSnapshot> frames = new ArrayDeque<>();
    private final ArrayDeque<ArchitectDebugSnapshot.Event> events = new ArrayDeque<>();
    private final ArrayDeque<ArchitectDebugSnapshot.Trail> trails = new ArrayDeque<>();
    private boolean enabled;
    private boolean sealed;
    private long startTick;
    private long droppedFrames;
    private long droppedEvents;
    private long capturedBreaks, capturedPlacements;
    private String runId = "";

    public void start(UUID id, long tick) {
        frames.clear(); events.clear(); trails.clear();
        runId = id.toString(); startTick = tick;
        droppedFrames = droppedEvents = capturedBreaks = capturedPlacements = 0;
        sealed = false;
    }

    public void setEnabled(boolean value) { enabled = value; }
    public boolean enabled() { return enabled; }
    public boolean sealed() { return sealed; }
    public long capturedBreaks() { return capturedBreaks; }
    public long capturedPlacements() { return capturedPlacements; }
    public void seal() { sealed = true; }
    public String runId() { return runId; }
    public long elapsed(long tick) { return Math.max(0, tick - startTick); }
    public ArchitectDebugSnapshot latest() { return frames.peekLast(); }
    public List<ArchitectDebugSnapshot> frames() { return List.copyOf(frames); }

    public boolean due(long tick) {
        return enabled && !sealed && (latest() == null || tick - latest().gameTick() >= SAMPLE_INTERVAL);
    }

    public void event(long tick, String kind, String detail, ArchitectDebugSnapshot.Point block, String reason) {
        if (!enabled || sealed) return;
        if (kind.equals("BREAK_END") && "DESTROYED".equals(detail)) capturedBreaks++;
        if (kind.equals("SCAFFOLD_PLACE")) capturedPlacements++;
        if (events.size() == EVENT_CAPACITY) { events.removeFirst(); droppedEvents++; }
        events.addLast(new ArchitectDebugSnapshot.Event(tick, elapsed(tick), kind, bounded(detail, 320), block, reason));
    }

    public List<ArchitectDebugSnapshot.Event> recentEvents() {
        List<ArchitectDebugSnapshot.Event> recent = new ArrayList<>();
        var iterator = events.descendingIterator();
        while (iterator.hasNext() && recent.size() < 10) {
            var event = iterator.next();
            if (!event.kind().equals("CANDIDATE") && !event.kind().equals("STATE")) recent.addFirst(event);
        }
        return List.copyOf(recent);
    }

    public List<ArchitectDebugSnapshot.Marker> candidates(long tick) {
        Map<ArchitectDebugSnapshot.Point, ArchitectDebugSnapshot.Marker> result = new LinkedHashMap<>();
        var iterator = events.descendingIterator();
        while (iterator.hasNext() && result.size() < 16) {
            var event = iterator.next();
            if (tick - event.gameTick() > 40) break;
            if (event.kind().equals("CANDIDATE") && event.block() != null) {
                result.putIfAbsent(event.block(), new ArchitectDebugSnapshot.Marker(
                        event.block(), "CANDIDATE", event.reason() + " " + event.detail()));
            }
        }
        return List.copyOf(result.values());
    }

    public List<ArchitectDebugSnapshot.Trail> trail(long tick, ArchitectDebugSnapshot.Point actor,
                                                   ArchitectDebugSnapshot.Point target) {
        if (!trails.isEmpty() && trails.getLast().gameTick() == tick) trails.removeLast();
        if (trails.size() == TRAIL_CAPACITY) trails.removeFirst();
        trails.addLast(new ArchitectDebugSnapshot.Trail(tick, actor, target));
        return List.copyOf(trails);
    }

    public void accept(ArchitectDebugSnapshot snapshot) {
        if (!enabled || (sealed && !frames.isEmpty())) return;
        if (!frames.isEmpty() && frames.getLast().gameTick() == snapshot.gameTick()) frames.removeLast();
        if (frames.size() == FRAME_CAPACITY) { frames.removeFirst(); droppedFrames++; }
        frames.addLast(snapshot);
    }

    /** Latest retained observation at or before this tick; never silently substitutes a newer frame. */
    public ArchitectDebugSnapshot atOrBefore(long gameTick) {
        var iterator = frames.descendingIterator();
        while (iterator.hasNext()) {
            var frame = iterator.next();
            if (frame.gameTick() <= gameTick) return frame;
        }
        return null;
    }

    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled); out.put("sealed", sealed);
        out.put("sampleIntervalTicks", SAMPLE_INTERVAL); out.put("frameCapacity", FRAME_CAPACITY);
        out.put("retainedFrames", frames.size()); out.put("droppedFrames", droppedFrames);
        out.put("capturedBreaks", capturedBreaks); out.put("capturedPlacements", capturedPlacements);
        out.put("retainedEvents", events.size()); out.put("droppedEvents", droppedEvents);
        out.put("firstGameTick", frames.isEmpty() ? null : frames.getFirst().gameTick());
        out.put("lastGameTick", frames.isEmpty() ? null : frames.getLast().gameTick());
        out.put("coordinates", "absolute world coordinates; gameTick and runTick identify each observation");
        return out;
    }

    public Map<String, String> files() {
        if (frames.isEmpty()) return Map.of();
        StringBuilder history = new StringBuilder();
        for (var frame : frames) history.append(frame.json()).append('\n');
        StringBuilder table = new StringBuilder("gameTick\trunTick\tevent\tblock\treason\tdetail\n");
        for (var event : events) table.append(event.gameTick()).append('\t').append(event.runTick()).append('\t')
                .append(cell(event.kind())).append('\t').append(cell(String.valueOf(event.block()))).append('\t')
                .append(cell(event.reason())).append('\t').append(cell(event.detail())).append('\n');
        return Map.of("visual-snapshot.json", latest().json(), "visual-history.jsonl", history.toString(),
                "visual-events.tsv", table.toString());
    }

    private static String bounded(String value, int max) { return value == null ? "" : value.substring(0, Math.min(max, value.length())); }
    private static String cell(String value) {
        return value == null ? "-" : value.replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }
}
