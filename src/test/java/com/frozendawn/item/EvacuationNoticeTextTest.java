package com.frozendawn.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CH-38's notice, restyled as a sheet: the words it always had, and only the new marks the owner approved. */
class EvacuationNoticeTextTest {
    /** The written book's text, line by line, as StarterBooks had it; its rule and stage direction are gone. */
    private static final List<String> ORIGINAL = List.of(
            "UNITED NATIONS", "EMERGENCY COUNCIL", "EVACUATION NOTICE",
            "Classification: PUBLIC", "Date: [SYSTEM DATE ERROR]", "THIS IS NOT A DRILL.",
            "By order of the United Nations Emergency Council, all civilians are to report to their nearest "
                    + "designated evacuation point IMMEDIATELY.",
            "SITUATION BRIEFING:",
            "The Orbital Resonance Stabilization Authority (ORSA) has confirmed that Earth's orbital trajectory has "
                    + "deviated beyond correctable parameters.",
            "Surface conditions will deteriorate rapidly.", "Immediate evacuation is the ONLY option.",
            "EVACUATION PROTOCOL:", "1. Gather essential supplies", "2. Proceed to the nearest shuttle terminal",
            "3. Follow all instructions from ORSA personnel", "4. Do NOT return home", "5. Do NOT wait for others",
            "PROJECT EXODUS is underway.", "14 billion people. 72 hours.",
            "DESTINATION:", "All evacuees will be transported to Mars Colony or Lunar Station facilities.",
            "Capacity has been expanded. Your safety is assured.",
            "Do not be alarmed.", "Do not panic.", "Proceed calmly.",
            "— UN Emergency Council", "— ORSA Coordination Division",
            "If you're reading this, we missed you.",
            "I went back. Three times. I checked every shelter, every basement, every locked door I could find.",
            "I'm sorry.", "— Kevin, Logistics Division");

    @Test
    void theNoticeKeepsItsWords() {
        JsonObject lang = lang();
        List<String> read = new ArrayList<>();
        read.add(text(lang, "council.1"));
        read.add(text(lang, "council.2"));
        read.add(text(lang, "title"));
        read.add(text(lang, "classification.label") + ": " + text(lang, "classification"));
        read.add(text(lang, "date.label") + ": " + text(lang, "date"));
        read.add(text(lang, "alert"));
        read.add(text(lang, "order"));
        for (List<EvacuationNotice.Line> page : EvacuationNotice.PAGES) {
            for (EvacuationNotice.Line line : page) {
                if (line.kind() != EvacuationNotice.Kind.GAP) read.add(text(lang, line.key()));
            }
        }
        assertEquals(ORIGINAL, read);
    }

    @Test
    void onlyTheApprovedMarksAreNew() {
        JsonObject lang = lang();
        assertEquals("Evac. point", text(lang, "point.label"));
        assertEquals("[AUTO-ASSIGN PENDING]", text(lang, "point"));
        assertEquals("EVN-2042-0001 // ISSUED: ORSA // CONCUR: UNEC", text(lang, "code"));
        assertEquals("REV. 9 // TEXT APPROVED 2071 // COPY 3,118,402,117", text(lang, "revision"));
        assertEquals("UN Emergency Evacuation Notice", lang.get("item.frozendawn.evacuation_notice").getAsString());
        Set<String> keys = lang.keySet().stream().filter(key -> key.startsWith(EvacuationNotice.KEY))
                .map(key -> key.substring(EvacuationNotice.KEY.length())).collect(Collectors.toSet());
        Set<String> used = new java.util.HashSet<>(EvacuationNotice.COVER);
        used.addAll(EvacuationNotice.CONTROL);
        EvacuationNotice.PAGES.forEach(page -> page.stream().filter(line -> line.key() != null)
                .forEach(line -> used.add(line.key())));
        assertEquals(used, keys, "Every notice key is drawn, and nothing else is written");
    }

    @Test
    void kevinsPageIsTheLastAndOnlyHis() {
        List<List<EvacuationNotice.Line>> pages = EvacuationNotice.PAGES;
        assertEquals(5, EvacuationNotice.pageCount(), "The cover, three printed pages and Kevin's");
        assertTrue(pages.get(pages.size() - 1).stream().allMatch(line -> line.kind() == EvacuationNotice.Kind.HAND
                || line.kind() == EvacuationNotice.Kind.GAP), "His page holds only his pen");
        assertTrue(pages.subList(0, pages.size() - 1).stream().flatMap(List::stream)
                .noneMatch(line -> line.kind() == EvacuationNotice.Kind.HAND), "And his pen is nowhere else");
    }

    private static String text(JsonObject lang, String key) {
        var value = lang.get(EvacuationNotice.KEY + key);
        if (value == null) throw new AssertionError("Missing " + EvacuationNotice.KEY + key);
        return value.getAsString();
    }

    private static JsonObject lang() {
        try (Reader reader = new InputStreamReader(EvacuationNoticeTextTest.class.getClassLoader()
                .getResourceAsStream("assets/frozendawn/lang/en_us.json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
