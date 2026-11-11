package com.frozendawn.homo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Payoff branch 4: the replacement ring (5a) and the return notice (5b), neither after the Heart (5c). */
class ReturnedEchoTest {

    /** Owner, 2026-11-10: the ring plays at the kill, so the player cannot walk away before it; never on a respawn. */
    @Test
    void onlyAKilledReturnedRings() {
        assertTrue(HearthPopulationPolicy.ringsOnDeath(HearthPopulationRole.RETURNED));
        assertTrue(HearthPopulationPolicy.ringsOnDeath(HearthPopulationRole.HUNTER));
        assertFalse(HearthPopulationPolicy.ringsOnDeath(HearthPopulationRole.MIMIC), "The Mimic dies silent");
        assertFalse(HearthPopulationPolicy.ringsOnDeath(HearthPopulationRole.ARCHITECT), "The Architect dies silent");
        assertFalse(HearthPopulationPolicy.ringsOnDeath(null), "A Returned outside the population dies silent");
    }

    /** Owner, 2026-11-09: killing Returned over and over rings at most once every ten minutes. */
    @Test
    void theRingRestsTenMinutes() {
        assertEquals(12_000L, HearthPopulationPolicy.RING_COOLDOWN_TICKS);
        assertTrue(HearthPopulationPolicy.ringCooldownElapsed(-1L, 0L), "The first ring is free");
        assertFalse(HearthPopulationPolicy.ringCooldownElapsed(1_000L, 1_000L));
        assertFalse(HearthPopulationPolicy.ringCooldownElapsed(1_000L, 12_999L));
        assertTrue(HearthPopulationPolicy.ringCooldownElapsed(1_000L, 13_000L));
        assertTrue(HearthPopulationPolicy.ringCooldownElapsed(50_000L, 10L), "A clock behind the last ring frees it");
    }

    /** The owner's pick from the Task 0 previews, 2026-11-09: pitch 0.71 at volume 0.2. */
    @Test
    void theRingIsTheRespawnRing() throws Exception {
        JsonObject event = json("assets/frozendawn/sounds.json").getAsJsonObject("entity.returned.replacement_ring");
        assertEquals("hostile", event.get("category").getAsString());
        assertFalse(event.has("subtitle"), "No text with the ring");
        assertEquals(1, event.getAsJsonArray("sounds").size());
        JsonObject sound = event.getAsJsonArray("sounds").get(0).getAsJsonObject();
        assertEquals("frozendawn:ui/orsa_awakening_ring", sound.get("name").getAsString());
        assertFalse(sound.has("stream") && sound.get("stream").getAsBoolean(), "Not streamed");
        assertEquals(0.71F, sound.get("pitch").getAsFloat(), 1e-6F);
        assertEquals(0.2F, sound.get("volume").getAsFloat(), 1e-6F);
        assertEquals(12, sound.get("attenuation_distance").getAsInt());
    }

    @Test
    void aNoticeNeedsEveryGate() {
        assertTrue(ReturnNoticeState.owes(false, false, true, true, 0));
        assertTrue(ReturnNoticeState.owes(false, false, true, true, 1));
        assertFalse(ReturnNoticeState.owes(true, false, true, true, 0), "Not after the Heart falls");
        assertFalse(ReturnNoticeState.owes(false, true, true, true, 0), "Not for a permanent Orsathae");
        assertFalse(ReturnNoticeState.owes(false, false, false, true, 0), "Not before the first transmission");
        assertFalse(ReturnNoticeState.owes(false, false, true, false, 0), "Not for a death the Hearth did not see");
        assertFalse(ReturnNoticeState.owes(false, false, true, true, 2), "At most twice per world");
        assertEquals(1, ReturnNoticeState.spend(0));
        assertEquals(2, ReturnNoticeState.spend(1));
        assertEquals(2, ReturnNoticeState.spend(2), "The count never passes the cap");
    }

    /** Review, 2026-11-10: notices already playing hold their slots, and only the Major Hearth saw the death. */
    @Test
    void aNoticeStartsOnlyWithAFreeSlotAtTheMajorHearth() {
        assertTrue(ReturnNoticeState.startsNotice(true, true, 0, 0));
        assertTrue(ReturnNoticeState.startsNotice(true, true, 1, 0));
        assertTrue(ReturnNoticeState.startsNotice(true, true, 0, 1));
        assertFalse(ReturnNoticeState.startsNotice(true, true, 1, 1), "Two owed players at once make two, not three");
        assertFalse(ReturnNoticeState.startsNotice(true, true, 0, 2));
        assertFalse(ReturnNoticeState.startsNotice(true, false, 0, 0), "A Minor Hearth did not see the death");
        assertFalse(ReturnNoticeState.startsNotice(false, true, 0, 0), "Nothing owed");
    }

    @Test
    void noticeLinesAreExistingWords() throws Exception {
        JsonObject lang = json("assets/frozendawn/lang/en_us.json");
        String died = lang.get("overlay.frozendawn.thaeven.notice.died").getAsString();
        String word = lang.get("overlay.frozendawn.thaeven.notice.word").getAsString();
        assertEquals("Vel-thae ethven.", died);
        assertEquals("...Vel-thae.", word);
        assertTrue(json("assets/frozendawn/thaeven_records/the_passage.json").toString().contains(died),
                "The died line is already in the lexicon");
        for (String line : new String[] {died, word}) {
            String lower = line.toLowerCase();
            assertFalse(lower.contains("thaeven"), line);
            assertFalse(lower.contains("you"), line);
            assertFalse(lower.contains("return"), line);
        }
    }

    @Test
    void theNoticeRanksBeforeTheMyth() {
        assertEquals(3, ThaevenTransmissionType.RETURN_NOTICE.networkId());
        assertEquals(200, ThaevenTransmissionType.RETURN_NOTICE.durationTicks());
        assertEquals(ThaevenTransmissionType.RETURN_NOTICE, ThaevenTransmissionType.fromNetworkId(3));
    }

    private static JsonObject json(String path) throws Exception {
        try (Reader reader = new InputStreamReader(
                ReturnedEchoTest.class.getClassLoader().getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
