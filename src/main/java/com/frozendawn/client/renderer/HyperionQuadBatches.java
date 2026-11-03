package com.frozendawn.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/** Vertices queued per render layer during one frame, then written one layer at a time. A BufferSource ends a shared
 * layer's builder as soon as another layer is fetched, so a renderer that holds two layers' consumers and writes to
 * them in turn writes to an ended builder and crashes. Positions and normals are posed when queued. */
public final class HyperionQuadBatches<K> {
    /** x, y, z, u, v, argb, light, nx, ny, nz; the ints stored as float bits. */
    private static final int STRIDE = 10;
    private static final class Batch { float[] data = new float[STRIDE * 64]; int size; }
    private final Map<K, Batch> batches = new LinkedHashMap<>();
    private final Vector3f at = new Vector3f(), normal = new Vector3f();

    public void vertex(K layer, PoseStack.Pose p, float x, float y, float z, float u, float v, int argb, int light, float nx, float ny, float nz) {
        var b = batches.computeIfAbsent(layer, k -> new Batch());
        if (b.size + STRIDE > b.data.length) b.data = Arrays.copyOf(b.data, b.data.length * 2);
        p.pose().transformPosition(x, y, z, at); p.transformNormal(nx, ny, nz, normal);
        float[] d = b.data; int i = b.size;
        d[i] = at.x; d[i + 1] = at.y; d[i + 2] = at.z; d[i + 3] = u; d[i + 4] = v;
        d[i + 5] = Float.intBitsToFloat(argb); d[i + 6] = Float.intBitsToFloat(light); d[i + 7] = normal.x; d[i + 8] = normal.y; d[i + 9] = normal.z;
        b.size += STRIDE;
    }

    /** Writes every queued layer, each in one run from its own consumer, and empties the batches for the next frame. */
    public void flush(Function<K, VertexConsumer> source) {
        for (var e : batches.entrySet()) {
            var b = e.getValue();
            if (b.size == 0) continue;
            var vc = source.apply(e.getKey()); float[] d = b.data;
            for (int i = 0; i < b.size; i += STRIDE)
                vc.addVertex(d[i], d[i + 1], d[i + 2]).setColor(Float.floatToRawIntBits(d[i + 5])).setUv(d[i + 3], d[i + 4]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(Float.floatToRawIntBits(d[i + 6])).setNormal(d[i + 7], d[i + 8], d[i + 9]);
            b.size = 0;
        }
    }
}
