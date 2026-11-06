package com.frozendawn.miller;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** One thing a player might mean. {@code node} null is always locked, {@code when} null always holds, {@code label} null is never offered, {@code mind} is the focus keys it answers, and {@code menu} is the menu it sits on. */
public record Intent(String id, String node, String when, String label, String guess, List<String> examples, List<String> mind, String menu) {
    /** The menu a topic sits on when it names no other. */
    public static final String ORSA = "orsa";

    public Intent(String id, String node, String when, String label, String guess, List<String> examples) {
        this(id, node, when, label, guess, examples, List.of(), ORSA);
    }

    public static List<Intent> parse(JsonElement file) {
        var intents = new ArrayList<Intent>();
        for (JsonElement entry : file.getAsJsonObject().getAsJsonArray("intents")) {
            var json = entry.getAsJsonObject();
            intents.add(new Intent(json.get("id").getAsString(), string(json, "node"), string(json, "when"), string(json, "label"), string(json, "guess"), strings(json, "examples"), strings(json, "mind"), json.has("menu") ? json.get("menu").getAsString() : ORSA));
        }
        return List.copyOf(intents);
    }

    private static String string(JsonObject json, String name) {
        return json.has(name) ? json.get(name).getAsString() : null;
    }

    private static List<String> strings(JsonObject json, String name) {
        var strings = new ArrayList<String>();
        if (json.has(name)) json.getAsJsonArray(name).forEach(entry -> strings.add(entry.getAsString()));
        return List.copyOf(strings);
    }
}
