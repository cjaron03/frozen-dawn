package com.frozendawn.labbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Bounded, passive timelines. Client and server sample on their own threads and retain their own clocks. */
public final class LabInputCapture {
    public static final int SERVER_LIMIT = 240;
    private static final int CLIENT_LIMIT = 1024, EVENT_LIMIT = 256;
    private final String id = UUID.randomUUID().toString(), session, player;
    private final Path directory;
    private final int serverLimit;
    private final JsonArray server = new JsonArray(), client = new JsonArray(), keys = new JsonArray();
    private final JsonObject hashes = new JsonObject();
    private long lastServerTick;
    private int dropped, keyRepeatsIgnored;
    private String reason = "RECORDING", archiveError;

    public LabInputCapture(Path root, String session, String player, JsonObject initial) throws java.io.IOException {
        this(root, session, player, initial, SERVER_LIMIT);
    }
    public LabInputCapture(Path root, String session, String player, JsonObject initial, int serverLimit) throws java.io.IOException {
        if (serverLimit != SERVER_LIMIT && serverLimit != 480) throw new IllegalArgumentException("Only 240 or 480 server ticks are supported");
        this.serverLimit = serverLimit;
        this.session = session; this.player = player;
        directory = root.resolve("probe-" + id);
        Files.createDirectories(root); Files.createDirectory(directory);
        lastServerTick = initial.get("gameTime").getAsLong() - 1;
        var start = initial.deepCopy(); start.addProperty("probe", id);
        start.addProperty("session", session); start.addProperty("player", player);
        start.addProperty("serverTickLimit", serverLimit);
        Files.writeString(directory.resolve("start.json"), start + "\n", StandardOpenOption.CREATE_NEW);
    }
    public synchronized boolean active() { return reason.equals("RECORDING"); }
    public synchronized void client(JsonObject row, boolean keyEvent) {
        if (!active() || !session.equals(row.get("session").getAsString())
                || !player.equals(row.get("player").getAsString())) return;
        // GLFW repeat describes a held key; state frames retain that hold. Reserve the
        // bounded event stream for press/release edges so a long walk cannot erase them.
        if (keyEvent && row.has("action") && row.get("action").getAsInt() == 2) { keyRepeatsIgnored++; return; }
        var target = keyEvent ? keys : client;
        if (target.size() >= (keyEvent ? EVENT_LIMIT : CLIENT_LIMIT)) { dropped++; return; }
        target.add(row.deepCopy());
    }
    /** Returns true only on the transition from recording to the completed time bound. */
    public synchronized boolean server(JsonObject row) {
        long tick = row.get("gameTime").getAsLong();
        if (!active() || tick <= lastServerTick || !session.equals(row.get("session").getAsString())) return false;
        lastServerTick = tick; server.add(row.deepCopy());
        if (server.size() == serverLimit) { finish("TIME_LIMIT"); return true; }
        return false;
    }
    public synchronized void finish(String ending) {
        if (!active()) return;
        reason = ending;
        try {
            write("server.jsonl", server); write("client.jsonl", client); write("keys.jsonl", keys);
            Files.writeString(directory.resolve("summary.json"), status() + "\n", StandardOpenOption.CREATE_NEW);
        } catch (Exception error) {
            archiveError = error.toString();
            System.err.println("[LabInputCapture] Evidence write failed: " + error);
        }
    }
    private void write(String name, JsonArray rows) throws Exception {
        var content = new StringBuilder(); rows.forEach(row -> content.append(row).append('\n'));
        var file = directory.resolve(name);
        Files.writeString(file, content, StandardOpenOption.CREATE_NEW);
        hashes.addProperty(name, java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
    }
    public synchronized JsonObject status() {
        var out = new JsonObject(); out.addProperty("probe", id); out.addProperty("session", session);
        out.addProperty("player", player); out.addProperty("directory", directory.toString());
        out.addProperty("reason", reason); out.addProperty("active", active());
        out.addProperty("serverTickLimit", serverLimit);
        out.addProperty("serverFrames", server.size()); out.addProperty("clientFrames", client.size());
        out.addProperty("keyRepeatsIgnored", keyRepeatsIgnored);
        out.addProperty("keyEvents", keys.size()); out.addProperty("dropped", dropped);
        out.addProperty("captureComplete", reason.equals("TIME_LIMIT") && server.size() == serverLimit
                && !client.isEmpty() && dropped == 0 && archiveError == null);
        out.add("hashes", hashes.deepCopy());
        if (archiveError != null) out.addProperty("archiveError", archiveError);
        out.addProperty("note", "Window key state can include synthetic input. This records game state, not proof of physical key presses.");
        return out;
    }
}
