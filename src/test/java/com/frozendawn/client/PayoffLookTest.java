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
    void theOpeningTypesQuicklyAndTheReplayTypesTheChangedLinesSlowly() {
        for (int line = 0; line < 4; line++) {
            int at = 16 + line * 16;
            assertEquals(-1, OrsaAwakeningIntro.readoutChars(false, line, at - 1, 45));
            assertEquals(1, OrsaAwakeningIntro.readoutChars(false, line, at, 45));
            assertEquals(45, OrsaAwakeningIntro.readoutChars(false, line, at + 15, 45), "Three letters a tick");
        }
        assertEquals(45, OrsaAwakeningIntro.readoutChars(true, 0, 16 + 15, 45), "The unchanged first line types quickly");
        int trauma = OrsaAwakeningIntro.readoutChars(true, 1, 34 + 10, 45);
        int enrollee = OrsaAwakeningIntro.readoutChars(true, 2, 64 + 10, 45);
        assertTrue(trauma > 0 && trauma < 45, "The trauma line types slowly");
        assertTrue(enrollee > 0 && enrollee < trauma, "The enrollee line types slowest");
        int fade = 214;
        for (int line = 0; line < 4; line++) {
            int len = "ENROLLEES REMAINING: 1 / AS PROJECTED".length();
            assertEquals(len, OrsaAwakeningIntro.readoutChars(true, line, fade - 30, len), "Line " + line + " done before the fade");
        }
    }

    @Test
    void theReplayDrawsTheLogoInRowByRowAndStalls() {
        // The opening's ORSA beat hands the masthead over already drawn (owner mockup, 2026-11-09).
        for (int t = 0; t <= 40; t++) assertEquals(1.0F, OrsaAwakeningIntro.logoReveal(t, false), "Opening, tick " + t);
        assertEquals(0.0F, OrsaAwakeningIntro.logoReveal(9, true));
        float last = 0.0F;
        for (int t = 10; t <= 40; t++) {
            float reveal = OrsaAwakeningIntro.logoReveal(t, true);
            assertTrue(reveal >= last, "The replay never draws backwards");
            last = reveal;
        }
        float stall = OrsaAwakeningIntro.logoReveal(20, true);
        for (int t = 21; t <= 23; t++) assertEquals(stall, OrsaAwakeningIntro.logoReveal(t, true), "The replay stalls at " + t);
        assertTrue(OrsaAwakeningIntro.logoReveal(23, true) < OrsaAwakeningIntro.logoReveal(23, false));
        assertEquals(1.0F, OrsaAwakeningIntro.logoReveal(40, true), "Then finishes");
    }

    @Test
    void theEyesOpenAndInTheReplayTheyStutter() {
        assertEquals(1.0F, OrsaAwakeningIntro.lidCover(309, false), "Shut until the fade");
        float last = 1.0F;
        for (int t = 310; t <= 346; t++) {
            float cover = OrsaAwakeningIntro.lidCover(t, false);
            assertTrue(cover <= last, "The opening's eyes only open");
            last = cover;
        }
        assertEquals(0.0F, OrsaAwakeningIntro.lidCover(346, false), "Open by 346");
        float partly = OrsaAwakeningIntro.lidCover(322, true);
        assertTrue(partly < 1.0F && partly > 0.5F, "The replay's eyes open a little");
        assertTrue(OrsaAwakeningIntro.lidCover(330, true) > partly, "Then sag");
        assertEquals(0.0F, OrsaAwakeningIntro.lidCover(380, true), "Then open slowly");
        assertEquals(360, OrsaAwakeningIntro.totalTicks(false));
        assertTrue(OrsaAwakeningIntro.totalTicks(true) >= 380, "The replay runs until its eyes are open");
    }

    @Test
    void theEnrolleeLineNeverGlitches() {
        boolean others = false;
        for (int t = 0; t < 400; t++) {
            assertFalse(OrsaAwakeningIntro.corrupts(true, 2, t), "The enrollee line, tick " + t);
            for (int line = 0; line < 4; line++) assertFalse(OrsaAwakeningIntro.corrupts(false, line, t), "The opening never glitches");
            others |= OrsaAwakeningIntro.corrupts(true, 0, t) || OrsaAwakeningIntro.corrupts(true, 1, t) || OrsaAwakeningIntro.corrupts(true, 3, t);
        }
        assertTrue(others, "The replay's other lines do");
    }

    @Test
    void theReplaysLogoHasNoRocket() throws IOException {
        var stream = getClass().getClassLoader().getResourceAsStream("assets/frozendawn/textures/gui/orsa_awakening_base_muted.png");
        assertNotNull(stream);
        var image = ImageIO.read(stream);
        // The rocket stands between the stripes, at the logo's center (817 x 362 texture).
        for (int y = 198; y < 285; y++) {
            for (int x = 398; x < 432; x++) {
                int argb = image.getRGB(x, y);
                int bright = Math.max(argb >> 16 & 255, Math.max(argb >> 8 & 255, argb & 255));
                assertTrue((argb >>> 24) < 32 || bright < 90, "Rocket pixel at " + x + "," + y);
            }
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
