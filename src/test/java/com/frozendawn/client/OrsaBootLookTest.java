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
