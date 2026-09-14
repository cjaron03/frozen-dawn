package com.frozendawn.debug.architect;

import com.frozendawn.entity.architect.ArchitectDecisionJournal;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Immutable exports and an atomic status pointer. A failed request never advertises old data. */
public final class ArchitectDebugReports {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private ArchitectDebugReports() { }

    public static Path export(Path directory, ArchitectDecisionJournal journal, Map<String, ?> context) throws IOException {
        return export(directory, journal, context, Map.of());
    }

    /** Publish the status pointer only after every linked trace is safely written. */
    public static Path export(Path directory, ArchitectDecisionJournal journal, Map<String, ?> context,
            Map<String, String> extraFiles) throws IOException {
        String id = journal.runId().toString();
        try {
            invalidate(directory, id, "WRITING", "Export in progress");
            if (journal.entries().isEmpty() && journal.visual().latest() == null) throw new IOException("No decisions recorded for run " + id);
            Path export = Files.createTempDirectory(directory, "run-" + id + "-");
            Path trace = export.resolve("decisions.tsv");
            Path summary = export.resolve("summary.json");
            String table = journal.tsv();
            Map<String, Object> data = new LinkedHashMap<>(journal.summary());
            data.put("context", context);
            data.put("build", buildInfo());
            data.put("traceSha256", sha256(table.getBytes(StandardCharsets.UTF_8)));
            data.put("visualDebug", journal.visual().summary());
            Map<String, String> attachments = new LinkedHashMap<>(extraFiles);
            attachments.putAll(journal.visual().files());
            Map<String, String> attachmentHashes = new LinkedHashMap<>();
            for (var entry : attachments.entrySet()) attachmentHashes.put(entry.getKey(), sha256(entry.getValue().getBytes(StandardCharsets.UTF_8)));
            data.put("attachmentSha256", attachmentHashes);
            Files.writeString(trace, table, StandardCharsets.UTF_8);
            Files.writeString(summary, JSON.toJson(data), StandardCharsets.UTF_8);
            for (var entry : attachments.entrySet()) {
                if (!Path.of(entry.getKey()).getFileName().toString().equals(entry.getKey())) {
                    throw new IOException("Extra report files must have a plain filename");
                }
                Files.writeString(export.resolve(entry.getKey()), entry.getValue(), StandardCharsets.UTF_8);
            }
            // Compatibility aliases are useful for humans. Automation follows the immutable
            // paths in architect-latest.json, whose COMPLETE status is published last.
            atomicWrite(directory.resolve("architect-latest.tsv"), table);
            atomicWrite(directory.resolve("architect-latest.tsv.meta.txt"), JSON.toJson(data));
            atomicWrite(directory.resolve("architect-latest.json"), JSON.toJson(Map.of(
                    "status", "COMPLETE", "runId", id,
                    "trace", directory.relativize(trace).toString(),
                    "summary", directory.relativize(summary).toString(),
                    "traceSha256", data.get("traceSha256"))));
            return export;
        } catch (IOException failure) {
            try { invalidate(directory, id, "FAILED", failure.getMessage()); }
            catch (IOException statusFailure) { failure.addSuppressed(statusFailure); }
            throw failure;
        }
    }

    public static void invalidate(Path directory, String runId, String status, String reason) throws IOException {
        Files.createDirectories(directory);
        atomicWrite(directory.resolve("architect-latest.json"), JSON.toJson(Map.of(
                "status", status, "runId", runId, "reason", reason)));
        Files.deleteIfExists(directory.resolve("architect-latest.tsv"));
        Files.deleteIfExists(directory.resolve("architect-latest.tsv.meta.txt"));
    }

    private static void atomicWrite(Path target, String contents) throws IOException {
        Path tmp = Files.createTempFile(target.getParent(), ".report-", ".tmp");
        try {
            Files.writeString(tmp, contents, StandardCharsets.UTF_8);
            try { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(tmp); }
    }

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static Map<String, String> buildInfo() throws IOException {
        Properties properties = new Properties();
        try (var input = ArchitectDebugReports.class.getResourceAsStream("/architect-build.properties")) {
            if (input == null) throw new IOException("Missing Architect build fingerprint");
            properties.load(input);
        }
        return Map.of("version", properties.getProperty("version"), "sourceSha256", properties.getProperty("sourceSha256"));
    }
}
