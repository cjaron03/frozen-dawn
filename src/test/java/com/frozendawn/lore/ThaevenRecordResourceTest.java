package com.frozendawn.lore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThaevenRecordResourceTest {
    @Test
    void everyRecordHasImmutablePagesAndUniqueStableSegments() {
        for (ThaevenRecordId record : ThaevenRecordId.values()) {
            JsonObject root = load("assets/frozendawn/thaeven_records/"
                    + record.serializedName() + ".json");
            assertFalse(root.getAsJsonArray("rawPages").isEmpty(),
                    record.serializedName());
            JsonArray pages = root.getAsJsonArray("translatedPages");
            assertFalse(pages.isEmpty(), record.serializedName());
            Set<String> segmentIds = new HashSet<>();
            for (var page : pages) {
                JsonArray segments = page.getAsJsonObject()
                        .getAsJsonArray("segments");
                assertFalse(segments.isEmpty(), record.serializedName());
                for (var element : segments) {
                    JsonObject segment = element.getAsJsonObject();
                    assertTrue(segmentIds.add(segment.get("id").getAsString()),
                            "duplicate segment in " + record.serializedName());
                    assertFalse(segment.get("baseText").getAsString().isBlank());
                    assertTrue(segment.get("revealTiming").getAsInt() >= 0);
                    assertNotNull(segment.get("uncertainty"));
                }
            }
        }
    }

    @Test
    void bookTwoUsesStableSemanticSegmentsForTheHeartRevision() {
        JsonObject root = load(
                "assets/frozendawn/thaeven_records/the_passage.json");
        JsonArray finalSegments = root.getAsJsonArray("translatedPages")
                .get(7).getAsJsonObject().getAsJsonArray("segments");

        assertEquals("heart_term_01",
                finalSegments.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("The Heart.", finalSegments.get(0).getAsJsonObject()
                .getAsJsonObject("semanticOverrides").get("1").getAsString());
        assertEquals("heart_keeper_01",
                finalSegments.get(1).getAsJsonObject().get("id").getAsString());
        assertEquals("the one who held the Heart.",
                finalSegments.get(1).getAsJsonObject()
                        .getAsJsonObject("semanticOverrides")
                        .get("1").getAsString());
    }

    @Test
    void translatorRecipeKeepsTheApprovedShapeAndSerializer() {
        JsonObject recipe = load(
                "data/frozendawn/recipe/thaeven_translator.json");

        assertEquals("frozendawn:thaeven_translator_shaped",
                recipe.get("type").getAsString());
        assertEquals("ASA", recipe.getAsJsonArray("pattern").get(0).getAsString());
        assertEquals("RMR", recipe.getAsJsonArray("pattern").get(1).getAsString());
        assertEquals("CCC", recipe.getAsJsonArray("pattern").get(2).getAsString());
        assertEquals("frozendawn:orsa_multitool", recipe.getAsJsonObject("key")
                .getAsJsonObject("M").get("item").getAsString());
    }

    private static JsonObject load(String path) {
        var stream = ThaevenRecordResourceTest.class.getClassLoader()
                .getResourceAsStream(path);
        assertNotNull(stream, path);
        return JsonParser.parseReader(new InputStreamReader(
                stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
