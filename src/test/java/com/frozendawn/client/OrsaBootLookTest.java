package com.frozendawn.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The regenerated ORSA spinner and the first boot's ORSA beat (owner mockups, 2026-11-09). */
class OrsaBootLookTest {
    @Test
    void theSpinnerHasSixtyFourFramesOnTwoRows() {
        assertEquals(0, OrsaLogoRenderer.bootFrame(0.0F));
        assertEquals(8, OrsaLogoRenderer.bootFrame(0.125F), "The masthead's pose");
        assertEquals(63, OrsaLogoRenderer.bootFrame(0.999F));
        assertEquals(0, OrsaLogoRenderer.bootFrame(1.0F), "One orbit wraps");
        assertEquals(48, OrsaLogoRenderer.bootFrame(-0.25F), "Negative phases wrap too");
        assertArrayEquals(new float[] {0.0F, 0.0F, 1.0F / 32, 0.5F}, OrsaLogoRenderer.bootFrameUv(0), 1e-6F);
        assertArrayEquals(new float[] {1.0F / 32, 0.5F, 2.0F / 32, 1.0F}, OrsaLogoRenderer.bootFrameUv(33), 1e-6F);
        assertArrayEquals(new float[] {31.0F / 32, 0.5F, 1.0F, 1.0F}, OrsaLogoRenderer.bootFrameUv(63), 1e-6F);
    }

    @Test
    void theReplayRunsTheBeatWithItsStalls() {
        int c = OrsaBootBeat.SPUTTER;
        assertEquals(OrsaBootBeat.TICKS, OrsaAwakeningIntro.bootBeatLength(false));
        assertEquals(OrsaBootBeat.REPLAY_TICKS, OrsaAwakeningIntro.bootBeatLength(true));
        assertEquals(OrsaBootBeat.TICKS + 7 + c, OrsaBootBeat.REPLAY_TICKS, "A cold start, then two stalls of three and four");
        assertEquals(OrsaBootBeat.GLINT + OrsaBootBeat.GROW + OrsaBootBeat.EXPAND + OrsaBootBeat.BLEND,
                OrsaBootBeat.TICKS);
        assertEquals(0.0F, OrsaBootBeat.replayTime(c), 1e-6F, "The beat starts after the cold start");
        for (int t = 13; t <= 16; t++) assertEquals(13.0F, OrsaBootBeat.replayTime(c + t), 1e-6F, "Holds at " + t);
        for (int t = 44; t <= 48; t++) assertEquals(41.0F, OrsaBootBeat.replayTime(c + t), 1e-6F, "Holds at " + t);
        assertEquals(18.0F, OrsaBootBeat.replayTime(c + 21), 1e-6F);
        assertEquals(14.0F, OrsaBootBeat.replayTime(c + 22), 1e-6F, "Skips back five ticks");
        assertEquals(24.0F, OrsaBootBeat.replayTime(c + 27), 1e-6F, "Then catches up");
        assertEquals(OrsaBootBeat.TICKS, OrsaBootBeat.replayTime(OrsaBootBeat.REPLAY_TICKS), 1e-6F, "Ends where the opening's does");
        int backwards = 0;
        for (int t = c; t < OrsaBootBeat.REPLAY_TICKS; t++) {
            float step = OrsaBootBeat.replayTime(t + 1) - OrsaBootBeat.replayTime(t);
            if (step < 0) backwards++;
            assertTrue(OrsaBootBeat.replayTime(t) <= OrsaBootBeat.TICKS);
        }
        assertEquals(1, backwards, "One skip back");
        for (int t = 0; t <= OrsaBootBeat.REPLAY_TICKS; t++) {
            int beat = t - c;
            assertEquals(beat == 30 || beat == 31 || beat == 49 || beat == 63, OrsaBootBeat.replayTears(t), "Tears at " + t);
        }
    }

    /** An old device warming up: the point of light catches, dies and catches again before the beat (owner, 2026-11-09). */
    @Test
    void theReplayColdStartsBeforeTheBeat() {
        int lit = 0;
        int dark = 0;
        for (int t = 0; t < OrsaBootBeat.SPUTTER; t++) {
            if (OrsaBootBeat.sputter(t) > 0.0F) lit++;
            else dark++;
        }
        assertTrue(lit >= 3 && dark >= 3, "It sputters: lit " + lit + ", dark " + dark);
        assertTrue(OrsaBootBeat.sputter(OrsaBootBeat.SPUTTER - 1) > 0.0F, "And has caught as the beat begins");
        assertEquals(0.0F, OrsaBootBeat.sputter(OrsaBootBeat.SPUTTER), 1e-6F, "The beat's own glint takes over");
    }

    @Test
    void theNeglectedScreenShips() throws Exception {
        ClassLoader loader = OrsaBootLookTest.class.getClassLoader();
        for (String name : new String[] {"orsa_replay_neglect_under", "orsa_replay_neglect_over"}) {
            try (var stream = loader.getResourceAsStream("assets/frozendawn/textures/gui/" + name + ".png")) {
                assertNotNull(stream, name);
                var image = javax.imageio.ImageIO.read(stream);
                assertEquals(320, image.getWidth(), name + " is drawn at GUI scale, a pixel a block-pixel");
                assertEquals(180, image.getHeight(), name);
            }
        }
    }

    @Test
    void theReplaySpinnerIsDrainedToGrey() throws Exception {
        java.awt.image.BufferedImage sheet;
        java.awt.image.BufferedImage muted;
        ClassLoader loader = OrsaBootLookTest.class.getClassLoader();
        try (var a = loader.getResourceAsStream("assets/frozendawn/textures/gui/orsa_logo_boot_sheet.png");
             var b = loader.getResourceAsStream("assets/frozendawn/textures/gui/orsa_logo_boot_sheet_muted.png")) {
            assertNotNull(b, "The replay's sheet ships");
            sheet = javax.imageio.ImageIO.read(a);
            muted = javax.imageio.ImageIO.read(b);
        }
        assertEquals(sheet.getWidth(), muted.getWidth());
        assertEquals(sheet.getHeight(), muted.getHeight());
        for (int y = 0; y < muted.getHeight(); y += 7) {
            for (int x = 0; x < muted.getWidth(); x += 7) {
                int p = muted.getRGB(x, y);
                assertEquals(sheet.getRGB(x, y) >>> 24, p >>> 24, "Same shape at " + x + "," + y);
                int r = p >> 16 & 0xFF;
                int b = p & 0xFF;
                if ((p >>> 24) > 0) assertTrue(Math.abs(r - b) <= 12, "Grey at " + x + "," + y);
            }
        }
    }

    @Test
    void theIntroLetsOnlyItsOwnSoundsThrough() {
        for (String path : new String[] {"ui.orsa_awakening_voice", "ui.orsa_awakening_ring", "terminal.boot_orsa",
                "ui.orsa_boot_sting_replay", "ui.orsa_awakening_voice_replay"}) {
            assertTrue(OrsaAwakeningIntro.introSound(path), path);
        }
        assertFalse(OrsaAwakeningIntro.introSound("ambient.wind_phase_one_calm"));
    }

    /** The replay's glitched sting and its voice, which breaks off script (owner preview, 2026-11-09). */
    @Test
    void theReplayHasItsOwnStingAndVoice() throws Exception {
        JsonObject sounds;
        ClassLoader loader = OrsaBootLookTest.class.getClassLoader();
        try (Reader reader = new InputStreamReader(loader.getResourceAsStream("assets/frozendawn/sounds.json"),
                StandardCharsets.UTF_8)) {
            sounds = JsonParser.parseReader(reader).getAsJsonObject();
        }
        String inventory;
        try (var stream = loader.getResourceAsStream("META-INF/frozendawn/SHIPPED_AUDIO_INVENTORY.tsv")) {
            inventory = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String name : new String[] {"orsa_boot_sting_replay", "orsa_awakening_voice_replay"}) {
            JsonObject event = sounds.getAsJsonObject("ui." + name);
            assertNotNull(event, name);
            assertEquals("frozendawn:ui/" + name, event.getAsJsonArray("sounds").get(0).getAsJsonObject()
                    .get("name").getAsString());
            assertNotNull(loader.getResource("assets/frozendawn/sounds/ui/" + name + ".ogg"), name + " ships");
            assertTrue(inventory.contains("assets/frozendawn/sounds/ui/" + name + ".ogg\t"), name + " is in the inventory");
        }
    }

    @Test
    void theIconGrowsFromAPointThenSettlesIntoTheO() {
        assertEquals(0.0F, OrsaBootBeat.growth(OrsaBootBeat.GLINT), 1e-6F, "A point first");
        assertEquals(1.0F, OrsaBootBeat.growth(OrsaBootBeat.GLINT + OrsaBootBeat.GROW), 1e-6F);
        float last = -1.0F;
        for (float t = 0; t <= OrsaBootBeat.TICKS; t += 0.25F) {
            float g = OrsaBootBeat.growth(t);
            assertTrue(g >= last, "Never shrinks back while growing: " + t);
            last = g;
        }
        int expandStart = OrsaBootBeat.GLINT + OrsaBootBeat.GROW;
        assertEquals(0.0F, OrsaBootBeat.expand(expandStart), 1e-6F);
        assertEquals(1.0F, OrsaBootBeat.expand(expandStart + OrsaBootBeat.EXPAND), 1e-6F);
        assertEquals(1.0F, OrsaBootBeat.iconAlpha(expandStart + OrsaBootBeat.EXPAND), 1e-6F, "Whole until the blend");
        assertEquals(0.0F, OrsaBootBeat.iconAlpha(OrsaBootBeat.TICKS), 1e-6F, "Gone into the masthead");
    }

    @Test
    void theSpinEasesToAStopOnTheMastheadPose() {
        int stop = OrsaBootBeat.GLINT + OrsaBootBeat.GROW + OrsaBootBeat.EXPAND;
        assertEquals(8, OrsaLogoRenderer.bootFrame(OrsaBootBeat.spinPhase(stop)));
        assertEquals(OrsaBootBeat.spinPhase(stop), OrsaBootBeat.spinPhase(OrsaBootBeat.TICKS), 1e-6F, "Still after");
        int decel = OrsaBootBeat.GLINT + OrsaBootBeat.GROW;
        float orbitPerTick = 1.0F / 22.0F;
        for (int t = 0; t < decel; t++) {
            assertEquals(orbitPerTick, OrsaBootBeat.spinPhase(t + 1) - OrsaBootBeat.spinPhase(t), 1e-4F,
                    "The terminals' speed while growing: " + t);
        }
        float lastStep = orbitPerTick;
        for (int t = decel; t < stop; t++) {
            float step = OrsaBootBeat.spinPhase(t + 1) - OrsaBootBeat.spinPhase(t);
            assertTrue(step > 0 && step <= lastStep + 1e-5F, "Slows smoothly, never backwards: " + t);
            lastStep = step;
        }
        assertTrue(lastStep < orbitPerTick * 0.01F, "And comes to rest");
    }

    /** The terminals, Aurora's first boot and the opening all play the one ORSA boot sound: the sting. */
    @Test
    void theBootSoundIsTheSting() throws Exception {
        JsonObject sounds;
        try (Reader reader = new InputStreamReader(OrsaBootLookTest.class.getClassLoader()
                .getResourceAsStream("assets/frozendawn/sounds.json"), StandardCharsets.UTF_8)) {
            sounds = JsonParser.parseReader(reader).getAsJsonObject();
        }
        assertEquals("frozendawn:ui/orsa_boot_sting", sounds.getAsJsonObject("terminal.boot_orsa")
                .getAsJsonArray("sounds").get(0).getAsString());
        assertNotNull(OrsaBootLookTest.class.getClassLoader()
                .getResource("assets/frozendawn/sounds/ui/orsa_boot_sting.ogg"), "The sting ships");
        String inventory;
        try (var stream = OrsaBootLookTest.class.getClassLoader()
                .getResourceAsStream("META-INF/frozendawn/SHIPPED_AUDIO_INVENTORY.tsv")) {
            inventory = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(inventory.contains("assets/frozendawn/sounds/ui/orsa_boot_sting.ogg\t"), "And is in the audio inventory");
    }
}
