package com.frozendawn.lore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the text that must be in worlds before the payoff exists. */
class PayoffCluesTest {
    private static final String ENTRIES =
            "assets/frozendawn/patchouli_books/frozen_dawn_guide/en_us/entries/";

    private static final List<String> EVA_FAMILIES = List.of(
            "hud.frozendawn.emergency_eva.",
            "hud.frozendawn.continuity.",
            "tooltip.frozendawn.emergency_eva.",
            "ui.frozendawn.suit.emergency_eva",
            "ui.frozendawn.suit.continuity",
            "subtitle.frozendawn.ui.suit.emergency_eva");

    /** Generated voice: the subtitle and toast must match the audio. */
    private static final Map<String, String> VOICED = Map.of(
            "ui.frozendawn.suit.emergency_eva_active",
            "Emergency EVA active. Ten-minute oxygen reserve. Fifteen-minute service limit.",
            "subtitle.frozendawn.ui.suit.emergency_eva_active",
            "Emergency EVA active. Ten-minute oxygen reserve. Fifteen-minute service limit.",
            "ui.frozendawn.suit.emergency_eva_ambient",
            "Breathable atmosphere detected. Ambient intake enabled. Reserve oxygen isolated. Emergency thermal support remains active.",
            "subtitle.frozendawn.ui.suit.emergency_eva_ambient",
            "Breathable atmosphere detected. Ambient intake enabled. Reserve oxygen isolated. Emergency thermal support remains active.");

    private static final Pattern DEATH_SCREEN_WORDS =
            Pattern.compile("(?i)\\b(remaining|active)\\b");

    @Test
    void evaTextNeverSaysRemainingOrActive() {
        List<String> clashes = new ArrayList<>();
        for (var e : lang().entrySet()) {
            String key = e.getKey();
            if (VOICED.containsKey(key)
                    || EVA_FAMILIES.stream().noneMatch(key::startsWith)) {
                continue;
            }
            if (DEATH_SCREEN_WORDS.matcher(e.getValue().getAsString()).find()) {
                clashes.add(key);
            }
        }
        assertTrue(clashes.isEmpty(), "EVA text reads as the death screen: " + clashes);
    }

    @Test
    void voicedEvaLinesMatchTheirRecordings() {
        JsonObject lang = lang();
        for (var e : VOICED.entrySet()) {
            assertEquals(e.getValue(), lang.get(e.getKey()).getAsString(), e.getKey());
        }
    }

    static final String FORECAST = "Projected enrollees remaining at terminus: 1.";
    static final String KEVIN = "Kevin remains a registered enrollee. His seat is being held.";
    static final String SIGNAL_2071 = String.join("\n",
            "[FRAGMENT \u2014 TIMESTAMP INTEGRITY: VERIFIED]",
            "Deep-band transponder signature received.",
            "Duration: 90 minutes.",
            "Origin: surface, below shelf depth.",
            "Date: 2071.",
            "Flagged: forwarded to seventh floor.");

    @Test
    void theForecastCountsOne() {
        assertTrue(pageText("orsa_intel/projected_timeline.json", 7)
                .startsWith(FORECAST + "$(br2)You are the unluckiest person alive"));
    }

    @Test
    void kevinIsARegisteredEnrollee() {
        assertTrue(pageText("orsa_intel/orsa_bulletin.json", 10)
                .startsWith(KEVIN + "$(br2)"));
    }

    @Test
    void the2071SignalWasLogged() {
        JsonObject log = entry("orsa_intel/satellite_log.json");
        assertEquals(9, log.getAsJsonArray("pages").size());
        JsonObject fragment = page(log, 2);
        assertFalse(fragment.has("title"));
        assertEquals(SIGNAL_2071, fragment.get("text").getAsString()
                .replace("$(bold)", "").replace("$()", "").replace("$(br)", "\n"));
        assertEquals("Relay Network Status", page(log, 3).get("title").getAsString());
    }

    @Test
    void theUplinkComesInPhasesThreeToFour() {
        assertTrue(pageText("getting_started/start_here.json", 3).contains("$(l:signals/uplink)"));
        assertFalse(pageText("getting_started/start_here.json", 6).contains("$(l:signals/uplink)"));
    }

    @Test
    void noClueSaysTheWord() {
        for (String clue : clueTexts()) {
            assertFalse(clue.toLowerCase(java.util.Locale.ROOT).contains("continuity"), clue);
        }
    }

    /** Every page that carries a clue string from this branch. */
    private static List<String> clueTexts() {
        return List.of(
                pageText("orsa_intel/projected_timeline.json", 7),
                pageText("orsa_intel/orsa_bulletin.json", 10),
                pageText("orsa_intel/satellite_log.json", 2));
    }

    private static String pageText(String path, int index) {
        return page(entry(path), index).get("text").getAsString();
    }

    private static JsonObject page(JsonObject entry, int index) {
        return entry.getAsJsonArray("pages").get(index).getAsJsonObject();
    }

    static JsonObject lang() {
        return load("assets/frozendawn/lang/en_us.json");
    }

    static JsonObject entry(String path) {
        return load(ENTRIES + path);
    }

    private static JsonObject load(String path) {
        var stream = PayoffCluesTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        return JsonParser.parseReader(new InputStreamReader(
                stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
