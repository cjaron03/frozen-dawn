package com.frozendawn.power;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class SfrSoundAssetsTest {
    private static final String ASSETS = "/assets/frozendawn/";
    private static final Set<String> EVENTS = Set.of("blocks.sfr_cryo", "blocks.sfr_ignition", "blocks.sfr_burn", "blocks.sfr_pump",
            "blocks.sfr_alarm", "blocks.sfr_ready", "blocks.sfr_refuse", "blocks.sfr_disruption", "blocks.sfr_quench");

    @Test void everySfrSoundExistsAndIsMono() throws IOException {
        JsonObject sounds = JsonParser.parseString(new String(read("sounds.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        var found = new TreeSet<String>();
        for (var entry : sounds.entrySet()) {
            if (!entry.getKey().startsWith("blocks.sfr_")) continue;
            found.add(entry.getKey());
            for (var sound : entry.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                String name = sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString() : sound.getAsString();
                var file = "sounds/" + name.substring(name.indexOf(':') + 1) + ".ogg";
                assertEquals(1, channels(read(file)), file + " must be mono");
            }
        }
        assertEquals(new TreeSet<>(EVENTS), found);
    }

    private static byte[] read(String path) throws IOException {
        try (var in = SfrSoundAssetsTest.class.getResourceAsStream(ASSETS + path)) {
            assertNotNull(in, "missing " + path);
            return in.readAllBytes();
        }
    }
    /** Channel count from the Vorbis identification header: "\u0001vorbis", 4 version bytes, then channels. */
    private static int channels(byte[] ogg) {
        byte[] magic = "\u0001vorbis".getBytes(StandardCharsets.ISO_8859_1);
        outer:
        for (int i = 0; i + magic.length + 5 <= ogg.length; i++) {
            for (int j = 0; j < magic.length; j++) if (ogg[i + j] != magic[j]) continue outer;
            return ogg[i + magic.length + 4] & 0xFF;
        }
        return -1;
    }
}
