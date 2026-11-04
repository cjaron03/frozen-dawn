package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.shepherd.ShepherdDialogue.Node;
import com.frozendawn.shepherd.ShepherdDialogue.Response;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Every dialogue file, walked the way the server will walk it. Lines are free to change; the shape is not. */
class ShepherdDialogueDataTest {
    private static JsonObject lang() throws IOException {
        try (var in = ShepherdDialogueDataTest.class.getResourceAsStream("/assets/frozendawn/lang/en_us.json")) {
            assertNotNull(in);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Map<String, Node> nodes() throws IOException, URISyntaxException {
        var folder = ShepherdDialogueDataTest.class.getResource("/data/frozendawn/shepherd/dialogue");
        assertNotNull(folder, "the dialogue folder on the classpath");
        Map<String, Node> nodes = new HashMap<>();
        try (var files = Files.list(Path.of(folder.toURI()))) {
            for (Path file : files.toList()) {
                try (var reader = Files.newBufferedReader(file)) {
                    for (Node node : ShepherdDialogue.parse(JsonParser.parseReader(reader))) {
                        assertNull(nodes.put(node.id(), node), "two nodes named " + node.id());
                    }
                }
            }
        }
        return nodes;
    }

    private static List<String> exits(Node node) {
        List<String> exits = new ArrayList<>(node.responses().stream().map(Response::next).toList());
        if (node.next() != null) exits.add(node.next());
        return exits;
    }

    @Test
    void everyNextResolves() throws Exception {
        var nodes = nodes();
        assertTrue(nodes.containsKey("fm.boot"));
        for (Node node : nodes.values()) {
            for (String next : exits(node)) assertTrue(nodes.containsKey(next), node.id() + " -> " + next);
        }
    }

    @Test
    void everyKeyHasText() throws Exception {
        var lang = lang();
        for (Node node : nodes().values()) {
            assertFalse(node.lines().isEmpty(), node.id());
            List<String> keys = new ArrayList<>();
            for (var line : node.lines()) {
                keys.add(line.key());
                if (line.heavyKey() != null) keys.add(line.heavyKey());
            }
            for (Response response : node.responses()) {
                keys.add(response.label());
                keys.add(response.full());
            }
            for (String key : keys) {
                assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), node.id() + ": " + key);
            }
        }
    }

    @Test
    void noSlotTwice() throws Exception {
        for (Node node : nodes().values()) {
            Set<ShepherdDialogue.Slot> slots = new HashSet<>();
            for (Response response : node.responses()) assertTrue(slots.add(response.slot()), node.id());
        }
    }

    @Test
    void nothingNamesTheWord() throws Exception {
        for (var entry : lang().entrySet()) {
            if (!entry.getKey().startsWith("shepherd.")) continue;
            assertFalse(entry.getValue().getAsString().toLowerCase(Locale.ROOT).contains("continuity"), entry.getKey());
        }
    }

    @Test
    void everyPathEnds() throws Exception {
        var nodes = nodes();
        int[] ends = {0};
        Set<String> reached = new HashSet<>();
        walk(nodes, nodes.get("fm.boot"), new ArrayDeque<>(), reached, ends);
        assertTrue(ends[0] > 0, "no path reached an end");

        // A loop is fine, like the filter's Ask, so long as every node reached can still get to an end.
        Set<String> canEnd = new HashSet<>();
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Node node : nodes.values()) {
                var exits = exits(node);
                if ((exits.isEmpty() || exits.stream().anyMatch(canEnd::contains)) && canEnd.add(node.id())) grew = true;
            }
        }
        assertTrue(canEnd.containsAll(reached), "trapped: " + reached.stream().filter(id -> !canEnd.contains(id)).toList());
    }

    private static void walk(Map<String, Node> nodes, Node node, Deque<String> path, Set<String> reached, int[] ends) {
        if (path.contains(node.id())) return;
        reached.add(node.id());
        path.push(node.id());
        var exits = exits(node);
        if (exits.isEmpty()) {
            ends[0]++;
            assertTrue(path.contains("fm.name"), "skipped fm.name: " + path);
            assertTrue(path.contains("fm.filter"), "skipped fm.filter: " + path);
        }
        for (String next : exits) walk(nodes, nodes.get(next), path, reached, ends);
        path.pop();
    }
}
