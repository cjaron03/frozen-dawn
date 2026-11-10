package com.frozendawn.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The owner-approved look of CH-37's opening flicker (ink mockups, 2026-11-09). */
class ThaevenLoopLookTest {
    private static final Set<Integer> DROPOUTS = Set.of(28, 26, 13);

    @Test
    void rawLinesShowOnlyInTheOpeningLeadIn() {
        for (int t = 11; t <= 30; t++) {
            assertEquals(!DROPOUTS.contains(t), OrsaAwakeningIntro.rawLinesShown(false, t), "Opening, tick " + t);
            assertEquals(DROPOUTS.contains(t), OrsaAwakeningIntro.rawLinesFracture(false, t), "Fracture, tick " + t);
        }
        for (int t : new int[] {40, 31, 10, 0}) {
            assertFalse(OrsaAwakeningIntro.rawLinesShown(false, t), "Outside the window, tick " + t);
            assertFalse(OrsaAwakeningIntro.rawLinesFracture(false, t), "Outside the window, tick " + t);
        }
        for (int t = 0; t <= 40; t++) {
            assertFalse(OrsaAwakeningIntro.rawLinesShown(true, t), "Never in the replay, tick " + t);
            assertFalse(OrsaAwakeningIntro.rawLinesFracture(true, t), "Never in the replay, tick " + t);
        }
    }

    @Test
    void rawLineIsRecordThree() {
        String raw = json("assets/frozendawn/lang/en_us.json").get("screen.frozendawn.awakening.raw").getAsString();
        assertEquals("Vel-thae thaeven.", raw);
        String pageOne = json("assets/frozendawn/thaeven_records/pattern_residue.json")
                .getAsJsonArray("rawPages").get(0).getAsString();
        assertTrue(pageOne.startsWith(raw + "\n" + raw + "\n"), "Record III's raw page 1 opens on it, twice");
    }

    @Test
    void fractureKeepsTheShapeInTheInkGlyphs() {
        String raw = "Vel-thae thaeven.";
        String fractured = ThaevenInk.fracture(raw, 3);
        assertEquals(raw.length(), fractured.length());
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == ' ') {
                assertEquals(' ', fractured.charAt(i));
            } else {
                assertTrue("⟟⟐ϟ·/".indexOf(fractured.charAt(i)) >= 0, "Ink glyph at " + i);
            }
        }
        assertEquals(fractured, ThaevenInk.fracture(raw, 3), "Stable for a seed");
    }

    private static JsonObject json(String path) {
        try (Reader reader = new InputStreamReader(ThaevenLoopLookTest.class.getClassLoader()
                .getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
