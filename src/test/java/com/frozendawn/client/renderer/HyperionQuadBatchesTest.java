package com.frozendawn.client.renderer;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HyperionQuadBatchesTest {
    /** Behaves like a BufferSource with shared layers: fetching one layer ends the builder of the layer fetched before. */
    private static final class StrictSource {
        final List<String> written = new ArrayList<>(); final List<float[]> positions = new ArrayList<>();
        Consumer current;
        VertexConsumer get(String layer) { if (current != null) current.ended = true; return current = new Consumer(layer); }
        final class Consumer implements VertexConsumer {
            final String layer; boolean ended;
            Consumer(String layer) { this.layer = layer; }
            private Consumer check() { if (ended) throw new IllegalStateException("Not building!"); return this; }
            @Override public VertexConsumer addVertex(float x, float y, float z) { check(); written.add(layer); positions.add(new float[]{x, y, z}); return this; }
            @Override public VertexConsumer setColor(int r, int g, int b, int a) { return check(); }
            @Override public VertexConsumer setUv(float u, float v) { return check(); }
            @Override public VertexConsumer setUv1(int u, int v) { return check(); }
            @Override public VertexConsumer setUv2(int u, int v) { return check(); }
            @Override public VertexConsumer setNormal(float x, float y, float z) { return check(); }
        }
    }

    /** Review finding: drawing metal and glow interleaved wrote to an ended builder and crashed the client. */
    @Test void interleavedLayersWriteOneLayerAtATime() {
        var q = new HyperionQuadBatches<String>(); var p = new PoseStack().last();
        for (var layer : new String[]{"metal", "glow", "metal", "glow", "flat"}) for (int i = 0; i < 4; i++) q.vertex(layer, p, i, 0, 0, 0, 0, -1, 0, 0, 0, -1);
        var source = new StrictSource();
        assertDoesNotThrow(() -> q.flush(source::get));
        assertEquals(20, source.written.size(), "Every vertex arrives");
        int runs = 1;
        for (int i = 1; i < source.written.size(); i++) if (!source.written.get(i).equals(source.written.get(i - 1))) runs++;
        assertEquals(3, runs, "Each layer arrives in one run: " + source.written);
        source.written.clear(); q.flush(source::get);
        assertTrue(source.written.isEmpty(), "A flush empties the batches");
    }

    @Test void verticesKeepThePoseTheyWereDrawnWith() {
        var q = new HyperionQuadBatches<String>(); var pose = new PoseStack();
        pose.translate(1, 2, 3); q.vertex("a", pose.last(), 0.5f, 0, 0, 0, 0, -1, 0, 0, 0, -1);
        pose.translate(10, 0, 0); q.vertex("a", pose.last(), 0, 0, 0, 0, 0, -1, 0, 0, 0, -1);
        var source = new StrictSource(); q.flush(source::get);
        assertArrayEquals(new float[]{1.5f, 2, 3}, source.positions.get(0), 1e-6f);
        assertArrayEquals(new float[]{11, 2, 3}, source.positions.get(1), 1e-6f);
    }
}
