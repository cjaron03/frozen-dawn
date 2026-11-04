package com.frozendawn.defense;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The Terminus's player-facing text, exactly as the spec words it, and never explaining what it does (CH-11). */
class TerminusTextTest {
    private static JsonObject lang() throws IOException {
        try (var in = TerminusTextTest.class.getResourceAsStream("/assets/frozendawn/lang/en_us.json")) {
            assertNotNull(in);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void theTooltipReadsAsTheSpecWritesIt() throws IOException {
        var lang = lang();
        assertEquals("Discharges remaining: %s", lang.get("item.frozendawn.terminus.discharges").getAsString());
        assertEquals("Transfers logged: %s", lang.get("item.frozendawn.terminus.transfers").getAsString());
        assertEquals("Terminations logged: %s", lang.get("item.frozendawn.terminus.terminations").getAsString());
        assertEquals("Operator exempt. Protocol active.", lang.get("item.frozendawn.terminus.exempt").getAsString());
        assertEquals("NOT FOR USE IN OCCUPIED HABITATS.", lang.get("item.frozendawn.terminus.warning").getAsString());
        assertEquals("Deathwand", lang.get("advancement.frozendawn.deathwand.title").getAsString());
        assertEquals("Nothing burned.", lang.get("advancement.frozendawn.deathwand.description").getAsString());
    }

    @Test
    void noTerminusTextNamesContinuity() throws IOException {
        for (var entry : lang().entrySet()) {
            if (!entry.getKey().contains("terminus") && !entry.getKey().contains("deathwand")) continue;
            assertFalse(entry.getValue().getAsString().toLowerCase().contains("continuity"), entry.getKey());
        }
    }
}
