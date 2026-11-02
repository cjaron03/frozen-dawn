package com.frozendawn.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Geometry shared by the Icarus renderers. Two kinds of buffer: a full-bright translucent one for bodies that may be dark
 * (the cold core, the shield, the rings) and the additive lightning one for light itself (glow, beams, the stream, arcs).
 * Fetch one, draw with it, then fetch the next: a shared batch ends when another type is requested. */
public final class IcarusDraw {
    public static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("neoforge", "textures/white.png");
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    /** Greyscale running-reactor textures from tools/generate_icarus_textures.py, tinted by vertex colour. */
    public static final ResourceLocation PLASMA = ResourceLocation.fromNamespaceAndPath("frozendawn", "textures/entity/icarus/plasma.png"),
            SHIELD = ResourceLocation.fromNamespaceAndPath("frozendawn", "textures/entity/icarus/shield.png"),
            GLOW = ResourceLocation.fromNamespaceAndPath("frozendawn", "textures/entity/icarus/glow.png");

    public interface Shade { int argb(double lat, double lon); }

    private IcarusDraw() {}

    public static RenderType body() { return RenderType.entityTranslucentEmissive(WHITE); }
    public static RenderType light() { return RenderType.lightning(); }
    /** A textured translucent surface, full-bright and seen from both sides. */
    public static RenderType surface(ResourceLocation texture) { return RenderType.entityTranslucentEmissive(texture); }
    /** A textured additive layer: it ignores alpha, so dim it through the colour (see glow). */
    public static RenderType additive(ResourceLocation texture) { return RenderType.energySwirl(texture, 0, 0); }
    /** rgb scaled by k, opaque: the colour of light of strength k in an additive layer. */
    public static int glow(int rgb, double k) {
        k = Math.clamp(k, 0, 1);
        return 0xFF000000 | (int) ((rgb >> 16 & 0xFF) * k) << 16 | (int) ((rgb >> 8 & 0xFF) * k) << 8 | (int) ((rgb & 0xFF) * k);
    }

    public static int argb(int rgb, double alpha) { return Math.clamp(Math.round(alpha * 255), 0, 255) << 24 | rgb & 0xFFFFFF; }

    /** A latitude-longitude sphere. In the body format each vertex carries its outward normal. */
    public static void sphere(VertexConsumer c, PoseStack.Pose pose, boolean body, float radius, int rings, int segments, Shade shade) {
        for (int i = 0; i < rings; i++) {
            double lat0 = -Math.PI / 2 + Math.PI * i / rings, lat1 = -Math.PI / 2 + Math.PI * (i + 1) / rings;
            for (int j = 0; j < segments; j++) {
                double lon0 = 2 * Math.PI * j / segments, lon1 = 2 * Math.PI * (j + 1) / segments;
                corner(c, pose, body, radius, lat0, lon0, shade);
                corner(c, pose, body, radius, lat0, lon1, shade);
                corner(c, pose, body, radius, lat1, lon1, shade);
                corner(c, pose, body, radius, lat1, lon0, shade);
            }
        }
    }
    private static void corner(VertexConsumer c, PoseStack.Pose pose, boolean body, float radius, double lat, double lon, Shade shade) {
        float nx = (float) (Math.cos(lat) * Math.cos(lon)), ny = (float) Math.sin(lat), nz = (float) (Math.cos(lat) * Math.sin(lon));
        int color = shade.argb(lat, lon);
        if (body) bodyVertex(c, pose, nx * radius, ny * radius, nz * radius, color, nx, ny, nz);
        else c.addVertex(pose.pose(), nx * radius, ny * radius, nz * radius).setColor(color);
    }

    /** A sphere in a body format with its texture tiled uTiles times around and vTiles times pole to pole, shifted by
     * (uShift, vShift) to animate it. */
    public static void texturedSphere(VertexConsumer c, PoseStack.Pose pose, float radius, int rings, int segments, float uTiles, float vTiles,
                                      float uShift, float vShift, Shade shade) {
        for (int i = 0; i < rings; i++) {
            double lat0 = -Math.PI / 2 + Math.PI * i / rings, lat1 = -Math.PI / 2 + Math.PI * (i + 1) / rings;
            for (int j = 0; j < segments; j++) {
                double lon0 = 2 * Math.PI * j / segments, lon1 = 2 * Math.PI * (j + 1) / segments;
                texturedCorner(c, pose, radius, lat0, lon0, uTiles, vTiles, uShift, vShift, shade);
                texturedCorner(c, pose, radius, lat0, lon1, uTiles, vTiles, uShift, vShift, shade);
                texturedCorner(c, pose, radius, lat1, lon1, uTiles, vTiles, uShift, vShift, shade);
                texturedCorner(c, pose, radius, lat1, lon0, uTiles, vTiles, uShift, vShift, shade);
            }
        }
    }
    private static void texturedCorner(VertexConsumer c, PoseStack.Pose pose, float radius, double lat, double lon, float uTiles, float vTiles,
                                       float uShift, float vShift, Shade shade) {
        float nx = (float) (Math.cos(lat) * Math.cos(lon)), ny = (float) Math.sin(lat), nz = (float) (Math.cos(lat) * Math.sin(lon));
        float u = (float) (lon / (2 * Math.PI)) * uTiles + uShift, v = (float) ((lat + Math.PI / 2) / Math.PI) * vTiles + vShift;
        c.addVertex(pose, nx * radius, ny * radius, nz * radius).setColor(shade.argb(lat, lon)).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }

    /** A square sprite of the given half-size facing the camera, centred on the pose's origin; rotate the pose to the
     * camera first. */
    public static void billboard(VertexConsumer c, PoseStack.Pose pose, float half, int argb) {
        float[][] q = {{-half, -half, 0, 1}, {half, -half, 1, 1}, {half, half, 1, 0}, {-half, half, 0, 0}};
        for (var p : q) c.addVertex(pose, p[0], p[1], 0).setColor(argb).setUv(p[2], p[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(pose, 0, 0, 1);
    }

    /** A billboard stretched to halfW by halfH, for a lens streak; rotate the pose to the camera first. */
    public static void streak(VertexConsumer c, PoseStack.Pose pose, float halfW, float halfH, int argb) {
        float[][] q = {{-halfW, -halfH, 0, 1}, {halfW, -halfH, 1, 1}, {halfW, halfH, 1, 0}, {-halfW, halfH, 0, 0}};
        for (var p : q) c.addVertex(pose, p[0], p[1], 0).setColor(argb).setUv(p[2], p[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(pose, 0, 0, 1);
    }

    /** A textured cube of the given half-size about the pose's origin, each face mapping the whole texture. */
    public static void cube(VertexConsumer c, PoseStack.Pose pose, float h, int argb) {
        float[][][] faces = {
                {{-h, -h, -h}, {-h, h, -h}, {h, h, -h}, {h, -h, -h}, {0, 0, -1}}, {{h, -h, h}, {h, h, h}, {-h, h, h}, {-h, -h, h}, {0, 0, 1}},
                {{-h, -h, h}, {-h, h, h}, {-h, h, -h}, {-h, -h, -h}, {-1, 0, 0}}, {{h, -h, -h}, {h, h, -h}, {h, h, h}, {h, -h, h}, {1, 0, 0}},
                {{-h, h, -h}, {-h, h, h}, {h, h, h}, {h, h, -h}, {0, 1, 0}}, {{-h, -h, h}, {-h, -h, -h}, {h, -h, -h}, {h, -h, h}, {0, -1, 0}}};
        float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
        for (var f : faces) for (int i = 0; i < 4; i++)
            c.addVertex(pose, f[i][0], f[i][1], f[i][2]).setColor(argb).setUv(uv[i][0], uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT).setNormal(pose, f[4][0], f[4][1], f[4][2]);
    }

    public static void bodyVertex(VertexConsumer c, PoseStack.Pose pose, float x, float y, float z, int argb, float nx, float ny, float nz) {
        c.addVertex(pose, x, y, z).setColor(argb).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }

    /** A beam of light as two crossed ribbons, each drawn both ways round so it shows from any side. */
    public static void beam(VertexConsumer c, PoseStack.Pose pose, Vec3 from, Vec3 to, float width, int argbFrom, int argbTo) {
        var dir = to.subtract(from);
        if (dir.lengthSqr() < 1e-8) return;
        dir = dir.normalize();
        var up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        var side = dir.cross(up).normalize().scale(width / 2);
        var lift = side.cross(dir).normalize().scale(width / 2);
        ribbon(c, pose, from, to, side, argbFrom, argbTo);
        ribbon(c, pose, from, to, lift, argbFrom, argbTo);
    }
    private static void ribbon(VertexConsumer c, PoseStack.Pose pose, Vec3 from, Vec3 to, Vec3 half, int a, int b) {
        var m = pose.pose();
        float[][] q = {
                {(float) (from.x - half.x), (float) (from.y - half.y), (float) (from.z - half.z)},
                {(float) (from.x + half.x), (float) (from.y + half.y), (float) (from.z + half.z)},
                {(float) (to.x + half.x), (float) (to.y + half.y), (float) (to.z + half.z)},
                {(float) (to.x - half.x), (float) (to.y - half.y), (float) (to.z - half.z)}};
        int[] colors = {a, a, b, b};
        for (int i = 0; i < 4; i++) c.addVertex(m, q[i][0], q[i][1], q[i][2]).setColor(colors[i]);
        for (int i = 3; i >= 0; i--) c.addVertex(m, q[i][0], q[i][1], q[i][2]).setColor(colors[i]);
    }

    /** A jagged bolt from one point to another, the same shape for the same seed. */
    public static void bolt(VertexConsumer c, PoseStack.Pose pose, Vec3 from, Vec3 to, long seed, int segments, double jitter, float width, int argb) {
        var random = RandomSource.create(seed);
        var last = from;
        for (int i = 1; i <= segments; i++) {
            var next = from.lerp(to, i / (double) segments);
            if (i < segments) next = next.add(random.nextGaussian() * jitter, random.nextGaussian() * jitter, random.nextGaussian() * jitter);
            beam(c, pose, last, next, width, argb, argb);
            last = next;
        }
    }

    /** A point on a sphere from latitude and longitude. */
    public static Vec3 onSphere(double radius, double lat, double lon) {
        return new Vec3(Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon)).scale(radius);
    }

    /** A flat ring of the given radii in the local XY plane, each segment coloured by its index; the body type does not
     * cull, so it shows from both sides. */
    public static void annulus(VertexConsumer c, PoseStack.Pose pose, float inner, float outer, float z, int segments, IntUnaryOperator colorOf) {
        for (int j = 0; j < segments; j++) {
            double a0 = 2 * Math.PI * j / segments, a1 = 2 * Math.PI * (j + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            int argb = colorOf.applyAsInt(j);
            bodyVertex(c, pose, c0 * inner, s0 * inner, z, argb, 0, 0, -1);
            bodyVertex(c, pose, c0 * outer, s0 * outer, z, argb, 0, 0, -1);
            bodyVertex(c, pose, c1 * outer, s1 * outer, z, argb, 0, 0, -1);
            bodyVertex(c, pose, c1 * inner, s1 * inner, z, argb, 0, 0, -1);
        }
    }

    /** The outer wall of a short cylinder of the given radius along local Z. */
    public static void band(VertexConsumer c, PoseStack.Pose pose, float radius, float z0, float z1, int segments, IntUnaryOperator colorOf) {
        for (int j = 0; j < segments; j++) {
            double a0 = 2 * Math.PI * j / segments, a1 = 2 * Math.PI * (j + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            int argb = colorOf.applyAsInt(j);
            bodyVertex(c, pose, c0 * radius, s0 * radius, z0, argb, c0, s0, 0);
            bodyVertex(c, pose, c1 * radius, s1 * radius, z0, argb, c1, s1, 0);
            bodyVertex(c, pose, c1 * radius, s1 * radius, z1, argb, c1, s1, 0);
            bodyVertex(c, pose, c0 * radius, s0 * radius, z1, argb, c0, s0, 0);
        }
    }
}
