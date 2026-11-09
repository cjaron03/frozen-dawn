package com.frozendawn.client;

import java.awt.Color;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The owner-approved look of the death line, the boot cue and the replay (mockups, 2026-11-09). */
class PayoffLookTest {
    private static final int LINE = "1 enrollee remaining.".length();

    @Test
    void theSecondDeathLineTypesInThenDropsOutOnce() {
        for (int t = 0; t < 20; t++) assertEquals(0, PayoffClient.typedChars(t, LINE), "Nothing for the first second");
        assertEquals(1, PayoffClient.typedChars(20, LINE));
        assertEquals(LINE, PayoffClient.typedChars(20 + LINE - 1, LINE), "One letter a tick");
        int done = 20 + LINE;
        assertEquals(LINE, PayoffClient.typedChars(done + 5, LINE));
        assertEquals(-1, PayoffClient.typedChars(done + 6, LINE), "Drops out");
        assertEquals(-1, PayoffClient.typedChars(done + 7, LINE));
        assertEquals(LINE, PayoffClient.typedChars(done + 8, LINE), "Then holds");
        assertEquals(LINE, PayoffClient.typedChars(10_000, LINE));
    }

    @Test
    void theBootCuePowersOnBeforeTheBlink() {
        assertEquals(0.0F, OrsaAwakeningIntro.powerOnOpen(0), "A line on black first");
        assertEquals(0.0F, OrsaAwakeningIntro.powerOnOpen(2));
        float last = 0.0F;
        for (int t = 3; t < 8; t++) {
            float open = OrsaAwakeningIntro.powerOnOpen(t);
            assertTrue(open > last && open < 1.0F, "Opens a little more each tick: " + t);
            last = open;
        }
        assertEquals(1.0F, OrsaAwakeningIntro.powerOnOpen(8), "Fully open");
        assertEquals(List.of(5, 6, 7), java.util.stream.IntStream.range(0, 48)
                .filter(OrsaAwakeningIntro::bootCueStatic).boxed().toList(), "Two or three frames of static");
    }

    @Test
    void theOpeningShowsWholeLinesAndTheReplayTypesTheChangedOnes() {
        for (int line = 0; line < 4; line++) {
            int at = 8 + line * 10;
            assertEquals(-1, OrsaAwakeningIntro.readoutChars(false, line, at - 1, 50));
            assertEquals(50, OrsaAwakeningIntro.readoutChars(false, line, at, 50), "The opening's line " + line + " is whole");
        }
        assertEquals(50, OrsaAwakeningIntro.readoutChars(true, 0, 8, 50), "Unchanged line, whole");
        int partial2 = OrsaAwakeningIntro.readoutChars(true, 1, 25, 50);
        int partial3 = OrsaAwakeningIntro.readoutChars(true, 2, 70, 50);
        assertTrue(partial2 > 0 && partial2 < 50, "The trauma line types");
        assertTrue(partial3 > 0 && partial3 < 50, "The enrollee line types");
        assertTrue(OrsaAwakeningIntro.readoutChars(true, 2, 70, 50) < OrsaAwakeningIntro.readoutChars(true, 1, 70 - 54 + 20, 50),
                "The enrollee line types slower than the trauma line");
        int fade = 214;
        for (int line = 0; line < 4; line++) {
            int len = "ENROLLEES REMAINING: 1 / AS PROJECTED".length();
            assertEquals(len, OrsaAwakeningIntro.readoutChars(true, line, fade - 30, len), "Line " + line + " done before the fade");
        }
    }

    @Test
    void onlyTheEnrolleeLineHoldsYellowInTheReplay() {
        assertTrue(OrsaAwakeningIntro.holdsYellow(true, 2));
        for (int line : new int[] {0, 1, 3}) assertFalse(OrsaAwakeningIntro.holdsYellow(true, line));
        for (int line = 0; line < 4; line++) assertFalse(OrsaAwakeningIntro.holdsYellow(false, line));
    }

    @Test
    void theReplaysLogoIsDrainedOfColor() throws IOException {
        for (String name : List.of("orsa_awakening_base", "orsa_awakening_stripe_top_left", "orsa_awakening_stripe_top_right",
                "orsa_awakening_stripe_mid_left", "orsa_awakening_stripe_mid_right", "orsa_awakening_stripe_bot_left",
                "orsa_awakening_stripe_bot_right")) {
            String path = "assets/frozendawn/textures/gui/" + name + "_muted.png";
            var stream = getClass().getClassLoader().getResourceAsStream(path);
            assertNotNull(stream, path);
            var image = ImageIO.read(stream);
            float most = 0.0F;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    if ((argb >>> 24) < 32) continue;
                    float[] hsb = Color.RGBtoHSB(argb >> 16 & 255, argb >> 8 & 255, argb & 255, null);
                    if (hsb[2] > 0.15F) most = Math.max(most, hsb[1]);
                }
            }
            assertTrue(most < 0.12F, path + " saturation " + most);
        }
    }
}
