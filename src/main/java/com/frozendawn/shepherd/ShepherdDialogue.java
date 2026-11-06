package com.frozendawn.shepherd;

import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The dialogue file format. Text stays in lang keys; a file holds only structure, moods and scores. */
public final class ShepherdDialogue {
    /** What a line's {@code args} may ask for. The server works them out for whoever it is talking to. */
    public static final Set<String> ARGS = Set.of("pit_x", "pit_z", "pit_blocks", "pit_bearing", "offline_days");
    /** What a node's {@code project} may name. */
    public static final Set<String> PROJECTIONS = Set.of("hunter", "mimic", "architect");

    private ShepherdDialogue() {}

    /** One thing she says. {@code heavyKey} replaces {@code key} when swearing is on, and may be null. A null {@code condition} is always said. {@code args} fill the text's slots, in order. */
    public record Line(String key, String heavyKey, Mood mood, String condition, List<String> args) {
        public Line(String key, String heavyKey, Mood mood, String condition) {
            this(key, heavyKey, mood, condition, List.of());
        }
    }

    /** The four fixed reply positions, in screen order. */
    public enum Slot {
        ASK,
        KIND,
        BLUNT,
        SARCASTIC
    }

    /** {@code noted} is what saying this tells her, as a node's is what arriving there does. A null {@code next} ends the walk. */
    public record Response(Slot slot, String label, String full, String next, int affinity, List<String> set, List<String> noted) {}

    /**
     * A node with no responses continues to {@code next} on a click, or ends the conversation if that is null.
     * If {@code when} does not hold, nobody ever stands on it: the walk goes straight on to {@code otherwise}.
     * Arriving by a reply, {@code noted} is evidence for or against a theory ({@code "+kind"}) and {@code keep} a moment to
     * hold on to, so writers need no Java. A {@code plain} node's slots mean nothing about tone: a menu, not an answer.
     * Reaching a node with {@code project} ends the talk, and its lines run over that hologram.
     */
    public record Node(String id, List<Line> lines, List<Response> responses, String next, List<String> set, String when, String otherwise,
                       List<String> noted, String keep, boolean plain, String project) {
        public Node(String id, List<Line> lines, List<Response> responses, String next, List<String> set, String when, String otherwise) {
            this(id, lines, responses, next, set, when, otherwise, List.of(), null, false);
        }

        public Node(String id, List<Line> lines, List<Response> responses, String next, List<String> set, String when, String otherwise,
                    List<String> noted, String keep, boolean plain) {
            this(id, lines, responses, next, set, when, otherwise, noted, keep, plain, null);
        }
    }

    public static List<Node> parse(JsonElement file) {
        List<Node> nodes = new ArrayList<>();
        for (JsonElement node : file.getAsJsonObject().getAsJsonArray("nodes")) nodes.add(node(node.getAsJsonObject()));
        return List.copyOf(nodes);
    }

    private static Node node(JsonObject json) {
        String id = json.get("id").getAsString();
        try {
            List<Line> lines = new ArrayList<>();
            for (JsonElement line : json.getAsJsonArray("lines")) lines.add(line(line.getAsJsonObject()));
            List<Response> responses = new ArrayList<>();
            if (json.has("responses")) {
                for (JsonElement response : json.getAsJsonArray("responses")) responses.add(response(response.getAsJsonObject()));
            }
            return new Node(id, List.copyOf(lines), List.copyOf(responses), string(json, "next"), strings(json, "set"), string(json, "when"), string(json, "else"),
                    strings(json, "noted"), string(json, "keep"), json.has("plain") && json.get("plain").getAsBoolean(), string(json, "project"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Dialogue node " + id + ": " + e.getMessage(), e);
        }
    }

    private static Line line(JsonObject json) {
        return new Line(json.get("line").getAsString(), string(json, "heavy"), Mood.byName(json.get("mood").getAsString()), string(json, "if"), strings(json, "args"));
    }

    private static Response response(JsonObject json) {
        String label = json.get("label").getAsString();
        String full = string(json, "full");
        Slot slot = Slot.valueOf(json.get("slot").getAsString().toUpperCase(Locale.ROOT));
        int affinity = json.has("affinity") ? json.get("affinity").getAsInt() : 0;
        return new Response(slot, label, full != null ? full : label, string(json, "next"), affinity, strings(json, "set"), strings(json, "noted"));
    }

    private static String string(JsonObject json, String name) {
        return json.has(name) ? json.get(name).getAsString() : null;
    }

    private static List<String> strings(JsonObject json, String name) {
        if (!json.has(name)) return List.of();
        JsonArray array = json.getAsJsonArray(name);
        List<String> strings = new ArrayList<>();
        for (JsonElement entry : array) strings.add(entry.getAsString());
        return List.copyOf(strings);
    }
}
