package com.frozendawn.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EndingCockpitTest {
    @Test
    void theClockReadsThePlayersOwnTime() {
        assertEquals("T+ 0:00:00", EndingCockpit.missionClock(0));
        assertEquals("T+ 212:14:06", EndingCockpit.missionClock((212 * 3600 + 14 * 60 + 6) * 20));
        assertEquals("T+ 0:00:00", EndingCockpit.missionClock(19), "whole seconds only");
    }

    @Test
    void theRangeClosesToZeroAsTheCreditsBegin() {
        assertEquals(EndingCockpit.START_RANGE_KM, EndingCockpit.rangeKm(0.0F));
        assertEquals(0, EndingCockpit.rangeKm(1.0F));
        int last = Integer.MAX_VALUE;
        for (int i = 0; i <= 100; i++) {
            int km = EndingCockpit.rangeKm(i / 100.0F);
            assertTrue(km <= last, "the range never grows");
            last = km;
        }
        assertEquals(EndingCockpit.START_RANGE_KM, EndingCockpit.rangeKm(-1.0F));
        assertEquals(0, EndingCockpit.rangeKm(2.0F));
    }

    @Test
    void theRangeGroupsItsThousands() {
        assertEquals("48 210", EndingCockpit.groupThousands(48210));
        assertEquals("0", EndingCockpit.groupThousands(0));
        assertEquals("1 000 000", EndingCockpit.groupThousands(1_000_000));
    }

    @Test
    void theWelcomeTypesInBeforeItFades() {
        String welcome = "Welcome home.";
        assertEquals(0, EndingCockpit.typedLength(0, welcome.length()));
        assertEquals(1, EndingCockpit.typedLength(2, welcome.length()));
        int done = welcome.length() * EndingCockpit.TICKS_PER_LETTER;
        assertEquals(welcome.length(), EndingCockpit.typedLength(done, welcome.length()));
        assertEquals(welcome.length(), EndingCockpit.typedLength(10_000, welcome.length()));
        EndingEpilogue.Beat signal = EndingEpilogue.beats(false).stream()
                .filter(beat -> beat.kind() == EndingEpilogue.Kind.SIGNAL).findFirst().orElseThrow();
        assertTrue(done < signal.ticks() - 35, "fully typed before the panel starts to fade");
    }

    @Test
    void aPanelFitsItsTitle() {
        assertEquals(200, EndingCockpit.panelWidth(427, 70, 100), "short lines keep the minimum width");
        int wide = EndingCockpit.panelWidth(427, 70, 218);
        assertTrue(wide >= EndingCockpit.TITLE_INSET + 218 + 8, "the corrupt header stays on the glass");
        assertEquals(427 - 32, EndingCockpit.panelWidth(427, 600, 100), "never wider than the screen allows");
    }

    @Test
    void everyCockpitLabelHasText() throws IOException {
        try (var in = EndingCockpitTest.class.getResourceAsStream("/assets/frozendawn/lang/en_us.json")) {
            assertNotNull(in, "en_us.json on the classpath");
            var lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : EndingCockpit.LANG_KEYS) {
                assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), key);
            }
            for (String retired : new String[] {"screen.frozendawn.ending.vector.gravity",
                    "screen.frozendawn.ending.vector.corridor", "screen.frozendawn.ending.vector.capture"}) {
                assertFalse(lang.has(retired), "retired sentence-style HUD line: " + retired);
            }
        }
    }
}
