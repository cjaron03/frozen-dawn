package com.frozendawn.lore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The payoff flag's rules and the canon text it reveals. */
class PayoffStateTest {
    @Test
    void firesOnlyOnceAfterErasure() {
        assertFalse(PayoffState.firesOnDeath(false, false, false), "Not before the Heart falls");
        assertTrue(PayoffState.firesOnDeath(true, false, false), "The first death after erasure");
        assertFalse(PayoffState.firesOnDeath(true, true, false), "Never when Maeve escaped");
        assertFalse(PayoffState.firesOnDeath(true, false, true), "Never twice");
    }

    @Test
    void onlyNewSavesAreEligible() {
        assertTrue(PayoffState.eligibleAt(0));
        assertTrue(PayoffState.eligibleAt(1199));
        assertFalse(PayoffState.eligibleAt(1200));

        var old = PayoffState.create(5_000_000, true);
        assertFalse(old.isEligible(), "A world from before the payoff");
        assertFalse(old.isFired());
        assertTrue(old.isTerminusTurned(), "Keeps the Terminus turn it already had");

        var fresh = PayoffState.create(0, false);
        assertTrue(fresh.isEligible(), "A new world");
        assertFalse(fresh.isFired());
        assertFalse(fresh.isTerminusTurned());
    }

    @Test
    void theAdvancementIsHidden() {
        JsonObject advancement = json("data/frozendawn/advancement/payoff_fired.json");
        assertFalse(advancement.has("parent"), "No parent");
        assertFalse(advancement.has("display"), "No display");
        JsonObject criteria = advancement.getAsJsonObject("criteria");
        assertEquals(1, criteria.size(), "One criterion");
        var only = criteria.entrySet().iterator().next().getValue().getAsJsonObject();
        assertEquals("minecraft:impossible", only.get("trigger").getAsString());
    }

    static JsonObject json(String path) {
        var stream = PayoffStateTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
