package com.frozendawn.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** CH-38: the blanket's only text is the stitched K. */
class ThermalBlanketTextTest {

    @Test
    void theBlanketSaysOnlyK() {
        JsonObject lang = lang();
        assertEquals("ORSA Thermal Blanket", lang.get("item.frozendawn.orsa_thermal_blanket").getAsString());
        assertEquals("K.", lang.get("tooltip.frozendawn.orsa_thermal_blanket.stitch").getAsString());
        Set<String> keys = lang.keySet().stream().filter(key -> key.contains("orsa_thermal_blanket")).collect(Collectors.toSet());
        assertEquals(Set.of("item.frozendawn.orsa_thermal_blanket", "tooltip.frozendawn.orsa_thermal_blanket.stitch"), keys);
    }

    private static JsonObject lang() {
        try (Reader reader = new InputStreamReader(ThermalBlanketTextTest.class.getClassLoader()
                .getResourceAsStream("assets/frozendawn/lang/en_us.json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
