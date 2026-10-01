package com.frozendawn.labbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** No network listener. Files are claimed before execution and never replayed after a crash. */
public final class LabInbox implements AutoCloseable {
    public interface Endpoint {
        CompletableFuture<JsonObject> status();
        CompletableFuture<JsonObject> execute(LabRequest request);
    }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path root;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final java.util.concurrent.ScheduledExecutorService worker;
    private volatile Endpoint endpoint;

    public LabInbox(Path root) throws IOException {
        this.root = root;
        for (String sub : new String[] {"", "requests", "claimed", "responses"}) {
            Path dir = root.resolve(sub); Files.createDirectories(dir);
            try { Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------")); }
            catch (UnsupportedOperationException ignored) { }
        }
        lockChannel = FileChannel.open(root.resolve("bridge.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        lock = lockChannel.tryLock();
        if (lock == null) { lockChannel.close(); throw new IOException("Another lab bridge owns this directory"); }
        worker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "FrozenDawn-LabBridge"); thread.setDaemon(true); return thread;
        });
    }
    public void bind(Endpoint endpoint) { this.endpoint = endpoint; }
    public void start() { worker.scheduleWithFixedDelay(this::safePoll, 0, 250, TimeUnit.MILLISECONDS); }
    private void safePoll() {
        try { poll(); }
        catch (Exception error) { System.err.println("[LabBridge] " + error); }
    }
    public void poll() throws Exception {
        Endpoint current = endpoint;
        JsonObject status = current == null ? new JsonObject() : current.status().get(3, TimeUnit.SECONDS);
        status.addProperty("schema", 1); status.addProperty("heartbeat", System.currentTimeMillis());
        status.addProperty("worldLoaded", current != null);
        status.addProperty("checkout", System.getProperty("frozendawn.labBridge.checkout", "test"));
        write(root.resolve("status.json"), status);
        try (var files = Files.newDirectoryStream(root.resolve("requests"), "*.json")) {
            for (Path file : files) {
                String stem = file.getFileName().toString().replace(".json", "");
                try { if (!UUID.fromString(stem).toString().equals(stem)) continue; }
                catch (IllegalArgumentException invalid) { continue; }
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                Path claimed = root.resolve("claimed").resolve(file.getFileName());
                Path response = root.resolve("responses").resolve(file.getFileName());
                // A claimed ID is never executed twice, even if the first process died before replying.
                if (Files.exists(claimed) || Files.exists(response)) { Files.delete(file); continue; }
                Files.move(file, claimed, StandardCopyOption.ATOMIC_MOVE);
                JsonObject reply = new JsonObject(); reply.addProperty("id", stem);
                try {
                    if (Files.size(claimed) > 16384) throw new IllegalArgumentException("Request exceeds 16 KiB");
                    JsonObject json = JsonParser.parseString(Files.readString(claimed)).getAsJsonObject();
                    LabRequest request = LabRequest.parse(json);
                    if (!stem.equals(request.id())) throw new IllegalArgumentException("Request ID does not match filename");
                    reply.add("request", json);
                    if (current == null || current != endpoint) throw new IllegalStateException("No matching loaded world");
                    reply.add("data", current.execute(request).get(32, TimeUnit.SECONDS));
                    reply.addProperty("ok", true);
                } catch (Exception error) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    reply.addProperty("ok", false); reply.addProperty("error", cause.toString());
                    reply.addProperty("note", "Do not automatically retry. A timeout may have an unknown outcome; inspect state first.");
                }
                reply.addProperty("finishedAt", System.currentTimeMillis());
                write(response, reply);
                break;
            }
        }
    }
    private static void write(Path path, JsonObject value) throws IOException {
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, JSON.toJson(value));
        Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    public void close() throws IOException {
        worker.shutdownNow(); endpoint = null;
        lock.release(); lockChannel.close();
    }
}
