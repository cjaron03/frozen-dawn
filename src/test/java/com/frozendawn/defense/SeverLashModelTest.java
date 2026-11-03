package com.frozendawn.defense;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class SeverLashModelTest {
    private static final String ASSETS = "/assets/frozendawn/";

    @Test
    void everyHeatStateHasItsModelAndTextures() throws IOException {
        var overrides = model("sever_lash").getAsJsonArray("overrides");
        var order = new ArrayList<String>();
        for (var o : overrides) {
            var override = o.getAsJsonObject();
            order.add(override.getAsJsonObject("predicate").keySet().iterator().next());
            var path = override.get("model").getAsString().substring("frozendawn:item/".length());
            for (var texture : model(path).getAsJsonObject("textures").entrySet()) {
                var name = texture.getValue().getAsString().substring("frozendawn:".length());
                try (InputStream in = SeverLashModelTest.class.getResourceAsStream(ASSETS + "textures/" + name + ".png")) {
                    assertNotNull(in, path + " needs texture " + name);
                }
            }
        }
        // The last matching override wins, and a locked-out Lash is also at full heat: the vent states come last.
        assertEquals(java.util.List.of("frozendawn:heat", "frozendawn:heat", "frozendawn:vent", "frozendawn:vent"), order);
    }

    private static JsonObject model(String name) throws IOException {
        try (var in = SeverLashModelTest.class.getResourceAsStream(ASSETS + "models/item/" + name + ".json")) {
            assertNotNull(in, "missing model " + name);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
