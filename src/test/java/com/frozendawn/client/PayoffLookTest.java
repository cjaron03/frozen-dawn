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
        int trauma = OrsaAwakeningIntro.readoutChars(true, 1, 116 + 10, 45);
        int enrollee = OrsaAwakeningIntro.readoutChars(true, 2, 240 + 10, 45);
        assertTrue(trauma > 0 && trauma < 45, "The trauma line types slowly");
        assertTrue(enrollee > 0 && enrollee < trauma, "The enrollee line types slowest");
        int shrink = OrsaAwakeningIntro.fadeStart(true) - 20 - OrsaAwakeningIntro.DIAGNOSTIC_START;
        for (int line = 0; line < 4; line++) {
            int len = "ENROLLEES REMAINING: 1 / AS PROJECTED".length();
            assertEquals(len, OrsaAwakeningIntro.readoutChars(true, line, shrink - 30, len), "Line " + line + " done before the panel goes");
        }
    }

    /** The replay's worn voice starts with the readout, each line types as the voice reaches it, and the eyes wait. */
    @Test
    void theReplaysReadoutFollowsItsVoice() {
        assertEquals(0, OrsaAwakeningIntro.voiceStart(false), "The opening's voice starts with the masthead");
        int voice = OrsaAwakeningIntro.voiceStart(true);
        assertEquals(OrsaAwakeningIntro.DIAGNOSTIC_START + 16, voice, "With the first line");
        // The voice's parts start 0, 5.0, 7.8, 11.2 and 15.2 s in and it runs 20.2 s; the third is off script.
        int[] lineAt = {0, 100, 224, 304};
        for (int line = 0; line < 4; line++) {
            int at = voice - OrsaAwakeningIntro.DIAGNOSTIC_START + lineAt[line];
            assertEquals(-1, OrsaAwakeningIntro.readoutChars(true, line, at - 1, 45), "Line " + line + " waits for the voice");
            assertEquals(1, OrsaAwakeningIntro.readoutChars(true, line, at, 45), "Line " + line + " types as it is spoken");
        }
        assertEquals(310, OrsaAwakeningIntro.fadeStart(false));
        assertTrue(OrsaAwakeningIntro.fadeStart(true) >= voice + 404, "The eyes open after the voice ends");
        assertTrue(OrsaAwakeningIntro.fadeStart(true) <= voice + 420, "And not long after");
        for (int t = voice + 156; t < voice + 224; t += 4) {
            assertTrue(OrsaAwakeningIntro.flatlines(true, t), "The pulse flatlines through the off-script line: " + t);
        }
        for (int t = 0; t < 700; t++) assertFalse(OrsaAwakeningIntro.flatlines(false, t), "Never in the opening");
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
        int fade = OrsaAwakeningIntro.fadeStart(true);
        assertEquals(1.0F, OrsaAwakeningIntro.lidCover(fade - 1, true), "The replay's stay shut until its own fade");
        float partly = OrsaAwakeningIntro.lidCover(fade + 12, true);
        assertTrue(partly < 1.0F && partly > 0.5F, "The replay's eyes open a little");
        assertTrue(OrsaAwakeningIntro.lidCover(fade + 20, true) > partly, "Then sag");
        assertEquals(0.0F, OrsaAwakeningIntro.lidCover(fade + 70, true), "Then open slowly");
        assertEquals(360, OrsaAwakeningIntro.totalTicks(false));
        assertTrue(OrsaAwakeningIntro.totalTicks(true) >= fade + 70, "The replay runs until its eyes are open");
    }

    @Test
    void theEnrolleeLineNeverGlitches() {
        boolean others = false;
        for (int t = 0; t < 700; t++) {
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
