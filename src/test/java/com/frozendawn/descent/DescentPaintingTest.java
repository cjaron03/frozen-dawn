package com.frozendawn.descent;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class DescentPaintingTest {
    @Test void noDescentPaintingRollsInSurvival() throws IOException {
        assertEquals(11, DescentPaintings.IDS.size(), "nine eras, the scratches and the portrait");
        for (String tag : new String[]{"/data/minecraft/tags/painting_variant/placeable.json",
                "/data/frozendawn/tags/painting_variant/placeable.json"}) {
            try (InputStream in = DescentPaintingTest.class.getResourceAsStream(tag)) {
                if (in == null) continue;
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                for (String id : DescentPaintings.IDS) assertFalse(text.contains(id), tag + " lists " + id);
            }
        }
        JsonObject lang = json("/assets/frozendawn/lang/en_us.json");
        for (String id : DescentPaintings.IDS) {
            JsonObject variant = json("/data/frozendawn/painting_variant/" + id + ".json");
            assertEquals("frozendawn:" + id, variant.get("asset_id").getAsString(), id);
            BufferedImage art;
            try (InputStream in = DescentPaintingTest.class.getResourceAsStream("/assets/frozendawn/textures/painting/" + id + ".png")) {
                assertNotNull(in, "missing art for " + id);
                art = ImageIO.read(in);
            }
            assertEquals(art.getWidth(), variant.get("width").getAsInt() * 16, id + " art is 16 px per block across");
            assertEquals(art.getHeight(), variant.get("height").getAsInt() * 16, id + " art is 16 px per block down");
            String key = "painting.frozendawn." + id.replace('/', '.');
            for (String part : new String[]{".title", ".author"}) {
                assertTrue(lang.has(key + part), key + part + " is missing");
                assertEquals("", lang.get(key + part).getAsString(), key + part + " must say nothing");
            }
        }
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = DescentPaintingTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
