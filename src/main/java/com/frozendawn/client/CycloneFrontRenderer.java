package com.frozendawn.client;

import com.frozendawn.cyclone.CycloneField;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** Layered horizon bank following the analytic front, with no world queries or extra particles. */
public final class CycloneFrontRenderer {
    private static final int COLUMNS = 64;
    private static final int CLOUD_ROWS = 10;
    private static final int HAZE_ROWS = 5;
    private static final float RADIUS = 128;

    private CycloneFrontRenderer() {}

    public static void render(Matrix4f view, float partial, Camera camera, Runnable skyFogSetup) {
        var storm = CycloneClientState.storm();
        if (storm.isEmpty()) return;
        var s = storm.get();
        var p = camera.getPosition();
        double time = CycloneClientState.time(partial);
        double distance = CycloneField.distanceToLeadingEdge(s, p.x, p.z, time);
        if (s.collapsing() || time < s.startGameTime() || time >= s.endGameTime()
                || !CycloneField.inTrack(s, p.x, p.z) || distance <= 0 || distance >= 4000) return;
        float exposure = StormExposureController.getExposure();
        if (exposure <= 0) return;

        double cross = CycloneField.cross(s, p.x, p.z);
        double dx = -Math.cos(s.headingRad()) * distance + Math.sin(s.headingRad()) * cross;
        double dz = -Math.sin(s.headingRad()) * distance - Math.cos(s.headingRad()) * cross;
        double bearing = Math.atan2(dz, dx);
        double spread = Math.atan2(CycloneField.HALF_WIDTH, Math.max(100, distance));
        float approach = smooth(0, 1, 1 - distance / 4000);
        // Blend in at the visibility limit and hand off to local weather as the edge arrives.
        float envelope = exposure * smooth(0, 900, 4000 - distance) * smooth(0, 140, distance)
                * smooth(0, CycloneField.EDGE_FADE, CycloneField.HALF_WIDTH - Math.abs(cross));
        if (envelope <= 0) return;
        float height = 10 + 100 * approach;
        float opacity = envelope * (.32f + .40f * approach);
        double phase = (s.seed() & 65535L) / 4096.0;
        double drift = time / 700.0;

        var poses = new PoseStack();
        poses.mulPose(view);
        var matrix = poses.last().pose();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        FogRenderer.setupNoFog();
        try {
            var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            // Back to front: a higher, softer bank, then two darker banks with independent folds.
            for (int layer = 0; layer < 3; layer++) {
                band(buffer, matrix, bearing, spread * (1.10 - .035 * layer), height,
                        opacity, drift, phase, layer, false);
            }
            band(buffer, matrix, bearing, spread * 1.13, 5 + 18 * approach,
                    envelope * (.16f + .16f * approach), drift, phase + 7, 0, true);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            skyFogSetup.run();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    private static void band(BufferBuilder buffer, Matrix4f matrix, double bearing, double spread,
                             float height, float opacity, double time, double phase, int layer, boolean haze) {
        int rows = haze ? HAZE_ROWS : CLOUD_ROWS;
        float radius = RADIUS - layer * 3 - (haze ? 10 : 0);
        float base = haze ? -9 : -16 + layer * 3;
        for (int column = 0; column < COLUMNS; column++) {
            double u = -1 + 2.0 * column / COLUMNS;
            double next = -1 + 2.0 * (column + 1) / COLUMNS;
            double angle = bearing + spread * u, nextAngle = bearing + spread * next;
            float x = (float) Math.cos(angle) * radius, z = (float) Math.sin(angle) * radius;
            float nx = (float) Math.cos(nextAngle) * radius, nz = (float) Math.sin(nextAngle) * radius;
            double layerPhase = phase + layer * 2.31;
            float top = crest(u, height, time, layerPhase, layer);
            float nextTop = crest(next, height, time, layerPhase, layer);
            for (int row = 0; row < rows; row++) {
                double v = (double) row / rows, above = (double) (row + 1) / rows;
                vertex(buffer, matrix, x, base, z, top, u, v, opacity, time, layerPhase, layer, haze);
                vertex(buffer, matrix, nx, base, nz, nextTop, next, v, opacity, time, layerPhase, layer, haze);
                vertex(buffer, matrix, nx, base, nz, nextTop, next, above, opacity, time, layerPhase, layer, haze);
                vertex(buffer, matrix, x, base, z, top, u, above, opacity, time, layerPhase, layer, haze);
            }
        }
    }

    private static float crest(double u, float height, double time, double phase, int layer) {
        return height * (1.13f - layer * .13f) * (.72f + .40f * folds(u * 2, .17, time, phase));
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float base, float z,
                               float top, double u, double v, float opacity, double time,
                               double phase, int layer, boolean haze) {
        float side = smooth(0, .24, 1 - Math.abs(u));
        float vertical = smooth(0, .20, v) * (1 - smooth(haze ? .30 : .50, 1, v));
        float detail = folds(u * 3 + time * .025, v * 2, time, phase);
        float alpha = opacity * side * vertical * (.45f + .55f * detail);
        float shade = .76f + .24f * detail;
        float red, green, blue;
        if (haze) {
            // The low snow veil stays readable against the dark bank, without emitting light.
            red = .25f; green = .30f; blue = .37f;
        } else {
            red = .115f - layer * .026f;
            green = .140f - layer * .027f;
            blue = .185f - layer * .030f;
            shade *= .85f + .15f * (float) v;
        }
        float flash = haze ? 0 : CycloneThunderClient.frontFlash(u, v);
        red = red * shade + (.62f - red * shade) * flash;
        green = green * shade + (.76f - green * shade) * flash;
        blue = blue * shade + (.94f - blue * shade) * flash;
        buffer.addVertex(matrix, x, base + (top - base) * (float) v, z)
                .setColor(red, green, blue, alpha);
    }

    /** Low spatial frequencies keep broad billows coherent as the bank approaches. */
    private static float folds(double x, double y, double time, double phase) {
        double value = Math.sin(x * 2.7 + y * 1.8 + time * .22 + phase)
                + .5 * Math.sin(x * 5.6 - y * 3.4 - time * .15 + phase * 1.7)
                + .25 * Math.sin(x * 11.5 + y * 5.3 + time * .10 + phase * .6);
        return (float) (.5 + value / 3.5);
    }

    private static float smooth(double low, double high, double value) {
        double f = Math.clamp((value - low) / (high - low), 0, 1);
        return (float) (f * f * (3 - 2 * f));
    }
}
