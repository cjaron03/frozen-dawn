package com.frozendawn.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void theBeatRunsOnlyInTheOpening() {
        assertEquals(OrsaBootBeat.TICKS, OrsaAwakeningIntro.bootBeatLength(false));
        assertEquals(0, OrsaAwakeningIntro.bootBeatLength(true), "The replay keeps its own draw-in");
        assertEquals(OrsaBootBeat.GLINT + OrsaBootBeat.GROW + OrsaBootBeat.EXPAND + OrsaBootBeat.BLEND,
                OrsaBootBeat.TICKS);
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
