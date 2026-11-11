package com.frozendawn.homo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HeartMemoryLedgerTest {
    private static final String NODE = "overlay.frozendawn.heart_memory.node_5.";

    @Test
    void theMainRunReadsAsTheSpec() {
        List<HeartMemoryLedger.MemoryLine> lines =
                new HeartMemoryLedger(4, 12, 31, 3, 38, 5, false).lines();

        assertEquals(List.of("arrived", "came_back", "crossed", "lost", "you_did", "erased"),
                keys(lines));
        assertEquals(List.of(4), lines.get(0).args());
        assertEquals(List.of(12), lines.get(1).args());
        assertEquals(List.of(31), lines.get(2).args());
        assertEquals(List.of(38, new HeartMemoryLedger.MemoryLine("overlay.frozendawn.number.3")),
                lines.get(3).args());
        assertEquals("Three", lang().get("overlay.frozendawn.number.3").getAsString());
    }

    @Test
    void aPeacefulRunCountsDeaths() {
        List<HeartMemoryLedger.MemoryLine> lines =
                new HeartMemoryLedger(4, 12, 0, 0, 0, 7, false).lines();
        assertEquals(List.of("arrived", "came_back", "watched", "erased"), keys(lines));
        assertEquals(List.of(7, 7), lines.get(2).args());

        assertEquals(List.of("arrived", "came_back", "watched_once", "erased"),
                keys(new HeartMemoryLedger(4, 12, 0, 0, 0, 1, false).lines()));
        assertEquals(List.of("arrived", "came_back", "erased"),
                keys(new HeartMemoryLedger(4, 12, 0, 0, 0, 0, false).lines()));
    }

    @Test
    void aMassacreSaysNone() {
        List<HeartMemoryLedger.MemoryLine> lines =
                new HeartMemoryLedger(4, 12, 0, 9, 40, 5, true).lines();

        assertEquals(List.of("arrived", "came_back", "lost_all", "you_did", "erased"),
                keys(lines));
        assertEquals(List.of(40), lines.get(2).args());
    }

    @Test
    void aStrangerGetsOnlyTheLastLine() {
        assertEquals(List.of("erased"),
                keys(new HeartMemoryLedger(0, 0, 0, 0, 0, 0, false).lines()));
    }

    @Test
    void oneCameBackIsDropped() {
        assertEquals(List.of("arrived", "erased"),
                keys(new HeartMemoryLedger(4, 1, 0, 0, 0, 0, false).lines()));
    }

    @Test
    void oneCasualtyIsAWordAndManyAreNumerals() {
        assertEquals(new HeartMemoryLedger.MemoryLine("overlay.frozendawn.number.1"),
                new HeartMemoryLedger(4, 0, 9, 1, 9, 0, false).lines().get(2).args().get(1));
        assertEquals(13,
                new HeartMemoryLedger(4, 0, 9, 13, 9, 0, false).lines().get(2).args().get(1));
    }

    @Test
    void daysStartAtOne() {
        assertEquals(1, HeartMemoryLedger.day(0L));
        assertEquals(1, HeartMemoryLedger.day(23_999L));
        assertEquals(2, HeartMemoryLedger.day(24_000L));
    }

    @Test
    void nodeFiveTextIsTheSpec() throws IOException {
        JsonObject lang = lang();
        Map<String, String> expected = Map.of(
                "arrived", "Day %s. You came down out of the cold. They felt your warmth before they saw you.",
                "came_back", "You came back %s times. They waited every time.",
                "crossed", "Day %s. You crossed the line. They wrote it down.",
                "lost", "Day %s. %s of them did not come back.",
                "lost_all", "Day %s. None of them came back.",
                "you_did", "You did.",
                "watched", "They watched you die %s times. They watched you return %s times.",
                "watched_once", "They watched you die once. They watched you return once.",
                "erased", "Now they do not remember you.");
        expected.forEach((key, text) -> {
            String value = lang.get(NODE + key).getAsString();
            assertEquals(text, value, key);
            String lower = value.toLowerCase(Locale.ROOT);
            assertFalse(lower.contains("respawn") || lower.contains("enrollee")
                    || lower.contains("continuity"), key);
        });
        String[] words = {"One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight",
                "Nine", "Ten", "Eleven", "Twelve"};
        for (int n = 1; n <= 12; n++) {
            assertEquals(words[n - 1], lang.get("overlay.frozendawn.number." + n).getAsString());
        }
        for (String gone : List.of("variant_0", "variant_1", "variant_2", "variant_3",
                "visits", "casualties")) {
            assertFalse(lang.has(NODE + gone), gone);
        }
    }

    private static List<String> keys(List<HeartMemoryLedger.MemoryLine> lines) {
        return lines.stream().map(line -> line.key().substring(NODE.length())).toList();
    }

    private static JsonObject lang() {
        try (var in = HeartMemoryLedgerTest.class.getResourceAsStream(
                "/assets/frozendawn/lang/en_us.json")) {
            assertNotNull(in, "en_us.json on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
