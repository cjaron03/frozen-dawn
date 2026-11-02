package com.frozendawn.power;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class HyperionTextureAssetsTest {
    private static final String ASSETS = "/assets/frozendawn/";
    private static final List<String> BLOCKS = List.of("superconducting_cell", "hyperion_controller", "hyperion_port", "cryocooler", "quench_vent");
    /** Drawn by the silo renderer rather than a block model. */
    private static final List<String> RENDERED = List.of("hyperion_ring", "hyperion_pipe_frost", "hyperion_pipe_bare");

    @Test void everyHyperionTextureExistsAt16Wide() throws IOException {
        var textures = new TreeSet<String>(RENDERED);
        for (var block : BLOCKS) for (var variant : json("blockstates/" + block + ".json").getAsJsonObject("variants").entrySet())
            collect(variant.getValue().getAsJsonObject().get("model").getAsString(), textures);
        for (var name : textures) assertEquals(16, image(name).getWidth(), name + " is 16 px wide");
    }

    @Test void formedCellsHaveNoEdge() throws IOException {
        var im = image("superconducting_cell_formed");
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int y = 2; y < 14; y++) for (int x = 2; x < 14; x++) { int v = luma(im, x, y); lo = Math.min(lo, v); hi = Math.max(hi, v); }
        for (int i = 0; i < 16; i++) for (int[] p : new int[][]{{i, 0}, {i, 15}, {0, i}, {15, i}}) {
            int v = luma(im, p[0], p[1]);
            assertTrue(v >= lo && v <= hi, "Border pixel " + p[0] + "," + p[1] + " (" + v + ") sits inside the interior's range " + lo + ".." + hi);
        }
    }

    @Test void theCubeLooksAreGone() {
        for (var name : List.of("superconducting_cell_frosted", "superconducting_cell_frosted_top", "superconducting_cell_stressed", "superconducting_cell_critical",
                "hyperion_controller_window", "hyperion_controller_inner", "hyperion_frame"))
            assertNull(HyperionTextureAssetsTest.class.getResource(ASSETS + "textures/block/" + name + ".png"), name + " is deleted");
    }

    /** Every texture a model names, following its parents inside this mod. */
    private static void collect(String model, TreeSet<String> out) throws IOException {
        if (!model.startsWith("frozendawn:")) return;
        var m = json("models/" + model.substring("frozendawn:".length()) + ".json");
        if (m.has("textures")) for (var t : m.getAsJsonObject("textures").entrySet()) {
            var ref = t.getValue().getAsString();
            if (ref.startsWith("frozendawn:block/")) out.add(ref.substring("frozendawn:block/".length()));
        }
        if (m.has("parent")) collect(m.get("parent").getAsString(), out);
    }
    private static JsonObject json(String path) throws IOException { return JsonParser.parseString(new String(read(path), StandardCharsets.UTF_8)).getAsJsonObject(); }
    private static BufferedImage image(String name) throws IOException { return ImageIO.read(new ByteArrayInputStream(read("textures/block/" + name + ".png"))); }
    private static int luma(BufferedImage im, int x, int y) { int c = im.getRGB(x, y); return ((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255); }
    private static byte[] read(String path) throws IOException {
        try (var in = HyperionTextureAssetsTest.class.getResourceAsStream(ASSETS + path)) {
            assertNotNull(in, "missing " + path);
            return in.readAllBytes();
        }
    }
}
