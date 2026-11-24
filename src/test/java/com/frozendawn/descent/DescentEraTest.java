package com.frozendawn.descent;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DescentEraTest {
    private static final String ASSETS = "/assets/frozendawn/";

    @Test void theCountFallsGoingUp() {
        assertEquals(List.of(DescentEra.HOUSE_1816, DescentEra.MAUNDER, DescentEra.FAMINE, DescentEra.LATE_ANTIQUE,
                DescentEra.BRONZE_AGE, DescentEra.FOUR_KILOYEAR, DescentEra.EIGHT_KILOYEAR, DescentEra.YOUNGER_DRYAS,
                DescentEra.GLACIAL_MAXIMUM), List.of(DescentEra.values()), "descent order, top to bottom");
        assertEquals(2, DescentEra.HOUSE_1816.standing());
        int expected = 3;
        for (DescentEra era : DescentEra.values()) {
            if (era == DescentEra.HOUSE_1816) continue;
            assertEquals(expected++, era.standing(), era.name());
        }
        assertEquals(11, expected, "Maunder to the Last Glacial Maximum stand 3 to 10");
    }

    @Test void eachEraHasItsOwnQuietSound() throws IOException {
        JsonObject sounds = JsonParser.parseString(new String(read("sounds.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        Set<String> files = new HashSet<>();
        for (DescentEra era : DescentEra.values()) {
            String file = quietSound(sounds, era.soundId(), "block", "block/descent/");
            assertTrue(files.add(file), era + " shares " + file);
            byte[] ogg = read("sounds/" + file + ".ogg");
            int clip = (int) Math.ceil(seconds(ogg) * 20);
            assertEquals(clip, era.clipTicks(), era + " clip length in ticks");
        }
        quietSound(sounds, "ambient.descent.icicle", "ambient", "ambient/descent/");
    }

    /** The event's single sound: category, volume 0.6, heard to 8 blocks, not streamed, mono. Returns its file path. */
    private static String quietSound(JsonObject sounds, String event, String category, String folder) throws IOException {
        JsonObject entry = sounds.getAsJsonObject(event);
        assertNotNull(entry, "missing event " + event);
        assertEquals(category, entry.get("category").getAsString(), event);
        assertEquals("subtitle.frozendawn." + event, entry.get("subtitle").getAsString(), event);
        var list = entry.getAsJsonArray("sounds");
        assertEquals(1, list.size(), event);
        JsonObject sound = list.get(0).getAsJsonObject();
        String name = sound.get("name").getAsString();
        assertTrue(name.startsWith("frozendawn:" + folder), event + " -> " + name);
        assertEquals(8, sound.get("attenuation_distance").getAsInt(), event);
        assertEquals(0.6, sound.get("volume").getAsDouble(), 1e-9, event);
        assertFalse(sound.has("stream") && sound.get("stream").getAsBoolean(), event + " must not stream");
        String file = name.substring(name.indexOf(':') + 1);
        assertEquals(1, channels(read("sounds/" + file + ".ogg")), file + " must be mono");
        return file;
    }

    private static final Set<String> PLAIN_WORDS = Set.of("a", "an", "and", "the", "bell", "rings", "small", "lyre",
            "plays", "play", "horn", "sounds", "drum", "whistle", "stone", "bone", "flute", "music", "box", "church", "monastery",
            "bronze", "lur", "bull", "headed", "water", "drips", "ink", "panel", "glass", "mosaic", "clay", "plaster",
            "carved", "ochre", "mark");

    @Test void noDescentTextSaysMore() throws IOException {
        JsonObject lang = JsonParser.parseString(new String(read("lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        int checked = 0;
        for (var entry : lang.entrySet()) {
            if (!entry.getKey().matches("(.*\\.)?descent[._].*")) continue;
            checked++;
            String value = entry.getValue().getAsString();
            assertFalse(value.matches(".*\\d.*"), entry.getKey() + " carries a number: " + value);
            assertFalse(value.toLowerCase(Locale.ROOT).contains("continuity"), entry.getKey());
            for (String word : value.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
                if (!word.isEmpty()) assertTrue(PLAIN_WORDS.contains(word), entry.getKey() + " says more than a plain description: " + word);
            }
        }
        assertTrue(checked >= DescentEra.values().length + 1, "the descent subtitles are in the lang file");
    }

    static byte[] read(String path) throws IOException {
        try (var in = DescentEraTest.class.getResourceAsStream(ASSETS + path)) {
            assertNotNull(in, "missing " + path);
            return in.readAllBytes();
        }
    }

    /** Channel count from the Vorbis identification header: "\u0001vorbis", 4 version bytes, then channels. */
    private static int channels(byte[] ogg) {
        int at = idHeader(ogg);
        return at < 0 ? -1 : ogg[at + 4] & 0xFF;
    }

    /** Length from the last Ogg page's granule position over the identification header's sample rate. */
    private static double seconds(byte[] ogg) {
        int at = idHeader(ogg);
        long rate = (ogg[at + 5] & 0xFFL) | (ogg[at + 6] & 0xFFL) << 8 | (ogg[at + 7] & 0xFFL) << 16 | (ogg[at + 8] & 0xFFL) << 24;
        for (int i = ogg.length - 27; i >= 0; i--) {
            if (ogg[i] == 'O' && ogg[i + 1] == 'g' && ogg[i + 2] == 'g' && ogg[i + 3] == 'S') {
                long granule = 0;
                for (int b = 7; b >= 0; b--) granule = granule << 8 | (ogg[i + 6 + b] & 0xFFL);
                return granule / (double) rate;
            }
        }
        return -1;
    }

    /** Offset just past "\u0001vorbis"; the version, channels and rate follow. */
    private static int idHeader(byte[] ogg) {
        byte[] magic = "\u0001vorbis".getBytes(StandardCharsets.ISO_8859_1);
        outer:
        for (int i = 0; i + magic.length + 9 <= ogg.length; i++) {
            for (int j = 0; j < magic.length; j++) if (ogg[i + j] != magic[j]) continue outer;
            return i + magic.length;
        }
        return -1;
    }
}
