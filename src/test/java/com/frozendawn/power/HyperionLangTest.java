package com.frozendawn.power;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.block.HyperionMultiblock;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class HyperionLangTest {
    @Test void everyProblemAndStageHasText() throws IOException {
        try (var in = HyperionLangTest.class.getResourceAsStream("/assets/frozendawn/lang/en_us.json")) {
            assertNotNull(in, "en_us.json on the classpath");
            var lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var p : HyperionMultiblock.Problem.values()) {
                var key = "hyperion.frozendawn.problem." + p.name().toLowerCase(Locale.ROOT);
                assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), key);
            }
            for (var s : HyperionPhysics.Stage.values()) {
                var key = "hyperion.frozendawn.stage." + s.name().toLowerCase(Locale.ROOT);
                assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), key);
            }
        }
    }
}
