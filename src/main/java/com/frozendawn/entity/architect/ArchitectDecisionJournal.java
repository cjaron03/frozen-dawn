package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-thread only. Transient diagnostics, never an input to the AI. */
public final class ArchitectDecisionJournal {
    public static final int CAPACITY = 400;
    private int capacity = CAPACITY;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private final Map<String, Long> eventCounts = new LinkedHashMap<>();
    private boolean enabled = Boolean.getBoolean("frozendawn.debug.architect");
    private UUID runId = UUID.randomUUID();
    private long startTick;
    private long endTick;
    private long breakBaseline;
    private long breaks;
    private BlockPos origin;
    private Rotation rotation = Rotation.NONE;
    private long dropped;
    private String outcome = "RECORDING";
    private String outcomeReason = "";

    public record Entry(long tick, String event, String action, BlockPos pos,
                        @Nullable BlockPos step, String stepType, @Nullable BlockPos waypoint,
                        @Nullable BreakChoice choice, int noProgress, int blacklist, int reinits,
                        long breaks, String detail) { }

    public boolean enabled() { return enabled; }
    public long dropped() { return dropped; }
    public long breaks() { return breaks; }
    public long elapsed(long tick) { return Math.max(0, tick - startTick); }
    public UUID runId() { return runId; }
    public List<Entry> entries() { return List.copyOf(entries); }
    public Map<String, Long> eventCounts() { return Map.copyOf(eventCounts); }
    public boolean heartbeatDue(long tick, int interval) {
        return enabled && origin != null && elapsed(tick) % interval == 0;
    }

    public void start(long tick, BlockPos pos) { start(UUID.randomUUID(), tick, pos, 0, Rotation.NONE); }

    public void start(UUID id, long tick, BlockPos pos, long lifetimeBreaks, Rotation orientation) {
        entries.clear();
        capacity = CAPACITY;
        eventCounts.clear();
        runId = id;
        startTick = endTick = tick;
        breakBaseline = lifetimeBreaks;
        breaks = dropped = 0;
        origin = pos.immutable();
        rotation = orientation;
        outcome = "RECORDING";
        outcomeReason = "";
        enabled = true;
    }

    /** Larger but still bounded capture for explicitly prepared stress labs. */
    public void useEnduranceLabBuffer() { capacity = 65536; }

    public void useExtendedLabBuffer() { capacity = 4096; }

    public void stop() { enabled = false; }

    public void finish(long tick, String status, String reason) {
        endTick = tick;
        outcome = status;
        outcomeReason = reason;
        stop();
    }

    public void append(Entry entry) {
        if (!enabled) return;
        if (origin == null) {
            origin = entry.pos().immutable();
            startTick = entry.tick();
        }
        endTick = entry.tick();
        breaks = Math.max(0, entry.breaks() - breakBaseline);
        eventCounts.merge(entry.event(), 1L, Long::sum);
        if (entries.size() == capacity) { entries.removeFirst(); dropped++; }
        entries.addLast(entry);
    }

    /** UUIDs and absolute coordinates live in the summary, outside the comparable table. */
    public String tsv() {
        StringBuilder out = new StringBuilder("tick\tevent\taction\tpos\tstep\tstepType\twaypoint\tbreakTarget\treason\tnoProg\tblacklist\treinits\tbreaks\tdetail\n");
        for (Entry e : entries) {
            out.append(e.tick() - startTick).append('\t').append(cell(e.event())).append('\t')
                .append(cell(e.action())).append('\t').append(relative(e.pos())).append('\t')
                .append(relative(e.step())).append('\t').append(cell(e.stepType())).append('\t')
                .append(relative(e.waypoint())).append('\t')
                .append(relative(e.choice() == null ? null : e.choice().pos())).append('\t')
                .append(e.choice() == null ? "-" : e.choice().reason()).append('\t')
                .append(e.noProgress()).append('\t').append(e.blacklist()).append('\t')
                .append(e.reinits()).append('\t').append(Math.max(0, e.breaks() - breakBaseline)).append('\t')
                .append(cell(e.detail())).append('\n');
        }
        return out.toString();
    }

    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema", 2);
        out.put("runId", runId.toString());
        out.put("outcome", outcome);
        out.put("reason", outcomeReason);
        out.put("startGameTime", startTick);
        out.put("elapsedTicks", elapsed(endTick));
        out.put("origin", origin == null ? null : List.of(origin.getX(), origin.getY(), origin.getZ()));
        out.put("rotation", rotation.name());
        out.put("capacity", capacity);
        out.put("retained", entries.size());
        out.put("dropped", dropped);
        out.put("traceComplete", dropped == 0);
        out.put("destroyedBlocks", breaks);
        out.put("events", new LinkedHashMap<>(eventCounts));
        return out;
    }

    public String relative(@Nullable BlockPos p) {
        if (p == null || origin == null) return "-";
        Rotation inverse = switch (rotation) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> rotation;
        };
        BlockPos local = p.subtract(origin).rotate(inverse);
        return local.getX() + "," + local.getY() + "," + local.getZ();
    }
    private static String cell(String s) {
        return s == null ? "-" : s.replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }
}
