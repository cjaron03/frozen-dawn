package com.frozendawn.debug.architect;

import com.frozendawn.debug.architect.ArchitectDebugSnapshot.*;
import com.frozendawn.entity.architect.ArchitectDecisionJournal;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectVisualTraceTest {
    @TempDir Path directory;

    private static ArchitectDebugSnapshot frame(long tick, String runId) {
        var point = new Point(1.25, 64.5, -2.75);
        return new ArchitectDebugSnapshot(1, tick, tick, runId, "minecraft:overworld", true, Lab.field(),
                new Body(42, "actor", "Architect", point, new Point(0, 0, 0), new Box(1, 64.5, -3, 2, 67, -2),
                        100, 90, 92, 89, true, false, point, Route.empty()), null,
                new Planner("SEARCHING", 25, 4, point, null, "NONE", null, List.of(point), 0, 0, 1, null, null),
                new Combat(-1, -1, -1, 2.8, false, false, false, -1, false, "NO_TARGET", 0),
                new Mining(null, "NONE", 0, 0), new Recovery("APPROACH", 4, 0, 0, 0, 0, 0, 0),
                List.of(new Shape("minecraft:stone_slab", new Box(1, 64, -3, 2, 64.5, -2))), false,
                List.of(), List.of(new Trail(tick, point, null)), List.of());
    }

    @Test void captureIsOptInBoundedAndDoesNotSubstituteNewerHistory() {
        var trace = new ArchitectVisualTrace();
        trace.start(UUID.randomUUID(), 0);
        trace.accept(frame(0, trace.runId()));
        assertNull(trace.latest());
        trace.setEnabled(true);
        for (int i = 0; i < 150; i++) trace.accept(frame(i * 5, trace.runId()));
        assertEquals(120, trace.frames().size());
        assertEquals(30L, trace.summary().get("droppedFrames"));
        assertNull(trace.atOrBefore(149));
        assertEquals(150, trace.atOrBefore(154).gameTick());
        assertFalse(trace.due(749));
        assertTrue(trace.due(750));
        var replacement = frame(745, trace.runId());
        trace.accept(replacement);
        assertSame(replacement, trace.latest());
        assertEquals(120, trace.frames().size());
        trace.setEnabled(false);
        trace.accept(frame(750, trace.runId()));
        assertSame(replacement, trace.latest());
    }

    @Test void completionPreservesTerminalFrameAndResetClearsOldEvidence() {
        var trace = new ArchitectVisualTrace();
        trace.setEnabled(true);
        trace.start(UUID.randomUUID(), 10);
        var terminal = frame(20, trace.runId());
        trace.accept(terminal);
        trace.seal();
        trace.accept(frame(25, trace.runId()));
        trace.event(25, "MARK", "too late", null, "");
        assertSame(terminal, trace.latest());
        assertTrue(trace.recentEvents().isEmpty());
        UUID next = UUID.randomUUID();
        trace.start(next, 40);
        assertTrue(trace.enabled());
        assertFalse(trace.sealed());
        assertNull(trace.latest());
        assertEquals(next.toString(), trace.runId());
        assertEquals(2, trace.elapsed(42));
        // A debugger enabled only after completion may show one honest post-completion observation.
        trace.seal();
        trace.accept(frame(42, trace.runId()));
        assertEquals(42, trace.latest().gameTick());
    }

    @Test void eventRetentionEscapesUserMarkersAndExpiresCandidates() {
        var trace = new ArchitectVisualTrace();
        trace.start(UUID.randomUUID(), 0);
        trace.setEnabled(true);
        for (int i = 0; i < 600; i++) trace.event(i, "CANDIDATE", "x", new Point(i, 0, 0), "BLOCKED");
        trace.event(600, "MARK", "tab\tline\nnext\rrow", null, "");
        trace.accept(frame(600, trace.runId()));
        assertEquals(89L, trace.summary().get("droppedEvents"));
        assertEquals(16, trace.candidates(600).size());
        assertTrue(trace.candidates(650).isEmpty());
        assertEquals(1, trace.recentEvents().size());
        var lines = trace.files().get("visual-events.tsv").split("\n");
        assertEquals(513, lines.length);
        for (var row : lines) assertEquals(6, row.split("\t", -1).length);
    }

    @Test void exportPreservesExactSnapshotAndHashesEveryVisualAttachment() throws Exception {
        var journal = new ArchitectDecisionJournal();
        journal.start(0, BlockPos.ZERO);
        journal.visual().setEnabled(true);
        var snapshot = frame(5, journal.runId().toString());
        journal.visual().accept(snapshot);
        Path exported = ArchitectDebugReports.export(directory, journal, Map.of());
        assertEquals(snapshot, ArchitectDebugSnapshot.JSON.fromJson(
                Files.readString(exported.resolve("visual-snapshot.json")), ArchitectDebugSnapshot.class));
        assertEquals(snapshot.json() + "\n", Files.readString(exported.resolve("visual-history.jsonl")));
        var summary = JsonParser.parseString(Files.readString(exported.resolve("summary.json"))).getAsJsonObject();
        var hashes = summary.getAsJsonObject("attachmentSha256");
        assertEquals(3, hashes.size());
        for (var entry : hashes.entrySet()) assertEquals(entry.getValue().getAsString(),
                ArchitectDebugReports.sha256(Files.readAllBytes(exported.resolve(entry.getKey()))));
        var pointer = JsonParser.parseString(Files.readString(directory.resolve("architect-latest.json"))).getAsJsonObject();
        assertEquals("COMPLETE", pointer.get("status").getAsString());
    }
}
