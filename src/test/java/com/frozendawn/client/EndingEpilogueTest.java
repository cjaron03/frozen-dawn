package com.frozendawn.client;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.client.EndingEpilogue.Beat;
import com.frozendawn.client.EndingEpilogue.Kind;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class EndingEpilogueTest {
    private static List<String> shape(List<Beat> beats) {
        return beats.stream().map(beat -> beat.kind() + " " + beat.ticks()).toList();
    }

    private static Beat beat(boolean truth, Kind kind) {
        return EndingEpilogue.beats(truth).stream().filter(b -> b.kind() == kind).findFirst().orElseThrow();
    }

    @Test
    void endingAIsUnchanged() {
        assertEquals(List.of("FADE 100", "COLONY 120", "SIGNAL 140"), shape(EndingEpilogue.beats(false)));
        assertEquals(8 * 60 * 20 + 20 * 20, EndingEpilogue.endingCompleteTicks(false));
    }

    @Test
    void endingBWelcomesFirst() {
        List<Beat> plain = EndingEpilogue.beats(false);
        List<Beat> truth = EndingEpilogue.beats(true);
        assertEquals(plain, truth.subList(0, plain.size()), "the welcome is Ending A's, beat for beat");
        assertEquals(List.of("COMPLIANCE 320", "HOLD 40", "END_CARD 120"),
                shape(truth.subList(plain.size(), truth.size())));
    }

    @Test
    void theCreditsKeepTheirLength() {
        // Ruling 4: the credit roll, the skip window and the music fade start where they start today.
        // Ending B's epilogue already ran 320 ticks longer (the compliance beat); only the hold and the card are new.
        assertEquals(8 * 60 * 20 + 20 * 20 - (100 + 120 + 140),
                EndingEpilogue.endingCompleteTicks(false) - EndingEpilogue.length(false));
        assertEquals(8 * 60 * 20 + 20 * 20 - (100 + 120 + 140 + 320),
                EndingEpilogue.endingCompleteTicks(true) - EndingEpilogue.length(true));
    }

    @Test
    void theSignalSaysWelcomeInBoth() {
        for (boolean truth : new boolean[] {false, true}) {
            Beat signal = beat(truth, Kind.SIGNAL);
            assertEquals("screen.frozendawn.ending.welcome", signal.key());
            assertEquals(0xFFA7FFEA, signal.titleColor());
            assertEquals(0xFFD9FFF6, signal.bodyColor());
            assertEquals(0xCC44E6A8, signal.accentColor());
        }
    }

    @Test
    void theCardSaysTheEnd() throws IOException {
        Beat card = beat(true, Kind.END_CARD);
        assertEquals("screen.frozendawn.ending.truth", card.key());
        assertEquals(0xFFFFF0B0, card.bodyColor());
        assertTrue(EndingEpilogue.beats(false).stream().noneMatch(b -> b.kind() == Kind.END_CARD),
                "The End? stays Ending B only");
        try (var in = EndingEpilogueTest.class.getResourceAsStream("/assets/frozendawn/lang/en_us.json")) {
            assertNotNull(in, "en_us.json on the classpath");
            var lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("The End?", lang.get(card.key()).getAsString());
        }
    }

    @Test
    void theCardFadesInAndNeverOut() {
        assertEquals(0.0F, EndingEpilogue.cardAlpha(0));
        assertEquals(0.5F, EndingEpilogue.cardAlpha(14));
        assertEquals(1.0F, EndingEpilogue.cardAlpha(28));
        assertEquals(1.0F, EndingEpilogue.cardAlpha(beat(true, Kind.END_CARD).ticks()));
        assertEquals(1.0F, EndingEpilogue.cardAlpha(10_000), "held under the return hint until the screen closes");
    }

    @Test
    void theComplianceBeatStartsAfterTheWelcome() {
        assertEquals(-1, EndingEpilogue.startOf(false, Kind.COMPLIANCE));
        assertEquals(100 + 120 + 140, EndingEpilogue.startOf(true, Kind.COMPLIANCE));
    }
}
