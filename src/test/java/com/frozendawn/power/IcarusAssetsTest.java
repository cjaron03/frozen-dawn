package com.frozendawn.power;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.client.IcarusBlastEffects;
import com.frozendawn.client.SoundMuffler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class IcarusAssetsTest {
    private static final String ASSETS = "/assets/frozendawn/";
    private static final List<String> BLOCKS = List.of("halsey_core", "icarus_stabilizer", "icarus_injector", "icarus_frame",
            "icarus_injector_casing", "icarus_stabilizer_housing", "icarus_reserve_bank", "icarus_controller", "icarus_power_port",
            "icarus_matter_port", "icarus_capsule_dock", "antiparticle_foundry");
    private static final Set<String> SOUNDS = Set.of("blocks.icarus_frame_hum", "blocks.icarus_stabilizer_spinup", "blocks.icarus_stabilizer_whine",
            "blocks.icarus_shield_hum", "blocks.icarus_core_roar", "blocks.icarus_injector_feed", "blocks.icarus_capsule_dock",
            "blocks.icarus_ignition", "blocks.icarus_clip_slam", "blocks.icarus_cole_complete", "blocks.icarus_alarm_caution",
            "blocks.icarus_alarm_warning", "blocks.icarus_alarm_critical", "blocks.icarus_boom", "blocks.icarus_ringing", "blocks.icarus_rumble",
            "blocks.icarus_vacuum_thud", "blocks.icarus_power_on", "blocks.icarus_shield_form", "blocks.icarus_core_song", "blocks.icarus_wind_down", "blocks.icarus_core_collapse", "blocks.icarus_shield_hit", "blocks.icarus_stabilizer_sputter", "blocks.icarus_klaxon", "blocks.icarus_strain", "blocks.icarus_core_thump",
            "ui.suit.icarus_caution", "ui.suit.icarus_warning", "ui.suit.icarus_critical", "ui.suit.icarus_radiation");

    @Test void everyIcarusBlockHasUniqueTextures() throws Exception {
        var owner = new HashMap<String, String>();
        var icarusModels = new HashSet<String>();
        for (var block : BLOCKS) {
            var models = new TreeSet<String>();
            collectModels(json("blockstates/" + block + ".json"), models);
            assertFalse(models.isEmpty(), block + " has no model");
            read("models/item/" + block + ".json");
            var textures = new TreeSet<String>();
            for (var model : models) textures(model, textures, icarusModels);
            assertFalse(textures.isEmpty(), block + " has no texture");
            for (var texture : textures) {
                read("textures/" + path(texture) + ".png");
                var previous = owner.putIfAbsent(texture, block);
                assertTrue(previous == null, texture + " is shared by " + previous + " and " + block);
            }
        }
        try (Stream<Path> files = Files.walk(dir("models"))) {
            for (var file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                var name = "frozendawn:" + dir("models").relativize(file).toString().replace('\\', '/').replaceAll("\\.json$", "");
                if (icarusModels.contains(name)) continue;
                var tex = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("textures");
                if (tex == null) continue;
                for (var e : tex.entrySet()) {
                    var t = qualify(e.getValue().getAsString());
                    assertFalse(owner.containsKey(t), name + " reuses Icarus texture " + t);
                }
            }
        }
    }

    @Test void everyIcarusSoundHasAnAssetAndBlockSoundsAreMono() throws IOException {
        JsonObject sounds = json("sounds.json").getAsJsonObject();
        var found = new TreeSet<String>();
        for (var entry : sounds.entrySet()) {
            if (!entry.getKey().startsWith("blocks.icarus_") && !entry.getKey().startsWith("ui.suit.icarus_")) continue;
            found.add(entry.getKey());
            for (var sound : entry.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                String name = sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString() : sound.getAsString();
                var file = "sounds/" + path(name) + ".ogg";
                // Positional block sounds must be mono to attenuate; suit lines share the stereo Piper pipeline.
                if (entry.getKey().startsWith("blocks.")) assertEquals(1, channels(read(file)), file + " must be mono");
                else assertTrue(channels(read(file)) > 0, file + " must be readable");
            }
        }
        assertEquals(new TreeSet<>(SOUNDS), found);
    }

    @Test void ringingEarsDoNotDullTheBlastItself() {
        assertTrue(IcarusBlastEffects.isBlastSound(ResourceLocation.fromNamespaceAndPath("frozendawn", "blocks.icarus_boom")));
        assertTrue(IcarusBlastEffects.isBlastSound(ResourceLocation.fromNamespaceAndPath("frozendawn", "blocks.icarus_ringing")));
        assertFalse(IcarusBlastEffects.isBlastSound(ResourceLocation.fromNamespaceAndPath("frozendawn", "blocks.icarus_core_roar")));
        assertFalse(IcarusBlastEffects.isBlastSound(ResourceLocation.withDefaultNamespace("block.stone.break")));
    }

    @Test void icarusSoundsPassTheVacuumMuffler() {
        assertTrue(SoundMuffler.isVacuumExempt(ResourceLocation.fromNamespaceAndPath("frozendawn", "blocks.icarus_boom")));
        assertTrue(SoundMuffler.isVacuumExempt(ResourceLocation.fromNamespaceAndPath("frozendawn", "blocks.icarus_core_roar")));
        assertTrue(SoundMuffler.isVacuumExempt(ResourceLocation.fromNamespaceAndPath("frozendawn", "ui.suit.icarus_radiation")));
        assertTrue(SoundMuffler.isVacuumExempt(ResourceLocation.fromNamespaceAndPath("frozendawn", "blocks.sfr_burn")));
        assertFalse(SoundMuffler.isVacuumExempt(ResourceLocation.fromNamespaceAndPath("minecraft", "blocks.icarus_boom")));
        assertFalse(SoundMuffler.isVacuumExempt(ResourceLocation.withDefaultNamespace("block.stone.break")));
    }

    private static void collectModels(JsonElement e, Set<String> out) {
        if (e.isJsonObject()) {
            for (var entry : e.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive()) out.add(qualify(entry.getValue().getAsString()));
                else collectModels(entry.getValue(), out);
            }
        } else if (e.isJsonArray()) for (var item : e.getAsJsonArray()) collectModels(item, out);
    }
    /** The model's own textures and its frozendawn parents', skipping #references. */
    private static void textures(String model, Set<String> out, Set<String> seen) throws IOException {
        if (!model.startsWith("frozendawn:") || !seen.add(model)) return;
        var json = json("models/" + path(model) + ".json").getAsJsonObject();
        var tex = json.getAsJsonObject("textures");
        if (tex != null) for (var e : tex.entrySet()) {
            var v = e.getValue().getAsString();
            if (!v.startsWith("#")) out.add(qualify(v));
        }
        if (json.has("parent")) textures(qualify(json.get("parent").getAsString()), out, seen);
    }
    private static String qualify(String id) { return id.contains(":") ? id : "minecraft:" + id; }
    private static String path(String id) { return id.substring(id.indexOf(':') + 1); }
    private static JsonElement json(String path) throws IOException { return JsonParser.parseString(new String(read(path), StandardCharsets.UTF_8)); }
    private static Path dir(String path) throws URISyntaxException {
        return Path.of(IcarusAssetsTest.class.getResource(ASSETS + path).toURI());
    }
    private static byte[] read(String path) throws IOException {
        try (var in = IcarusAssetsTest.class.getResourceAsStream(ASSETS + path)) {
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
