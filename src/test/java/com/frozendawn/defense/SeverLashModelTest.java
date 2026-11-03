package com.frozendawn.defense;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

/** The 3D Lash: one model per heat, firing and vent state, chosen by the overrides in {@code sever_lash.json}. */
class SeverLashModelTest {
    private static final String ASSETS = "/assets/frozendawn/";
    private static final Set<Double> VANILLA_ANGLES = Set.of(-45.0, -22.5, 0.0, 22.5, 45.0);

    private static List<JsonObject> overrides() throws IOException {
        var list = new ArrayList<JsonObject>();
        for (var o : model("sever_lash").getAsJsonArray("overrides")) list.add(o.getAsJsonObject());
        return list;
    }
    private static String path(JsonObject override) { return override.get("model").getAsString().substring("frozendawn:item/".length()); }

    @Test
    void everyStateHasItsModelAndTextures() throws IOException {
        var names = new ArrayList<String>(List.of("sever_lash"));
        for (var o : overrides()) names.add(path(o));
        for (var name : names) {
            var model = model(name);
            var textures = model.getAsJsonObject("textures");
            assertTrue(textures.has("particle"), name + " needs a particle texture");
            for (var texture : textures.entrySet()) {
                var id = texture.getValue().getAsString().substring("frozendawn:".length());
                try (InputStream in = SeverLashModelTest.class.getResourceAsStream(ASSETS + "textures/" + id + ".png")) {
                    assertNotNull(in, name + " needs texture " + id);
                }
            }
            for (var element : elements(model))
                for (var face : element.getAsJsonObject("faces").entrySet()) {
                    var ref = face.getValue().getAsJsonObject().get("texture").getAsString().substring(1);
                    assertTrue(textures.has(ref), name + " " + element.get("name") + " uses an undefined #" + ref);
                    // Vanilla does not clamp face UVs: past 16 a face samples the neighbouring sprite in the atlas.
                    for (var uv : face.getValue().getAsJsonObject().getAsJsonArray("uv"))
                        assertTrue(uv.getAsDouble() >= 0 && uv.getAsDouble() <= 16, name + " " + element.get("name") + " " + face.getKey() + " UV leaves its tile");
                }
        }
    }

    @Test
    void ventStatesComeLastAndInOrder() throws IOException {
        var order = new ArrayList<String>();
        for (var o : overrides()) {
            var keys = new ArrayList<>(o.getAsJsonObject("predicate").keySet());
            Collections.sort(keys);
            order.add(String.join("+", keys));
        }
        // The last matching override wins, and a locked-out Lash is also at full heat: the vent states come last.
        assertEquals(List.of("frozendawn:heat", "frozendawn:heat", "frozendawn:firing", "frozendawn:firing+frozendawn:heat",
                "frozendawn:firing+frozendawn:heat", "frozendawn:vent", "frozendawn:vent", "frozendawn:vent", "frozendawn:vent",
                "frozendawn:vent", "frozendawn:vent"), order);
        double last = 0;
        for (var o : overrides().subList(5, 11)) {
            double vent = o.getAsJsonObject("predicate").get("frozendawn:vent").getAsDouble();
            assertTrue(vent > last && vent <= 1, "Vent thresholds rise");
            last = vent;
        }
        assertEquals(LashHeat.VENT_SHUT / 8.0, last, 1e-9, "The last vent override is the last stage, in eighths");
    }

    @Test
    void theFinsLiftThenFoldInVanillaSteps() throws IOException {
        var expected = Map.of(LashHeat.VENT_OPENING, 22.5, LashHeat.VENT_OPEN, 45.0, LashHeat.VENT_COOLING, 45.0,
                LashHeat.VENT_COOLED, 45.0, LashHeat.VENT_CLOSING, 22.5, LashHeat.VENT_SHUT, 0.0);
        for (var o : overrides()) {
            var model = model(path(o));
            var vent = o.getAsJsonObject("predicate").get("frozendawn:vent");
            double want = vent == null ? 0 : expected.get((int) Math.round(vent.getAsDouble() * 8));
            int fins = 0;
            for (var element : elements(model)) {
                var rotation = element.getAsJsonObject("rotation");
                double angle = rotation == null ? 0 : rotation.get("angle").getAsDouble();
                assertTrue(VANILLA_ANGLES.contains(angle), "Vanilla rotates elements only in 22.5 degree steps");
                if (name(element).startsWith("fin")) { fins++; assertEquals(want, angle, 1e-9, path(o) + " fin angle"); }
            }
            assertEquals(6, fins, "Three fins, each a base and a tip");
        }
    }

    @Test
    void theLensAndHotPartsAreFullbright() throws IOException {
        for (var o : overrides()) {
            var model = model(path(o));
            var predicate = o.getAsJsonObject("predicate");
            // Venting fins glow until the lockout ends, white through orange to red.
            boolean hot = predicate.has("frozendawn:vent")
                    || predicate.has("frozendawn:heat") && predicate.get("frozendawn:heat").getAsDouble() >= .8;
            for (var element : elements(model)) {
                var name = name(element);
                if (name.equals("lens") || name.equals("vent_slot")) assertTrue(fullbright(element), path(o) + " " + name + " glows");
                if (name.startsWith("fin") || name.equals("barrel_tip")) assertEquals(hot, fullbright(element), path(o) + " " + name);
                if (name.equals("body") || name.startsWith("grip")) assertFalse(fullbright(element), "The body is lit by the world");
            }
        }
        for (var element : elements(model("sever_lash")))
            assertFalse(name(element).startsWith("fin") && fullbright(element), "Cool fins are lit by the world");
    }

    @Test
    void firingRaisesTheLashOnlyInThirdPerson() throws IOException {
        var idle = model("sever_lash").getAsJsonObject("display");
        var firing = model("sever_lash_firing").getAsJsonObject("display");
        assertEquals(idle.get("firstperson_righthand"), firing.get("firstperson_righthand"));
        assertEquals(idle.get("gui"), firing.get("gui"));
        assertNotEquals(idle.get("thirdperson_righthand"), firing.get("thirdperson_righthand"));
    }

    private static boolean fullbright(JsonObject element) {
        var data = element.getAsJsonObject("neoforge_data");
        return data != null && data.get("block_light").getAsInt() == 15 && data.get("sky_light").getAsInt() == 15;
    }
    private static String name(JsonObject element) { return element.get("name").getAsString(); }
    private static List<JsonObject> elements(JsonObject model) {
        var list = new ArrayList<JsonObject>();
        for (JsonElement e : model.getAsJsonArray("elements")) list.add(e.getAsJsonObject());
        return list;
    }

    private static JsonObject model(String name) throws IOException {
        try (var in = SeverLashModelTest.class.getResourceAsStream(ASSETS + "models/item/" + name + ".json")) {
            assertNotNull(in, "missing model " + name);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
