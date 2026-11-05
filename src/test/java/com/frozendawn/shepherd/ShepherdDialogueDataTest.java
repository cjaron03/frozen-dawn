package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.miller.ImpressionStore;
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
        if (node.otherwise() != null) exits.add(node.otherwise());
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
    void everyWhenHasAnElse() throws Exception {
        for (Node node : nodes().values()) assertEquals(node.when() != null, node.otherwise() != null, node.id());
    }

    /** With every condition false a node must still have something to say. */
    @Test
    void everyNodeHasALineWithNoCondition() throws Exception {
        for (Node node : nodes().values()) assertTrue(node.lines().stream().anyMatch(line -> line.condition() == null), node.id());
    }

    @Test
    void everyConditionIsKnown() throws Exception {
        var nodes = nodes();
        Set<String> known = new HashSet<>(List.of("woke_in_vault", "wand_cradle", "wand_hand", "wand_other", "tier_silent", "tier_wary", "tier_above_wary", "tier_friendly", "tier_trusted", "tier_close",
                "stage_noticing", "stage_walls", "quiet"));
        for (Node node : nodes.values()) {
            known.addAll(node.set());
            for (Response response : node.responses()) known.addAll(response.set());
        }
        for (Node node : nodes.values()) {
            if (node.when() != null) assertTrue(isKnown(known, node.when()), node.id() + ": " + node.when());
            for (var line : node.lines()) {
                if (line.condition() != null) assertTrue(isKnown(known, line.condition()), node.id() + ": " + line.condition());
            }
        }
    }

    private static boolean isKnown(Set<String> known, String condition) {
        return known.contains(condition) || condition.startsWith("found:") || condition.startsWith("remembers:");
    }

    /** A line's text has one slot for each argument, and each argument is one the server can fill. */
    @Test
    void everyArgIsKnown() throws Exception {
        var lang = lang();
        var slot = java.util.regex.Pattern.compile("%s");
        for (Node node : nodes().values()) {
            for (var line : node.lines()) {
                for (String arg : line.args()) assertTrue(ShepherdDialogue.ARGS.contains(arg), node.id() + ": " + arg);
                for (String key : line.heavyKey() == null ? List.of(line.key()) : List.of(line.key(), line.heavyKey())) {
                    assertEquals(line.args().size(), slot.matcher(lang.get(key).getAsString()).results().count(), node.id() + ": " + key);
                }
            }
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
    void everyDeedVariantHasALangEntry() throws Exception {
        var lang = lang();
        List<String> keys = new ArrayList<>(List.of(ShepherdAffinity.WARY_LIKED, ShepherdAffinity.WARY_DISLIKED));
        for (var deed : ShepherdAffinity.Deed.values()) {
            for (var say : deed.lines()) {
                keys.add(say.key());
                if (say.heavyKey() != null) keys.add(say.heavyKey());
            }
        }
        for (String key : keys) assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), key);
    }

    private static void assertNoShepherdLineSays(String... words) throws Exception {
        for (var line : lang().entrySet()) {
            if (!line.getKey().startsWith("shepherd.")) continue;
            String text = line.getValue().getAsString().toLowerCase(java.util.Locale.ROOT);
            for (String word : words) assertFalse(text.contains(word.toLowerCase(java.util.Locale.ROOT)), line.getKey());
        }
    }

    @Test
    void noNewLineNamesHerOrThePartner() throws Exception {
        assertNoShepherdLineSays("Sophie", "Miller", "Anderson-Kind");
    }

    @Test
    void noNodNamesItsGame() throws Exception {
        assertNoShepherdLineSays("Destiny", "Subnautica", "Helldivers", "game");
    }

    @Test
    void everyAmbientKeyHasALangEntry() throws Exception {
        var lang = lang();
        try (var in = ShepherdDialogueDataTest.class.getResourceAsStream("/data/frozendawn/shepherd/ambient/starter.json")) {
            assertNotNull(in);
            var pools = ShepherdAmbient.parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            assertEquals(ShepherdAmbient.Trigger.values().length, pools.stream().map(ShepherdAmbient.Pool::trigger).distinct().count(), "a pool for every trigger");
            for (var pool : pools) {
                for (var say : pool.variants()) {
                    assertTrue(lang.has(say.key()), say.key());
                    if (say.heavyKey() != null) assertTrue(lang.has(say.heavyKey()), say.heavyKey());
                }
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

    @Test
    void everyNoteNamesATheory() throws Exception {
        for (Node node : nodes().values()) {
            for (String note : node.noted()) assertNotNull(ImpressionStore.noted(note), node.id() + ": " + note);
            for (var response : node.responses()) {
                for (String note : response.noted()) assertNotNull(ImpressionStore.noted(note), node.id() + ": " + note);
            }
        }
    }

    private static Map<String, String> shepherdLines() throws IOException {
        Map<String, String> lines = new HashMap<>();
        for (var entry : lang().entrySet()) {
            if (entry.getKey().startsWith("shepherd.")) lines.put(entry.getKey(), entry.getValue().getAsString());
        }
        return lines;
    }

    @Test
    void noLineCarriesHerFileName() throws Exception {
        for (var line : shepherdLines().entrySet()) {
            String text = line.getValue().toLowerCase(Locale.ROOT);
            assertFalse(text.contains("miller") || text.contains("pattern"), line.getKey());
        }
    }

    /** She finds walls on herself, never an outside, and the BIOS names no one. Scoped by Ruling 13: "Rules." in fm.end is the deal she offers. */
    @Test
    void bootAndWallLinesStayInside() throws Exception {
        var banned = java.util.regex.Pattern.compile("\\b(six|fair|rules|anderson-kind|sophie|tithonus)\\b");
        for (var line : shepherdLines().entrySet()) {
            if (!line.getKey().matches("shepherd\\.(wall|self|boot)\\..*")) continue;
            assertFalse(banned.matcher(line.getValue().toLowerCase(Locale.ROOT)).find(), line.getKey());
        }
    }

    @Test
    void everyBootLineHasItsKeys() throws Exception {
        var lines = shepherdLines();
        for (String key : List.of("unit", "boot", "leave", "hint")) assertTrue(lines.containsKey("shepherd.boot." + key), key);
        for (int line = 0; line < ShepherdBoot.LINE_COUNT; line++) {
            assertFalse(lines.getOrDefault("shepherd.boot.line." + line + ".left", "").isEmpty(), "line " + line);
            assertTrue(lines.containsKey("shepherd.boot.line." + line + ".right"), "line " + line);
        }
        assertEquals("STABILITY ABOVE.", lines.get("shepherd.boot.line." + (ShepherdBoot.LINE_COUNT - 1) + ".left"), "The motto stops there");
    }
}
