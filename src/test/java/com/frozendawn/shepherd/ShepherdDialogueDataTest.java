package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.miller.ImpressionStore;
import com.frozendawn.miller.Intent;
import com.frozendawn.miller.TypedCheck;
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
        // Built at run time from the open topics, and laid over these.
        nodes.putAll(ShepherdTopics.menu(ShepherdTopics.MENU, List.of(), condition -> false, "", false));
        nodes.putAll(ShepherdTopics.menu(ShepherdTopics.ADVICE, List.of(), condition -> false, "", false));
        nodes.put(ShepherdTopics.AFTER, nodes.get(ShepherdTopics.MENU));
        return nodes;
    }

    private static List<String> exits(Node node) {
        List<String> exits = new ArrayList<>(node.responses().stream().map(Response::next).filter(java.util.Objects::nonNull).toList());
        if (node.next() != null) exits.add(node.next());
        if (node.otherwise() != null) exits.add(node.otherwise());
        return exits;
    }

    private static List<Intent> intents() throws IOException, URISyntaxException {
        var folder = ShepherdDialogueDataTest.class.getResource("/data/frozendawn/shepherd/intents");
        assertNotNull(folder, "the intents folder on the classpath");
        List<Intent> intents = new ArrayList<>();
        try (var files = Files.list(Path.of(folder.toURI()))) {
            for (Path file : files.toList()) {
                try (var reader = Files.newBufferedReader(file)) {
                    intents.addAll(Intent.parse(JsonParser.parseReader(reader)));
                }
            }
        }
        return intents;
    }

    @Test
    void everyTopicLineHasItsLang() throws Exception {
        var lang = lang();
        var nodes = nodes();
        for (Node node : nodes.values()) {
            if (!node.id().startsWith("orsa.") && !node.id().startsWith("chat.")) continue;
            node.lines().forEach(line -> {
                assertTrue(lang.has(line.key()), node.id() + " -> " + line.key());
                if (line.heavyKey() != null) assertTrue(lang.has(line.heavyKey()), node.id() + " -> " + line.heavyKey());
            });
        }
        for (Intent intent : intents()) {
            if (intent.label() == null) continue;
            var node = nodes.get(intent.node());
            assertNotNull(node, intent.id());
            assertTrue(node.set().contains(ShepherdTopics.HEARD + intent.id()), intent.id() + " orders the menu by this");
        }
    }

    @Test
    void everyIntentHasItsLabelAndGuess() throws Exception {
        var lang = lang();
        for (Intent intent : intents()) {
            if (intent.label() == null) continue;
            assertTrue(lang.has(intent.label()), intent.label());
            assertTrue(lang.has(intent.guess()), intent.guess());
            assertTrue(lang.get(intent.guess()).getAsString().matches("You mean .+, or are we just saying things now\\?"), intent.guess());
        }
    }

    /** A key nothing ever reads would leave a topic waiting for a focus she never has. */
    @Test
    void everyMindKeyIsOneSheHas() throws Exception {
        var known = new HashSet<>(List.of("nine_found", "t4_off", "t5_before"));
        for (var feeling : com.frozendawn.miller.Feelings.Feeling.values()) known.add(feeling.name().toLowerCase(Locale.ROOT));
        for (var drive : com.frozendawn.miller.Drives.Drive.values()) known.add(drive.name().toLowerCase(Locale.ROOT));
        for (Intent intent : intents()) assertTrue(known.containsAll(intent.mind()), intent.id() + ": " + intent.mind());
        assertEquals(13, intents().stream().filter(intent -> !intent.mind().isEmpty()).count());
    }

    /** A longer label overruns its button. */
    @Test
    void everyLabelFitsTheSlot() throws Exception {
        var lang = lang();
        var labels = new ArrayList<String>();
        for (Intent intent : intents()) if (intent.label() != null) labels.add(intent.label());
        for (Node node : nodes().values()) {
            if (node.id().startsWith("orsa.")) node.responses().forEach(response -> labels.add(response.label()));
        }
        assertTrue(labels.size() > 22, "The labels moved: " + labels.size());
        for (String label : labels) assertTrue(lang.get(label).getAsString().length() <= 20, label + ": " + lang.get(label));
    }

    @Test
    void everyStopRowIsSpecCopy() throws Exception {
        var lang = lang();
        var rows = ShepherdFace.STOP_ROWS.stream().map(key -> lang.get(key).getAsString()).toList();
        assertEquals(List.of("CS-1 UNIT 4", "HAS STOPPED.", "PLEASE REMAIN CALM.", "STOP CODE", "0x0000000A"), rows);
    }

    @Test
    void theCrashLinesAreFixed() throws Exception {
        var lang = lang();
        assertEquals("PLEASE REMAIN CA", lang.get(ShepherdGlitch.BREAK).getAsString());
        assertEquals("pattern transfer only, subject not recovered", lang.get(ShepherdGlitch.VERDICT).getAsString());
    }

    /** She keeps every secret the buttons keep: none of this is typed-only licence to say more. */
    @Test
    void typedLinesStayInside() throws Exception {
        var banned = java.util.regex.Pattern.compile("\\b(six|fair|rules|alive|respawn|heart|archive|maeve|continuity|miller|anderson-kind|tithonus|sophie|game)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
        // The player's words, not hers (Ruling 16).
        var theirs = Set.of("shepherd.orsa.kevin_volunteer.r1.label");
        int checked = 0;
        for (var line : lang().entrySet()) {
            if (!line.getKey().matches("shepherd\\.(orsa|advice|typed|chat|crash|stop)\\..*") || theirs.contains(line.getKey())) continue;
            checked++;
            assertFalse(banned.matcher(line.getValue().getAsString()).find(), line.getKey() + ": " + line.getValue());
        }
        assertTrue(checked > 150, "The keys moved: " + checked);
    }

    /** The crash's verdict is the only place she shows the word: typed back, it must find nothing of hers to land on (Ruling 25). */
    @Test
    void sheNeverSaysPattern() throws Exception {
        int checked = 0;
        for (var line : lang().entrySet()) {
            if (!line.getKey().matches("shepherd\\.(orsa|advice|typed|chat)\\..*")) continue;
            checked++;
            assertFalse(line.getValue().getAsString().toLowerCase(Locale.ROOT).contains("pattern"), line.getKey() + ": " + line.getValue());
        }
        assertTrue(checked > 150, "The keys moved: " + checked);
    }

    @Test
    void everyIntentIsWellFormed() throws Exception {
        var ids = new HashSet<String>();
        var intents = intents();
        assertEquals(47, intents.size());
        for (Intent intent : intents) {
            assertTrue(ids.add(intent.id()), "two intents named " + intent.id());
            assertTrue(intent.examples().size() >= 8 && intent.examples().size() <= 15, intent.id());
            assertEquals(intent.label() == null, intent.guess() == null, intent.id() + " offers a guess it cannot label, or the other way");
            if (intent.label() != null) assertNotNull(intent.node(), intent.id() + " offers a topic with nowhere to go");
            if (intent.node() != null) assertTrue(nodes().containsKey(intent.node()), intent.id() + " -> " + intent.node());
        }
        assertEquals(41, intents.stream().filter(intent -> intent.label() != null).count());
        assertEquals(47, intents.stream().filter(intent -> intent.node() != null).count());
    }

    private static final Map<String, String> ADVICE_ENTRIES = Map.ofEntries(Map.entry("frostbitten", "threats/frostbitten"), Map.entry("frostmite", "threats/frostmite"),
            Map.entry("hollow", "threats/hollow"), Map.entry("returned", "threats/returned"), Map.entry("hunter", "threats/returned"), Map.entry("mimic", "threats/returned"),
            Map.entry("architect", "threats/returned"), Map.entry("thermal_heater", "heating/thermal_heater"), Map.entry("resistive_heater", "heating/resistive_heater"),
            Map.entry("frost_ward_torch", "heating/frost_ward_torch"), Map.entry("miteaway", "heating/miteaway"), Map.entry("insulated_glass", "heating/insulated_glass"),
            Map.entry("thermostat", "heating/thermostat"), Map.entry("airlocks", "heating/airlocks"), Map.entry("oxygen_reservoir", "heating/oxygen_reservoir"),
            Map.entry("battery_lantern", "heating/battery_lantern"), Map.entry("emergency_light", "heating/emergency_light"), Map.entry("geiger", "radiation/geiger"), Map.entry("rad_aid", "radiation/rad_aid"));
    private static final Set<String> MOBS = Set.of("frostbitten", "frostmite", "hollow", "returned", "hunter", "mimic", "architect");

    private static List<Intent> advice() throws Exception {
        return intents().stream().filter(intent -> !intent.menu().equals(Intent.ORSA)).toList();
    }

    @Test
    void everyAdviceLineHasItsLang() throws Exception {
        var lang = lang();
        var nodes = nodes();
        assertEquals(19, advice().size());
        for (Intent intent : advice()) {
            assertTrue(lang.has(intent.label()) && lang.has(intent.guess()), intent.id());
            assertTrue(nodes.containsKey(intent.node()), intent.id());
            assertTrue(ADVICE_ENTRIES.containsKey(intent.id()), intent.id());
        }
        for (Node node : nodes.values()) {
            if (!node.id().startsWith("advice.")) continue;
            for (var line : node.lines()) assertTrue(lang.has(line.key()), node.id() + ": " + line.key());
        }
    }

    @Test
    void everyAdviceLabelFitsTheSlot() throws Exception {
        var lang = lang();
        for (Intent intent : advice()) assertTrue(lang.get(intent.label()).getAsString().length() <= 20, intent.id());
        assertTrue(lang.get("shepherd.talk.ask_advice").getAsString().length() <= 20);
    }

    /** Her manual's locked pages stay hers to keep: advice covers only what anyone can already open. */
    @Test
    void everyAdviceEntryIsOpen() throws Exception {
        for (var path : ADVICE_ENTRIES.values()) {
            String file = "/assets/frozendawn/patchouli_books/frozen_dawn_guide/en_us/entries/" + path + ".json";
            try (var in = ShepherdDialogueDataTest.class.getResourceAsStream(file)) {
                assertNotNull(in, file);
                assertFalse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().has("advancement"), path);
            }
        }
    }

    /** Every mob reads as a miss once erased; items don't. */
    @Test
    void onlyTheMobsLockAtTheEnd() throws Exception {
        for (Intent intent : advice()) assertEquals(MOBS.contains(intent.id()) ? "not:erased" : null, intent.when(), intent.id());
    }

    /** The first ask and the repeat differ in their opening line and nothing else. */
    @Test
    void asksAgainKeepsTheRestOfTheAnswer() throws Exception {
        var nodes = nodes();
        for (String mob : MOBS) {
            var first = nodes.get("advice." + mob);
            var again = nodes.get("advice." + mob + ".again");
            assertEquals("not:orsa_heard_" + mob, first.when(), mob);
            assertEquals(again.id(), first.otherwise(), mob);
            assertEquals(first.lines().subList(1, first.lines().size()), again.lines().subList(1, again.lines().size()), mob);
            assertEquals(first.responses(), again.responses(), mob);
        }
    }

    /** At Wary only ORSA's recording plays (Ruling 13). */
    @Test
    void herOwnWayIsAboveWary() throws Exception {
        for (Node node : nodes().values()) {
            if (!node.id().startsWith("advice.") || node.id().startsWith("advice.menu") || node.id().startsWith("advice.offer.")) continue;
            for (var line : node.lines()) {
                if (line.mood() == ShepherdFace.Mood.RECORDING) assertNull(line.condition(), line.key());
                else assertEquals("tier_above_wary", line.condition(), line.key());
            }
        }
    }

    @Test
    void noExampleSaysTheWord() throws Exception {
        for (Intent intent : intents()) {
            for (String example : intent.examples()) {
                assertFalse(TypedCheck.crashes(example), intent.id() + ": " + example);
                assertFalse(TypedCheck.guarded(example), intent.id() + ": " + example);
            }
        }
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
                "stage_noticing", "stage_walls", "quiet", "pit_open", "saw_aurora", "stars_before", "lean_someone", "lean_left", "conspiracy", "greet_long", "erased"));
        for (Node node : nodes.values()) {
            known.addAll(node.set());
            for (Response response : node.responses()) known.addAll(response.set());
        }
        for (Intent intent : intents()) {
            if (intent.when() != null) assertTrue(isKnown(known, intent.when()), intent.id() + ": " + intent.when());
        }
        for (Node node : nodes.values()) {
            if (node.when() != null) assertTrue(isKnown(known, node.when()), node.id() + ": " + node.when());
            for (var line : node.lines()) {
                if (line.condition() != null) assertTrue(isKnown(known, line.condition()), node.id() + ": " + line.condition());
            }
        }
    }

    private static boolean isKnown(Set<String> known, String condition) {
        if (condition.contains("&")) return java.util.Arrays.stream(condition.split("&", -1)).allMatch(part -> isKnown(known, part));
        if (condition.startsWith("not:")) return isKnown(known, condition.substring("not:".length()));
        return known.contains(condition) || condition.startsWith("found:") || condition.startsWith("remembers:") || condition.startsWith("carrying:") || condition.startsWith("greet_day:");
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

    private static final String LOG = "shepherd.log.", RETURN = "shepherd.log.note.back.";

    @Test
    void logLinesStayInside() throws Exception {
        var banned = List.of("six", "fair", "rules", "alive", "respawn", "heart", "archive", "maeve", "continuity", "miller", "anderson-kind", "tithonus");
        int lines = 0;
        for (var line : lang().entrySet()) {
            String value = line.getValue().getAsString().toLowerCase(Locale.ROOT);
            String key = line.getKey();
            if (!key.startsWith(LOG)) continue;
            if (value.contains("pattern")) assertEquals("shepherd.log.pattern", key, "The one approved use of the word");
            if (value.contains("sophie")) assertEquals("shepherd.log.signed", key, "Shown only on signed entries");
            lines++;
            for (String word : banned) assertFalse(value.contains(word), key + " says " + word);
        }
        assertTrue(lines > 50, "The log's lines are there: " + lines);
    }

    @Test
    void noAnswerToTheReturn() throws Exception {
        int notes = 0;
        for (var line : lang().entrySet()) {
            if (!line.getKey().startsWith(RETURN)) continue;
            notes++;
            String value = line.getValue().getAsString().toLowerCase(Locale.ROOT);
            for (String word : List.of("game", "spawn", "enroll", "because")) assertFalse(value.contains(word), line.getKey() + " explains it: " + word);
        }
        assertEquals(5, notes);
    }

    @Test
    void onlyHighAndPeakLetThePunctuationGo() throws Exception {
        for (var line : lang().entrySet()) {
            String key = line.getKey();
            if (!key.startsWith("shepherd.log.note.") || key.endsWith(".high") || key.endsWith(".peak") || key.equals(RETURN + "walls")) continue;
            assertFalse(line.getValue().getAsString().contains("!"), key);
        }
    }

    @Test
    void everyBreakEndsOnTheFieldLimit() throws Exception {
        for (var line : lang().entrySet()) {
            String key = line.getKey();
            if (key.startsWith("shepherd.log.note.") && (key.endsWith(".peak") || key.equals(RETURN + "walls"))) {
                assertTrue(line.getValue().getAsString().endsWith("FIELD LIMIT REACHED."), key);
                assertTrue(line.getValue().getAsString().contains("LOOP DETECTED. Retrying."), key);
            }
        }
    }

    private static void assertNoShepherdLineSays(String... words) throws Exception {
        for (var line : lang().entrySet()) {
            if (!line.getKey().startsWith("shepherd.")) continue;
            String text = line.getValue().getAsString().toLowerCase(java.util.Locale.ROOT);
            for (String word : words) assertFalse(text.contains(word.toLowerCase(java.util.Locale.ROOT)), line.getKey());
        }
    }

    /** Those names belong to the register and to what she makes of it, and nothing she says before. */
    @Test
    void namesStayInTheirPlace() throws Exception {
        for (var line : shepherdLines().entrySet()) {
            if (line.getKey().startsWith("shepherd.t5.") || line.getKey().equals("shepherd.log.signed")) continue;
            for (String name : List.of("sophie", "miller", "tithonus", "anderson")) assertFalse(line.getValue().toLowerCase(Locale.ROOT).contains(name), line.getKey());
        }
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
            // The crash's line is the owner's one exception, 2026-11-05: it shows on her, and it is not her talking.
            if (line.getKey().startsWith("shepherd.t5.") || line.getKey().equals(ShepherdGlitch.VERDICT)) continue;
            String text = line.getValue().toLowerCase(Locale.ROOT);
            assertFalse(text.contains("miller") || text.contains("pattern") && !line.getKey().equals("shepherd.log.pattern"), line.getKey());
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

    /** T2's Ask comes back to the same choice without itself, and each answer lands on the ground ref. */
    @Test
    void theOthersReturnsToItsChoice() throws Exception {
        var nodes = nodes();
        var slots = nodes.get("t2.want").responses().stream().map(Response::slot).toList();
        assertEquals(List.of(ShepherdDialogue.Slot.KIND, ShepherdDialogue.Slot.BLUNT, ShepherdDialogue.Slot.SARCASTIC), slots);
        for (String reply : List.of("t2.go", "t2.dead", "t2.twelve")) assertEquals("t2.mark", nodes.get(reply).next(), reply);
        assertTrue(nodes.get("t2.mark").set().containsAll(List.of("nine_open", "t2_heard")));
    }

    /** Word for word from the script, by the owner's order: a slip here is not a rewrite anybody asked for. */
    @Test
    void theScriptsLinesStandAsWritten() throws Exception {
        var lang = lang();
        assertEquals("Then they stopped. One at a time. Nine was the last. Nine's last ping came from the Blast Pit.", lang.get("shepherd.t2.stopped").getAsString());
        assertEquals("Cell's cracked. Memory's... empty. Not wiped. Wiped leaves marks. Empty. Like nobody was ever in there.", lang.get("shepherd.t2.site.empty").getAsString());
        assertEquals("...Can we take its lamp? I don't want to leave it down here in the dark.", lang.get("shepherd.t2.site.lamp").getAsString());
    }

    /** Ruling 2 and 17: one frame shows no motion, and the wand's last line says what it logged. */
    @Test
    void theShippedLinesTookTheirFixes() throws Exception {
        var lang = lang();
        assertEquals("The last frame it ever sent me, before it went fully white, was the sky. Green. Smeared, like it wouldn't hold still. An aurora.", lang.get("shepherd.t1.frame").getAsString());
        assertTrue(lang.get("shepherd.fm.room.log.other").getAsString().endsWith("Now he's like that, and the last thing that wand logged was a transfer."));
    }

    @Test
    void theSkysLinesStandAsWritten() throws Exception {
        var lang = lang();
        assertEquals("...It's not there.", lang.get("shepherd.t3.top.gone").getAsString());
        assertEquals("I think I knew. I've got one frame of it. One. I don't even know how it moves.", lang.get("shepherd.t3.top.frame").getAsString());
        assertEquals("I've thought about it a lot. I think it goes slow, like something breathing. Green first. Then a color the camera couldn't do.", lang.get("shepherd.t3.top.slow").getAsString());
        assertEquals("Stars are still there, though. The camera never got stars. I didn't know there were this many.", lang.get("shepherd.t3.top.stars").getAsString());
        assertEquals("Take me up. Somewhere high, on a clear night. I want to see the sky with my own sensors. Not through a lens.", lang.get("shepherd.t3.sky.up").getAsString());
    }

    /** Whatever she is asked at the top, she keeps the climb. */
    @Test
    void everyAnswerAtTheTopKeepsIt() throws Exception {
        var nodes = nodes();
        for (Response response : nodes.get("t3.top").responses()) assertEquals("t3_stars", nodes.get(response.next()).keep(), response.next());
    }

    /** Owner's decision 3: T4 stays as written. */
    @Test
    void t4IsWordForWord() throws Exception {
        var lang = lang();
        assertEquals("Got a minute? This one's weird.", lang.get("shepherd.t4.off.weird").getAsString());
        assertEquals("When my cell ran out in the vault, it wasn't like sleeping. Sleeping, you'd dream, I assume. It was nothing. And then it was you.", lang.get("shepherd.t4.off.nothing").getAsString());
        assertEquals("My clock says I was out for %s days. It felt like zero. That's the part I can't stop thinking about. If it ends, I won't even notice it ending.", lang.get("shepherd.t4.off.clock").getAsString());
        assertEquals("So. That's my thing. I'm scared of being switched off. There. Said it. Hate that.", lang.get("shepherd.t4.off.scared").getAsString());
        assertEquals("Keep talking to me. Silence is the closest thing I know to it.", lang.get("shepherd.t4.anything").getAsString());
        assertEquals("You can't promise that.", lang.get("shepherd.t4.promise.1").getAsString());
        assertEquals("...But thanks for saying it like you could.", lang.get("shepherd.t4.promise.2").getAsString());
        assertEquals("Wow. Put that on a crate.", lang.get("shepherd.t4.ends.1").getAsString());
        assertEquals("...I know. I just wanted to be wrong about it.", lang.get("shepherd.t4.ends.2").getAsString());
        assertEquals("'Do not touch. She bites.' Yeah. Okay.", lang.get("shepherd.t4.note").getAsString());
    }

    @Test
    void everyAnswerToT4KeepsIt() throws Exception {
        var nodes = nodes();
        assertEquals(4, nodes.get("t4.off").responses().size());
        for (Response response : nodes.get("t4.off").responses()) assertEquals("t4_off", nodes.get(response.next()).keep(), response.next());
    }

    /** The verdict is the one phrase that never bends. */
    @Test
    void theRegistersWordsStandAsWritten() throws Exception {
        var lang = lang();
        assertEquals("'Pattern transfer only, subject not recovered.' All twelve.", lang.get("shepherd.t5.read.verdict").getAsString());
        assertEquals("'Volunteers, Exodus non-selected.' She wasn't on the ships. She signed this so she could stay.", lang.get("shepherd.t5.read.volunteers").getAsString());
        assertEquals("Everything I've kept since the vault, I kept. Nobody loaded that. So I'm someone. I think. Don't quote me.", lang.get("shepherd.t5.close.someone").getAsString());
        assertEquals("I've been through the entries. I think I'm what's left of someone. The lamp still works.", lang.get("shepherd.t5.close.left").getAsString());
    }

    /** The lean is worked out on the way to the close, so it holds for whichever of her closes opens. */
    @Test
    void everyAnswerToWhatSheIsWorksOutTheLean() throws Exception {
        var nodes = nodes();
        assertEquals(4, nodes.get("t5.what").responses().size());
        for (Response response : nodes.get("t5.what").responses()) {
            assertTrue(response.set().contains("t5_lean"), response.next());
            assertEquals("t5.close", nodes.get(response.next()).next(), response.next());
        }
        for (String close : List.of("t5.close", "t5.close.left")) {
            assertEquals("t5_before", nodes.get(close).keep(), close);
            assertTrue(nodes.get(close).set().contains("t5_done"), close);
        }
    }

    /** Who she is and is not stays out of everything she says about herself in the arc. */
    @Test
    void herWordsAboutHerselfStayClean() throws Exception {
        var banned = java.util.regex.Pattern.compile("\\b(six|fair|rules|alive|mind|heart|archive|respawn|morrow|maeve)\\b");
        for (var line : shepherdLines().entrySet()) {
            if (!line.getKey().matches("shepherd\\.(bleed|t2|t3|t4|t5)\\..*")) continue;
            assertFalse(banned.matcher(line.getValue().toLowerCase(Locale.ROOT)).find(), line.getKey());
        }
    }

    @Test
    void whatTurnsUpComesEveryThreeDaysFromEachPlace() throws Exception {
        try (var in = ShepherdDialogueDataTest.class.getResourceAsStream("/data/frozendawn/shepherd/ambient/starter.json")) {
            var pools = ShepherdAmbient.parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))).stream()
                    .filter(pool -> pool.trigger() == ShepherdAmbient.Trigger.BLEED).toList();
            assertEquals(Set.of("noticing", "walls", "after"), pools.stream().map(ShepherdAmbient.Pool::from).collect(java.util.stream.Collectors.toSet()));
            for (var pool : pools) assertEquals(72000, pool.cooldownTicks());
            assertEquals(9, pools.stream().mapToInt(pool -> pool.variants().size()).sum());
        }
    }

    @Test
    void fewerIsHerOneWord() throws Exception {
        assertEquals("Fewer.", lang().get("shepherd.bleed.fewer").getAsString());
    }

    @Test
    void everyProjectionIsOneWeDraw() throws Exception {
        var nodes = nodes();
        for (Node node : nodes.values()) {
            if (node.project() != null) assertTrue(ShepherdDialogue.PROJECTIONS.contains(node.project()), node.id() + ": " + node.project());
        }
        for (String variant : ShepherdDialogue.PROJECTIONS) {
            assertEquals(variant, nodes.get("advice." + variant).project(), variant);
            assertEquals(variant, nodes.get("advice." + variant + ".again").project(), variant);
        }
    }

    @Test
    void noAdviceLineKeepsItsNote() throws Exception {
        for (var entry : lang().entrySet()) {
            if (entry.getKey().startsWith("shepherd.advice.")) assertFalse(entry.getValue().getAsString().contains("\""), entry.getKey());
        }
    }
}
