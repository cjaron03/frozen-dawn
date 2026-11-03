package com.frozendawn.labbridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;

/** Explicit world and scoreboard guards are required even for otherwise approved QA functions. */
public final class LabFunctionPolicy {
    private LabFunctionPolicy() { }
    public static void check(Path path, String function, String world, BiFunction<String, String, Integer> score) throws IOException {
        if (!function.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || function.contains(".."))
            throw new IllegalArgumentException("Invalid function identifier");
        if (Files.size(path) > 65536) throw new IllegalArgumentException("Policy is too large");
        JsonObject policy = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        JsonObject functions = policy.getAsJsonObject("functions");
        if (policy.get("schema").getAsInt() != 1 || !functions.has(function))
            throw new IllegalArgumentException("Function is not approved in config/lab-bridge-functions.json");
        JsonObject rule = functions.getAsJsonObject(function);
        if (!rule.get("world").getAsString().equals(world)) throw new IllegalArgumentException("Function belongs to a different world");
        for (var item : rule.getAsJsonArray("scores")) {
            var guard = item.getAsJsonObject();
            String objective = guard.get("objective").getAsString(), holder = guard.get("holder").getAsString();
            Integer actual = score.apply(objective, holder);
            int expected = guard.get("equals").getAsInt();
            if (actual == null || actual != expected)
                throw new IllegalArgumentException("STAGE_MISMATCH: " + holder + " " + objective + " expected " + expected + ", got " + actual);
        }
    }
}
