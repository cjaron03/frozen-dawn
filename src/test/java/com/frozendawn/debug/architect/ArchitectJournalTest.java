package com.frozendawn.debug.architect;

import com.frozendawn.entity.architect.ArchitectDecisionJournal;
import com.frozendawn.entity.architect.BreakChoice;
import com.frozendawn.entity.architect.BreakReason;
import com.frozendawn.entity.architect.ArchitectWalkBreakPlanner;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectJournalTest {
    @TempDir Path directory;

    private static ArchitectDecisionJournal.Entry row(long tick, BlockPos pos, long breaks, String detail) {
        return new ArchitectDecisionJournal.Entry(tick, "BREAK_END", "APPROACH", pos, pos.south(), "BREACH", null,
                new BreakChoice(pos.above(2), BreakReason.STEP_UP_CEILING), 40, 1, 0, breaks, detail);
    }

    @Test void comparisonsUseRunRelativeTicksCoordinatesAndExcavation() {
        var a = new ArchitectDecisionJournal();
        var b = new ArchitectDecisionJournal();
        a.start(UUID.randomUUID(), 100, BlockPos.ZERO, 0, Rotation.NONE);
        b.start(UUID.randomUUID(), 9200, new BlockPos(40, 64, -20), 7, Rotation.NONE);
        a.append(row(140, BlockPos.ZERO, 1, "DESTROYED"));
        b.append(row(9240, new BlockPos(40, 64, -20), 8, "DESTROYED"));
        assertEquals(a.tsv(), b.tsv());
        assertEquals(1L, b.breaks());
    }

    @Test void overflowRetainsRunSummaryAndReportsTruncation() {
        var journal = new ArchitectDecisionJournal();
        journal.start(0, BlockPos.ZERO);
        for (int i = 0; i < 600; i++) journal.append(row(i, BlockPos.ZERO, i, "DESTROYED"));
        assertEquals(400, journal.entries().size());
        assertEquals(200, journal.dropped());
        assertEquals(600L, journal.eventCounts().get("BREAK_END"));
        assertEquals(599L, journal.summary().get("destroyedBlocks"));
        assertEquals(false, journal.summary().get("traceComplete"));
        journal.finish(600, "FAILED", "deadline");
        journal.append(row(601, BlockPos.ZERO, 999, "DESTROYED"));
        assertEquals(599, journal.breaks());
        assertEquals("FAILED", journal.summary().get("outcome"));
    }

    @Test void markersCannotAddTsvRowsOrColumns() {
        var journal = new ArchitectDecisionJournal();
        journal.start(0, BlockPos.ZERO);
        journal.append(row(1, BlockPos.ZERO, 0, "line\tcolumn\nnext\rrow"));
        String[] lines = journal.tsv().split("\n");
        assertEquals(2, lines.length);
        assertEquals(14, lines[1].split("\t", -1).length);
    }

    @Test void emptyExportInvalidatesLatestButKeepsPreviousRun() throws Exception {
        var journal = new ArchitectDecisionJournal();
        journal.start(0, BlockPos.ZERO);
        journal.append(row(1, BlockPos.ZERO, 1, "DESTROYED"));
        journal.finish(1, "PASSED", "complete");
        Path previous = ArchitectDebugReports.export(directory, journal, Map.of("scenario", "example"));
        String before = Files.readString(previous.resolve("decisions.tsv"));
        journal.start(20, new BlockPos(1, 2, 3));
        assertThrows(IOException.class, () -> ArchitectDebugReports.export(directory, journal, Map.of()));
        assertFalse(Files.exists(directory.resolve("architect-latest.tsv")));
        var pointer = JsonParser.parseString(Files.readString(directory.resolve("architect-latest.json"))).getAsJsonObject();
        assertEquals("FAILED", pointer.get("status").getAsString());
        assertEquals(journal.runId().toString(), pointer.get("runId").getAsString());
        assertEquals(before, Files.readString(previous.resolve("decisions.tsv")));
    }

    @Test void filesystemFailureCannotAdvertiseThePreviousExportAsCurrent() throws Exception {
        var journal = new ArchitectDecisionJournal();
        journal.start(0, BlockPos.ZERO);
        journal.append(row(1, BlockPos.ZERO, 1, "DESTROYED"));
        Path previous = ArchitectDebugReports.export(directory, journal, Map.of());
        // A non-empty directory at an alias path causes a real filesystem write failure.
        Path obstructed = directory.resolve("architect-latest.tsv.meta.txt");
        Files.delete(obstructed);
        Files.createDirectory(obstructed);
        Files.writeString(obstructed.resolve("obstruction"), "keep");
        assertThrows(IOException.class, () -> ArchitectDebugReports.export(directory, journal, Map.of()));
        var pointer = JsonParser.parseString(Files.readString(directory.resolve("architect-latest.json"))).getAsJsonObject();
        assertEquals("FAILED", pointer.get("status").getAsString());
        assertFalse(Files.exists(directory.resolve("architect-latest.tsv")));
        assertTrue(Files.isRegularFile(previous.resolve("decisions.tsv")));
    }

    @Test void successfulExportIdentifiesImmutableTraceAndBuild() throws Exception {
        var journal = new ArchitectDecisionJournal();
        journal.start(0, BlockPos.ZERO);
        journal.append(row(1, BlockPos.ZERO, 1, "DESTROYED"));
        Path export = ArchitectDebugReports.export(directory, journal, Map.of("seed", 1337));
        var pointer = JsonParser.parseString(Files.readString(directory.resolve("architect-latest.json"))).getAsJsonObject();
        var summary = JsonParser.parseString(Files.readString(export.resolve("summary.json"))).getAsJsonObject();
        assertEquals("COMPLETE", pointer.get("status").getAsString());
        assertEquals(export.resolve("decisions.tsv"), directory.resolve(pointer.get("trace").getAsString()));
        assertEquals(ArchitectDebugReports.sha256(Files.readAllBytes(export.resolve("decisions.tsv"))), pointer.get("traceSha256").getAsString());
        assertEquals(64, summary.getAsJsonObject("build").get("sourceSha256").getAsString().length());
    }

    @Test void candidateTracingPreservesSelectionAndShortCircuitPredicates() {
        var candidates = List.of(new BreakChoice(BlockPos.ZERO, BreakReason.HEAD_CLEARANCE),
                new BreakChoice(BlockPos.ZERO.above(), BreakReason.STEP_UP_CEILING),
                new BreakChoice(BlockPos.ZERO.east(), BreakReason.IMMEDIATE_CANDIDATE),
                new BreakChoice(BlockPos.ZERO.south(), BreakReason.IMMEDIATE_CANDIDATE));
        var trace = new ArrayList<ArchitectWalkBreakPlanner.CandidateDecision>();
        var visited = new ArrayList<BlockPos>();
        var selected = ArchitectWalkBreakPlanner.selectChoice(candidates, Set.of(BlockPos.ZERO),
                p -> { visited.add(p); return p.equals(BlockPos.ZERO.above()) ? "PROTECTED" : null; }, p -> false, trace::add);
        var quietVisited = new ArrayList<BlockPos>();
        var quiet = ArchitectWalkBreakPlanner.selectChoice(candidates, Set.of(BlockPos.ZERO),
                p -> { quietVisited.add(p); return p.equals(BlockPos.ZERO.above()) ? "PROTECTED" : null; }, p -> false, d -> { });
        assertEquals(quiet, selected);
        assertEquals(quietVisited, visited);
        assertEquals(List.of(BlockPos.ZERO.above(), BlockPos.ZERO.east()), visited);
        assertEquals(List.of("BLACKLISTED", "PROTECTED", "SELECTED", "NOT_EVALUATED_AFTER_SELECTION"), trace.stream().map(ArchitectWalkBreakPlanner.CandidateDecision::outcome).toList());
    }
}
