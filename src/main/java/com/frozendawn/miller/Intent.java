package com.frozendawn.miller;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** One thing a player might mean. {@code node} null is always locked, {@code when} null always holds, and {@code label} null is never offered. */
public record Intent(String id, String node, String when, String label, String guess, List<String> examples) {
    public static List<Intent> parse(JsonElement file) {
        var intents = new ArrayList<Intent>();
        for (JsonElement entry : file.getAsJsonObject().getAsJsonArray("intents")) {
            var json = entry.getAsJsonObject();
            var examples = new ArrayList<String>();
            json.getAsJsonArray("examples").forEach(example -> examples.add(example.getAsString()));
            intents.add(new Intent(json.get("id").getAsString(), string(json, "node"), string(json, "when"), string(json, "label"), string(json, "guess"), List.copyOf(examples)));
        }
        return List.copyOf(intents);
    }

    private static String string(JsonObject json, String name) {
        return json.has(name) ? json.get(name).getAsString() : null;
    }
}
