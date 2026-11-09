package com.frozendawn.lore;

import com.frozendawn.client.ThaevenRecordDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CH-37's loop text: Record I's last page and its re-ink. */
class ThaevenLoopTextTest {
    private static final String RAW = "Vel-thae thaeven.\nVel-thae thaeven.\nThae ethven.";
    private static final String LINES = "The warm-one crossed here.\nThen crossed here again.\n"
            + "The body broke.\nThe returning was not.\n";

    @Test
    void recordOneEndsOnTheLoopPage() {
        JsonObject root = json("vel_an");
        JsonArray raw = root.getAsJsonArray("rawPages");
        JsonArray translated = root.getAsJsonArray("translatedPages");
        assertEquals(6, raw.size());
        assertEquals(6, translated.size());
        assertEquals(RAW, raw.get(5).getAsString());

        JsonArray segments = translated.get(5).getAsJsonObject().getAsJsonArray("segments");
        assertEquals(1, segments.size());
        JsonObject segment = segments.get(0).getAsJsonObject();
        assertEquals("vel_an_06", segment.get("id").getAsString());
        assertEquals(58, segment.get("revealTiming").getAsInt());
        assertTrue(segment.get("uncertainty").getAsBoolean());
        assertEquals(LINES + "Source: unresolved.", segment.get("baseText").getAsString());
        JsonObject overrides = segment.getAsJsonObject("semanticOverrides");
        assertNotNull(overrides.get("1"));
        assertEquals(LINES + "Source: the warm-one.", overrides.get("1").getAsString());
    }

    @Test
    void segmentResolvesTheReInk() {
        ThaevenRecordDefinition record = definition("vel_an");
        assertTrue(record.translatedPage(5, 0).endsWith("\nSource: unresolved."));
        assertTrue(record.translatedPage(5, 1).endsWith("\nSource: the warm-one."));
    }

    @Test
    void onlyTheChangedPageReInks() {
        ThaevenRecordDefinition recordOne = definition("vel_an");
        assertEquals(List.of(5), recordOne.changedPages(0, 1));
        assertEquals(List.of(0, 1, 2, 3, 4, 5), recordOne.changedPages(-1, 1));
        assertEquals(List.of(7), definition("the_passage").changedPages(0, 1));
    }

    private static JsonObject json(String name) {
        try (Reader reader = reader(name)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static ThaevenRecordDefinition definition(String name) {
        try (Reader reader = reader(name)) {
            return ThaevenRecordDefinition.parse(reader);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static Reader reader(String name) {
        return new InputStreamReader(ThaevenLoopTextTest.class.getClassLoader()
                .getResourceAsStream("assets/frozendawn/thaeven_records/" + name + ".json"),
                StandardCharsets.UTF_8);
    }
}
