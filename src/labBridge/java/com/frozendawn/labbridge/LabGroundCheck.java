package com.frozendawn.labbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Retains both clocks while the actor is held; scoring begins only after actual support settles. */
public final class LabGroundCheck {
    private final Path directory;
    private final JsonObject initial;
    private final JsonArray serverRows = new JsonArray(), clientRows = new JsonArray(), keyRows = new JsonArray();
    private JsonObject latestClient;
    private int stable, samples;
    private long lastTick = Long.MIN_VALUE;
    private String reason = "WAITING_FOR_GROUND";
    public LabGroundCheck(Path root, JsonObject initial) throws java.io.IOException {
        this.initial = initial.deepCopy();
        this.initial.addProperty("check", UUID.randomUUID().toString());
        directory = root.resolve("check-" + this.initial.get("check").getAsString());
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("start.json"), this.initial + "\n", StandardOpenOption.CREATE_NEW);
    }
    public synchronized boolean waiting() { return reason.equals("WAITING_FOR_GROUND"); }
    public synchronized void client(JsonObject row, boolean key) {
        if (!waiting()) return;
        var target = key ? keyRows : clientRows;
        if (target.size() >= 4096) { finish("OVERFLOW"); return; }
        target.add(row.deepCopy());
        if (!key) latestClient = row.deepCopy();
    }
    public synchronized boolean server(JsonObject row) {
        if (!waiting()) return false;
        long tick = row.get("gameTime").getAsLong();
        if (tick <= lastTick) return false;
        boolean valid = lastTick == Long.MIN_VALUE || tick == lastTick + 1;
        lastTick = tick; samples++;
        try {
            var player = row.getAsJsonArray("players").get(0).getAsJsonObject();
            var actor = row.getAsJsonArray("architects").get(0).getAsJsonObject();
            valid &= row.getAsJsonArray("players").size() == 1 && row.getAsJsonArray("architects").size() == 1
                    && row.get("session").equals(initial.get("session")) && player.get("uuid").equals(initial.get("player"))
                    && actor.get("uuid").equals(initial.get("actor")) && actor.get("noAI").getAsBoolean()
                    && actor.get("health").getAsDouble() == 40 && player.get("gameMode").getAsString().equals("survival")
                    && !player.get("sprinting").getAsBoolean() && row.get("viewRun").equals(initial.get("viewRun")) && row.get("viewStage").getAsInt() == 1
                    && row.get("viewTimer").getAsInt() == 0 && stationary(player)
                    && latestClient != null && latestClient.get("session").equals(initial.get("session"))
                    && latestClient.get("player").equals(initial.get("player")) && stationary(latestClient)
                    && latestClient.get("focused").getAsBoolean() && !latestClient.get("screenOpen").getAsBoolean()
                    && !latestClient.get("paused").getAsBoolean() && Math.abs(tick - latestClient.get("gameTime").getAsLong()) <= 2;
            for (String bucket : java.util.List.of("mapped", "windowKeys"))
                for (var entry : latestClient.getAsJsonObject(bucket).entrySet()) valid &= !entry.getValue().getAsBoolean();
        } catch (RuntimeException malformed) { valid = false; }
        stable = valid ? stable + 1 : 0;
        var retained = row.deepCopy(); retained.addProperty("groundValid", valid); retained.addProperty("stableTicks", stable);
        serverRows.add(retained);
        if (stable >= 40) return finish("GROUND_CONFIRMED");
        if (samples >= 2400) finish("TIME_LIMIT");
        return false;
    }
    private boolean stationary(JsonObject row) {
        return row.get("x").getAsDouble() == initial.get("x").getAsDouble()
                && row.get("y").getAsDouble() == -44 && row.get("z").getAsDouble() == 5.5
                && row.get("onGround").getAsBoolean() && !row.get("flying").getAsBoolean()
                && !row.get("sneaking").getAsBoolean() && row.get("pose").getAsString().equals("STANDING");
    }
    public synchronized JsonObject status() {
        var out = new JsonObject();
        for (String key : java.util.List.of("check", "session", "player", "actor", "viewRun")) out.add(key, initial.get(key));
        out.addProperty("directory", directory.toString()); out.addProperty("reason", reason);
        out.addProperty("waiting", waiting()); out.addProperty("stableTicks", stable); out.addProperty("requiredStableTicks", 40);
        out.addProperty("serverSamples", serverRows.size()); out.addProperty("clientSamples", clientRows.size());
        return out;
    }
    public synchronized boolean finish(String outcome) {
        if (!waiting()) return false;
        reason = outcome;
        try {
            var hashes = new JsonObject();
            for (var entry : java.util.Map.of("server.jsonl",serverRows,"client.jsonl",clientRows,"keys.jsonl",keyRows).entrySet()) {
                var text = new StringBuilder(); entry.getValue().forEach(row -> text.append(row).append('\n'));
                var file = directory.resolve(entry.getKey()); Files.writeString(file,text,StandardOpenOption.CREATE_NEW);
                hashes.addProperty(entry.getKey(), hash(file));
            }
            hashes.addProperty("start.json",hash(directory.resolve("start.json")));
            var summary = status(); summary.add("hashes", hashes);
            Files.writeString(directory.resolve("summary.json"), summary + "\n", StandardOpenOption.CREATE_NEW);
            return outcome.equals("GROUND_CONFIRMED");
        } catch (Exception failure) { reason = "ARCHIVE_FAILED"; return false; }
    }
    private static String hash(Path file) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
    /** Bind the archived readiness to this exact participant, actor and view, without rewriting it. */
    public static boolean retained(Path root, UUID check, UUID player, UUID actor, int run) {
        try {
            var d = root.resolve("check-"+check);
            var summary = com.google.gson.JsonParser.parseString(Files.readString(d.resolve("summary.json"))).getAsJsonObject();
            if (!summary.get("check").getAsString().equals(check.toString())
                    || !summary.get("player").getAsString().equals(player.toString()) || !summary.get("actor").getAsString().equals(actor.toString())
                    || summary.get("viewRun").getAsInt()!=run || !summary.get("reason").getAsString().equals("GROUND_CONFIRMED")
                    || summary.get("stableTicks").getAsInt()!=40) return false;
            for (var entry : summary.getAsJsonObject("hashes").entrySet())
                if (!java.util.Set.of("server.jsonl","client.jsonl","keys.jsonl","start.json").contains(entry.getKey())
                        || !hash(d.resolve(entry.getKey())).equals(entry.getValue().getAsString())) return false;
            var initial = com.google.gson.JsonParser.parseString(Files.readString(d.resolve("start.json"))).getAsJsonObject();
            for(String key:java.util.List.of("check","session","player","actor","viewRun")) if(!initial.get(key).equals(summary.get(key))) return false;
            var rows=Files.readAllLines(d.resolve("server.jsonl"));int size=rows.size();
            if(size!=summary.get("serverSamples").getAsInt() || size<40 || size>2400 || summary.get("clientSamples").getAsInt()>4096) return false;
            long tick=Long.MIN_VALUE;
            for(int i=size-40;i<size;i++) {
                var row=com.google.gson.JsonParser.parseString(rows.get(i)).getAsJsonObject();
                long now=row.get("gameTime").getAsLong();
                if(!row.get("groundValid").getAsBoolean() || row.get("stableTicks").getAsInt()!=i-(size-40)+1
                        || tick!=Long.MIN_VALUE && now!=tick+1 || !row.get("session").equals(summary.get("session"))
                        || row.get("viewRun").getAsInt()!=run || row.get("viewStage").getAsInt()!=1 || row.get("viewTimer").getAsInt()!=0) return false;
                tick=now;
            }
            return summary.getAsJsonObject("hashes").size()==4;
        } catch (Exception failure) { return false; }
    }
}
