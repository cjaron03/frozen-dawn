package com.frozendawn.defense;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SeverLashRecipeTest {
    private static final String RECIPES = "/data/frozendawn/recipe/";

    @Test
    void theLashIsCraftedFromItsEmitterLensCellIronAndLead() throws IOException {
        var recipe = read("sever_lash");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        var result = recipe.getAsJsonObject("result");
        assertEquals("frozendawn:sever_lash", result.get("id").getAsString());
        assertEquals(1, result.get("count").getAsInt());
        assertFalse(result.has("components"), "A crafted Lash carries no heat: it starts cool");
        var counts = new HashMap<String, Integer>();
        var key = recipe.getAsJsonObject("key");
        for (var row : recipe.getAsJsonArray("pattern")) for (char c : row.getAsString().toCharArray())
            if (c != ' ') counts.merge(key.getAsJsonObject(String.valueOf(c)).get("item").getAsString(), 1, Integer::sum);
        assertEquals(Map.of("frozendawn:lash_emitter", 1, "frozendawn:acheronite_shard", 1, "frozendawn:power_cell", 1,
                "minecraft:iron_ingot", 2, "frozendawn:lead_ingot", 1), counts);
    }

    @Test
    void noOtherRecipeSharesTheLashGrid() throws Exception {
        var mine = read("sever_lash");
        var dir = Path.of(SeverLashRecipeTest.class.getResource(RECIPES).toURI());
        try (var files = Files.walk(dir)) {
            for (var file : files.filter(f -> f.toString().endsWith(".json") && !f.endsWith("sever_lash.json")).toList()) {
                var other = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                assertFalse(mine.get("pattern").equals(other.get("pattern")) && mine.get("key").equals(other.get("key")), "Same grid as " + file);
            }
        }
    }

    private static JsonObject read(String name) throws IOException {
        try (var in = SeverLashRecipeTest.class.getResourceAsStream(RECIPES + name + ".json")) {
            assertNotNull(in, "missing recipe " + name);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
