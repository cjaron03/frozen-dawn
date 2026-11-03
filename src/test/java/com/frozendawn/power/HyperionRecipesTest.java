package com.frozendawn.power;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HyperionRecipesTest {
    private static final String RECIPES = "/data/frozendawn/recipe/";
    /** Each part and how many one craft makes: cells come in pairs, since a silo needs 32 to 68 of them. */
    private static final Map<String, Integer> PARTS = Map.of("superconducting_cell", 2, "hyperion_controller", 1, "hyperion_port", 1,
            "cryocooler", 1, "quench_vent", 1);

    @Test void everyPartIsCraftedFromHearthstoneTierItems() throws IOException {
        for (var part : PARTS.entrySet()) {
            var recipe = read(part.getKey());
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), part.getKey());
            var result = recipe.getAsJsonObject("result");
            assertEquals("frozendawn:" + part.getKey(), result.get("id").getAsString());
            assertEquals(part.getValue(), result.get("count").getAsInt(), part.getKey() + " count");
            // Only vanilla items or this mod's own crafted ones: nothing from loot, slates or the Fabricator.
            for (var key : recipe.getAsJsonObject("key").entrySet()) {
                String item = key.getValue().getAsJsonObject().get("item").getAsString();
                if (item.startsWith("frozendawn:")) assertEquals("minecraft:crafting_shaped", read(item.substring("frozendawn:".length())).get("type").getAsString(),
                        part.getKey() + " uses " + item + ", which must itself be crafted");
                else assertTrue(item.startsWith("minecraft:"), part.getKey() + " uses " + item);
            }
        }
    }

    private static JsonObject read(String name) throws IOException {
        try (var in = HyperionRecipesTest.class.getResourceAsStream(RECIPES + name + ".json")) {
            assertNotNull(in, "missing recipe " + name);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
