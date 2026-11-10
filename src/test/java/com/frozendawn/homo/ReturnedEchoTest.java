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
