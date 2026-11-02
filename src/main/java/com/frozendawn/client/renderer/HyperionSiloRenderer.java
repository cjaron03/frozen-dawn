package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.HyperionControllerBlock;
import com.frozendawn.block.HyperionControllerBlockEntity;
import com.frozendawn.block.CryocoolerBlock;
import com.frozendawn.block.HyperionFacingPartBlock;
import com.frozendawn.client.HyperionSiloEffects;
import com.frozendawn.power.HyperionGauge;
import com.frozendawn.power.HyperionGauge.Colour;
import com.frozendawn.power.HyperionGauge.Lamps;
import com.frozendawn.power.HyperionGauge.Motion;
import com.frozendawn.power.HyperionGauge.Pipes;
import com.frozendawn.power.HyperionGauge.Rings;
import com.frozendawn.power.HyperionSiloLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;

/** Everything the formed silo shows beyond its blocks, from the Controller: the window of stacked bars over a rising
 * faint charge column, a plinth, edge trims and a cap round the vent, the status lamps round the base, the frosted pipes out of each
 * Cryocooler into the floor, and the thin dark rings that
 * float round the silo with a glowing inner edge, spin with the flow, wobble when hot and drop on a quench. What each shows comes
 * from HyperionGauge. Model space faces north with the Controller's block at the origin, so the silo spans x -1..2,
 * z 0..3 and its axis is (0.5, 1.5). Glowing parts are emissive; the rest takes the light in front of the Controller.
 * Everything is queued per layer and written one layer at a time at the end, never two layers interleaved. */
public final class HyperionSiloRenderer implements BlockEntityRenderer<HyperionControllerBlockEntity> {
    static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    static final ResourceLocation SWIRL = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "block/hyperion_window_swirl");
    static final ResourceLocation RING = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "block/hyperion_ring");
    static final ResourceLocation PIPE_FROST = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "block/hyperion_pipe_frost");
    static final ResourceLocation PIPE_BARE = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "block/hyperion_pipe_bare");
    static final float PX = 1 / 16f, AXIS_X = 0.5f, AXIS_Z = 1.5f;
    /** The window: a dark recess the width of the gauge column less a 1 px frame, from the plinth to 2 px under the top.
     * Bars are 12 x 6 px, one per block, with dark gaps between them. The plinth is a lighter band round the base. */
    private static final float WIN_X0 = PX, WIN_X1 = 15 * PX, PLINTH = 4 * PX, WIN_TOP_GAP = 2 * PX,
            BAR_X0 = 2 * PX, BAR_X1 = 14 * PX, BAR_Y0 = 5 * PX, BAR_Y1 = 11 * PX;
    /** The cap round the top vent: a rim CAP_PX thick and CAP_TALL_PX tall on the vent block's edges. */
    private static final float CAP = 2 * PX, CAP_TALL = 3 * PX;
    /** The swirl glows this bright behind the bars, so it reads as a faint charge and not as a blue slab. */
    private static final float SWIRL_BRIGHT = 0.3f;
    private static final int SLOT = 0xFF161C25, BACKDROP = 0xFF08090B, TRIM = 0xFF0B0C0E, EDGE_LIGHT = 0xFF3A3E45, PLINTH_C = 0xFF2B2E34,
            CAP_C = 0xFF4A5059, DEAD = 0xFF5A5C62, EMBER = 0xFF8A2614, LAMP_OFF = 0xFF2A2018, RING_DIM = 0xFF3C5A78;
    /** A quench drops each ring in DROP_TICKS, easing in, each DROP_STAGGER after the one above; they lift back over LIFT_TICKS. */
    private static final float DROP_TICKS = 8, DROP_STAGGER = 2, LIFT_TICKS = 40, BOB_TICKS = 60;
    private static final float RING_R = HyperionSiloLayout.RING_INNER_PX * PX, RING_W = HyperionSiloLayout.RING_WIDTH_PX * PX, RING_T = HyperionSiloLayout.RING_TALL_PX * PX;
    /** Each segment a little longer than its share of the circle, so neighbours overlap and no gap shows. */
    private static final float SEGMENT = (float) (2 * Math.PI * (RING_R + RING_W / 2) / HyperionSiloLayout.RING_SEGMENTS * 1.06);

    /** Per silo on this client: the spin angle and each ring's height, so spin changes and the quench drop stay smooth. */
    private static final class RingMotion {
        float angle, lastTime = Float.NaN, dropStart = Float.NEGATIVE_INFINITY;
        float[] y = new float[0], from = new float[0];
        long seenQuench;
    }
    private final Map<HyperionControllerBlockEntity, RingMotion> motions = new WeakHashMap<>();
    private final HyperionQuadBatches<RenderType> batches = new HyperionQuadBatches<>();
    /** One layer of this frame's batches. */
    record Out(HyperionQuadBatches<RenderType> q, RenderType type) {
        void vertex(PoseStack.Pose p, float x, float y, float z, float u, float v, int argb, int light, float nx, float ny, float nz) {
            q.vertex(type, p, x, y, z, u, v, argb, light, nx, ny, nz);
        }
    }
    /** Flat colour lit by the world, textured metal lit by the world, flat glow, and the charge column's glow. */
    record Layers(Out flat, Out metal, Out glow, Out charge) {
        static Layers of(HyperionQuadBatches<RenderType> q) {
            return new Layers(new Out(q, RenderType.entityCutoutNoCull(WHITE)), new Out(q, RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)),
                    new Out(q, RenderType.entityTranslucentEmissive(WHITE)), new Out(q, RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS)));
        }
    }
    /** Layer offsets over a face, in multiples of HyperionSiloLayout.OVERLAY_STEP. */
    private static final float STEP = (float) HyperionSiloLayout.OVERLAY_STEP;

    public HyperionSiloRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(HyperionControllerBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var level = be.getLevel();
        if (level == null) return;
        var s = be.snapshot();
        var facing = be.getBlockState().getValue(HyperionControllerBlock.FACING);
        long now = level.getGameTime(); float time = now + partial;
        var g = HyperionSiloEffects.gauge(be, now);
        if (g == null || !facing.getAxis().isHorizontal()) return;
        int lit = LevelRenderer.getLightColor(level, be.getBlockPos().relative(facing));
        long seed = be.getBlockPos().asLong();
        var out = Layers.of(batches);
        pipes(pose, out, be, facing, g, time);

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(switch (facing) { case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0; }));
        pose.translate(-0.5, 0, -0.5);
        window(pose, out, g, s.height(), time, now, seed, lit);
        trims(pose, out, s.height(), lit);
        cap(pose, out, s.height(), LevelRenderer.getLightColor(level, be.getBlockPos().relative(facing.getOpposite()).above(s.height())));
        lamps(pose, out, g.lamps(), now, lit);
        rings(pose, out, motions.computeIfAbsent(be, k -> new RingMotion()), g, s.height(), s.lastQuenchTick(), time, lit);
        pose.popPose();
        batches.flush(buffers::getBuffer);
    }

    /** Per cooler, a 6 px pipe from its outward face to an elbow, then down to the floor (or a flange), frosted or bare,
     * with glowing dashes running in or out with the flow. Drawn in world-aligned space from the Controller's block. */
    private static void pipes(PoseStack pose, Layers out, HyperionControllerBlockEntity be, net.minecraft.core.Direction front, HyperionGauge.State g, float time) {
        var level = be.getLevel(); var origin = be.getBlockPos();
        var sprite = sprite(g.pipes() == Pipes.BARE ? PIPE_BARE : PIPE_FROST);
        var metal = out.metal(); var glow = out.glow();
        float half = HyperionSiloLayout.PIPE_PX / 2f * PX;
        float centreY = (float) HyperionSiloLayout.PIPE_Y, top = centreY + half;
        int dash = colour(g.colour() == Colour.AMBER || g.colour() == Colour.RED ? g.colour() : Colour.BLUE);
        for (var cooler : be.snapshot().coolers()) {
            var state = level.getBlockState(cooler);
            if (!(state.getBlock() instanceof CryocoolerBlock)) continue;
            var d = state.getValue(HyperionFacingPartBlock.FACING);
            if (!d.getAxis().isHorizontal()) continue;
            // A corner cooler's pipe runs shorter, so it stays inside the resting rings.
            float outer = 0.5f + HyperionSiloLayout.pipeOutPx(HyperionSiloEffects.lateralPx(origin, front, cooler, d)) * PX;
            float inner = Math.max(0.5f, outer - HyperionSiloLayout.PIPE_PX * PX);
            int air = HyperionSiloEffects.solidBelow(level, cooler, d);
            float drop = (float) HyperionSiloLayout.pipeDrop(air), bottom = centreY - drop;
            int lit = LevelRenderer.getLightColor(level, cooler.relative(d));
            pose.pushPose();
            pose.translate(cooler.getX() - origin.getX() + 0.5, cooler.getY() - origin.getY(), cooler.getZ() - origin.getZ() + 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(switch (d) { case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0; }));
            var p = pose.last();
            box(metal, p, -half, centreY - half, -outer, half, top, -0.5f, sprite, -1, lit);
            box(metal, p, -half, bottom, -outer, half, centreY - half, -inner, sprite, -1, lit);
            // A collar where the pipe meets the floor, or a flange where it stops short of one.
            box(metal, p, -half - PX, bottom, -outer - PX, half + PX, bottom + (air >= 0 && air <= HyperionSiloLayout.MAX_PIPE_AIR ? PX : 2 * PX), Math.min(-0.5f, -inner + PX), sprite, -1, lit);
            if (g.flow() != HyperionGauge.Flow.NONE) {
                // Path length s runs from the face (0) along the run (to the elbow) and down; dashes move 0.5 blocks a second.
                float run = outer - half - 0.5f, length = run + (centreY - bottom), spacing = 6 * PX;
                float shift = (time / 20 * 0.5f) % spacing; if (g.flow() == HyperionGauge.Flow.IN) shift = spacing - shift;
                for (float at = shift; at < length; at += spacing) {
                    float e = STEP;
                    if (at < run) { float z = -0.5f - at; box(glow, p, -half - e, centreY - half - e, z - PX, half + e, top + e, z + PX, null, dash, LightTexture.FULL_BRIGHT); }
                    else { float y = centreY - (at - run); box(glow, p, -half - e, y - PX, -outer - e, half + e, y + PX, -inner + e, null, dash, LightTexture.FULL_BRIGHT); }
                }
            }
            pose.popPose();
        }
    }
    /** An axis-aligned box in colour argb at light, textured with the sprite's first PIPE_PX columns, or flat without one. */
    private static void box(Out vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, TextureAtlasSprite sprite, int argb, int light) {
        float u0 = sprite == null ? 0 : sprite.getU0(), v0 = sprite == null ? 0 : sprite.getV0();
        float u1 = sprite == null ? 1 : sprite.getU0() + (sprite.getU1() - sprite.getU0()) * HyperionSiloLayout.PIPE_PX / 16f;
        float v1 = sprite == null ? 1 : sprite.getV1();
        // -z, +z, -x, +x, -y, +y
        vertex(vc, p, x0, y0, z0, u0, v1, argb, light, 0, 0, -1); vertex(vc, p, x0, y1, z0, u0, v0, argb, light, 0, 0, -1);
        vertex(vc, p, x1, y1, z0, u1, v0, argb, light, 0, 0, -1); vertex(vc, p, x1, y0, z0, u1, v1, argb, light, 0, 0, -1);
        vertex(vc, p, x1, y0, z1, u0, v1, argb, light, 0, 0, 1); vertex(vc, p, x1, y1, z1, u0, v0, argb, light, 0, 0, 1);
        vertex(vc, p, x0, y1, z1, u1, v0, argb, light, 0, 0, 1); vertex(vc, p, x0, y0, z1, u1, v1, argb, light, 0, 0, 1);
        vertex(vc, p, x0, y0, z1, u0, v1, argb, light, -1, 0, 0); vertex(vc, p, x0, y1, z1, u0, v0, argb, light, -1, 0, 0);
        vertex(vc, p, x0, y1, z0, u1, v0, argb, light, -1, 0, 0); vertex(vc, p, x0, y0, z0, u1, v1, argb, light, -1, 0, 0);
        vertex(vc, p, x1, y0, z0, u0, v1, argb, light, 1, 0, 0); vertex(vc, p, x1, y1, z0, u0, v0, argb, light, 1, 0, 0);
        vertex(vc, p, x1, y1, z1, u1, v0, argb, light, 1, 0, 0); vertex(vc, p, x1, y0, z1, u1, v1, argb, light, 1, 0, 0);
        vertex(vc, p, x0, y0, z0, u0, v1, argb, light, 0, -1, 0); vertex(vc, p, x1, y0, z0, u1, v1, argb, light, 0, -1, 0);
        vertex(vc, p, x1, y0, z1, u1, v0, argb, light, 0, -1, 0); vertex(vc, p, x0, y0, z1, u0, v0, argb, light, 0, -1, 0);
        vertex(vc, p, x0, y1, z0, u0, v1, argb, light, 0, 1, 0); vertex(vc, p, x0, y1, z1, u0, v0, argb, light, 0, 1, 0);
        vertex(vc, p, x1, y1, z1, u1, v0, argb, light, 0, 1, 0); vertex(vc, p, x1, y1, z0, u1, v1, argb, light, 0, 1, 0);
    }

    /** Moves each ring toward where the gauge wants it (or along its quench drop), then draws it with its own phase. */
    private static void rings(PoseStack pose, Layers out, RingMotion m, HyperionGauge.State g, int height, long lastQuench, float time, int lit) {
        int n = HyperionSiloLayout.ringCount(height);
        boolean floating = g.rings() == Rings.FLOATING;
        if (m.y.length != n) {
            // First sight (or a new height): start where the rings belong, so a reload never replays a drop.
            m.y = new float[n]; m.from = new float[n]; m.seenQuench = lastQuench; m.dropStart = Float.NEGATIVE_INFINITY;
            for (int k = 0; k < n; k++) m.y[k] = (float) (floating ? HyperionSiloLayout.ringFloatY(k, n, height) : HyperionSiloLayout.ringRestY(k));
        }
        float dt = Float.isNaN(m.lastTime) ? 0 : Math.max(0, Math.min(20, time - m.lastTime)); m.lastTime = time;
        m.angle = (m.angle + (float) g.spin() * 360 / 1200 * dt) % 360;
        if (lastQuench != m.seenQuench) { m.seenQuench = lastQuench; m.dropStart = time; System.arraycopy(m.y, 0, m.from, 0, n); }
        boolean dropping = time - m.dropStart < DROP_STAGGER * (n - 1) + DROP_TICKS;
        for (int k = 0; k < n; k++) {
            float rest = (float) HyperionSiloLayout.ringRestY(k), up = (float) HyperionSiloLayout.ringFloatY(k, n, height);
            if (dropping) {
                float f = (time - m.dropStart - DROP_STAGGER * (n - 1 - k)) / DROP_TICKS;
                m.y[k] = f <= 0 ? m.from[k] : f >= 1 ? rest : m.from[k] + (rest - m.from[k]) * f * f;
            } else {
                float step = (up - rest) / LIFT_TICKS * dt, target = floating ? up : rest;
                m.y[k] += Math.max(-step, Math.min(step, target - m.y[k]));
            }
        }

        var sprite = sprite(RING); var metal = out.metal(); var glow = out.glow();
        boolean live = g.colour() == Colour.BLUE || g.colour() == Colour.AMBER || g.colour() == Colour.RED;
        float flow = (float) ((g.spin() - HyperionGauge.IDLE_SPIN) / HyperionGauge.FLOW_SPIN);
        int edge = !live ? shade(RING_DIM, 0.35f) : shade(colour(g.colour()), floating ? 0.55f + 0.45f * flow : 0.3f);
        for (int k = 0; k < n; k++) {
            float phase = k * 1.9f, bob = 0, tiltX = 0, tiltZ = 0, jx = 0, jz = 0;
            boolean settled = !dropping && Math.abs(m.y[k] - (float) HyperionSiloLayout.ringFloatY(k, n, height)) < 1e-3f;
            if (floating && settled) {
                Motion motion = g.motion();
                bob = (motion == Motion.CALM ? 0.5f : 1) * PX * (float) Math.sin(2 * Math.PI * time / BOB_TICKS + phase);
                if (motion != Motion.CALM) { tiltX = 4 * (float) Math.sin(time * 0.13 + phase); tiltZ = 4 * (float) Math.sin(time * 0.17 + phase + 1); }
                if (motion == Motion.SHUDDER) { jx = PX * (float) Math.sin(time * 4.7 + phase); jz = PX * (float) Math.sin(time * 5.3 + 2 * phase); }
            }
            pose.pushPose();
            pose.translate(AXIS_X + jx, m.y[k] + bob, AXIS_Z + jz);
            pose.mulPose(Axis.XP.rotationDegrees(tiltX)); pose.mulPose(Axis.ZP.rotationDegrees(tiltZ));
            pose.mulPose(Axis.YP.rotationDegrees(m.angle + k * 5));
            ring(pose, metal, glow, sprite, edge, lit);
            pose.popPose();
        }
    }

    /** One ring about the current origin: dark segments with a 1 px glowing line round the inside of their faces; every
     * marker segment glows brighter and taller, so the turn shows. */
    private static void ring(PoseStack pose, Out metal, Out glow, TextureAtlasSprite sprite, int edge, int lit) {
        float x0 = -SEGMENT / 2, x1 = SEGMENT / 2, zIn = -RING_R, zOut = -RING_R - RING_W, uSpan = SEGMENT * 16;
        float u0 = sprite.getU0(), u1 = u0 + (sprite.getU1() - u0) * Math.min(1, uSpan / 16), dv = (sprite.getV1() - sprite.getV0()) / 16;
        float v = sprite.getV0();
        for (int j = 0; j < HyperionSiloLayout.RING_SEGMENTS; j++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(360f * j / HyperionSiloLayout.RING_SEGMENTS));
            var p = pose.last();
            // Outer face, top (the lit rows of the texture), bottom (the shadowed rows) and the inner face.
            vertex(metal, p, x0, 0, zOut, u0, v + 8 * dv, -1, lit, 0, 0, -1); vertex(metal, p, x0, RING_T, zOut, u0, v + 6 * dv, -1, lit, 0, 0, -1);
            vertex(metal, p, x1, RING_T, zOut, u1, v + 6 * dv, -1, lit, 0, 0, -1); vertex(metal, p, x1, 0, zOut, u1, v + 8 * dv, -1, lit, 0, 0, -1);
            vertex(metal, p, x0, RING_T, zOut, u0, v, -1, lit, 0, 1, 0); vertex(metal, p, x0, RING_T, zIn, u0, v + 2 * dv, -1, lit, 0, 1, 0);
            vertex(metal, p, x1, RING_T, zIn, u1, v + 2 * dv, -1, lit, 0, 1, 0); vertex(metal, p, x1, RING_T, zOut, u1, v, -1, lit, 0, 1, 0);
            vertex(metal, p, x0, 0, zIn, u0, v + 14 * dv, -1, lit, 0, -1, 0); vertex(metal, p, x0, 0, zOut, u0, v + 16 * dv, -1, lit, 0, -1, 0);
            vertex(metal, p, x1, 0, zOut, u1, v + 16 * dv, -1, lit, 0, -1, 0); vertex(metal, p, x1, 0, zIn, u1, v + 14 * dv, -1, lit, 0, -1, 0);
            vertex(metal, p, x0, 0, zIn, u0, v + 12 * dv, -1, lit, 0, 0, 1); vertex(metal, p, x1, 0, zIn, u1, v + 12 * dv, -1, lit, 0, 0, 1);
            vertex(metal, p, x1, RING_T, zIn, u1, v + 10 * dv, -1, lit, 0, 0, 1); vertex(metal, p, x0, RING_T, zIn, u0, v + 10 * dv, -1, lit, 0, 0, 1);
            int full = LightTexture.FULL_BRIGHT; float e = STEP, mid = RING_T / 2;
            boolean marker = HyperionSiloLayout.ringMarker(j); float band = marker ? RING_T / 2 : PX / 2; int c = marker ? lighten(edge) : edge;
            vertex(glow, p, x0, mid - band, zIn + e, 0, 1, c, full, 0, 0, 1); vertex(glow, p, x1, mid - band, zIn + e, 1, 1, c, full, 0, 0, 1);
            vertex(glow, p, x1, mid + band, zIn + e, 1, 0, c, full, 0, 0, 1); vertex(glow, p, x0, mid + band, zIn + e, 0, 0, c, full, 0, 0, 1);
            for (float y : new float[]{RING_T + e, -e}) {
                float ny = y > 0 ? 1 : -1;
                vertex(glow, p, x0, y, zIn - PX, 0, 0, c, full, 0, ny, 0); vertex(glow, p, x0, y, zIn, 0, 1, c, full, 0, ny, 0);
                vertex(glow, p, x1, y, zIn, 1, 1, c, full, 0, ny, 0); vertex(glow, p, x1, y, zIn - PX, 1, 0, c, full, 0, ny, 0);
            }
            pose.popPose();
        }
    }
    static void vertex(Out vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int argb, int light, float nx, float ny, float nz) {
        vc.vertex(p, x, y, z, u, v, argb, light, nx, ny, nz);
    }

    /** The dark window, the faint charge column up to the fill, then one bar per block: an empty slot, or a gradient
     * bar from dark to light across its width, filled from the left. */
    private static void window(PoseStack pose, Layers out, HyperionGauge.State g, int height, float time, long now, long seed, int lit) {
        var p = pose.last(); var flat = out.flat();
        float winTop = height - WIN_TOP_GAP;
        front(flat, p, WIN_X0, PLINTH, WIN_X1, winTop, STEP, BACKDROP, lit);
        double top = g.collapseShown() >= 0 ? 0 : Math.min(winTop, g.lit());
        if (top > PLINTH && g.colour() != Colour.OFF && g.colour() != Colour.DEAD) {
            var sprite = sprite(SWIRL); var glow = out.charge(); int faint = shade(0xFFFFFFFF, SWIRL_BRIGHT);
            for (int i = 0; i < height && i < top; i++) {
                float y0 = Math.max(i, PLINTH), y1 = (float) Math.min(i + 1, top);
                float v0 = sprite.getV1() - (y1 - i) * (sprite.getV1() - sprite.getV0()), v1 = sprite.getV1() - (y0 - i) * (sprite.getV1() - sprite.getV0());
                quad(glow, p, WIN_X0, y0, WIN_X1, y1, -2 * STEP, sprite.getU0(), v0, sprite.getU1(), v1, faint, LightTexture.FULL_BRIGHT);
            }
        }
        var glow = out.glow();
        for (int i = 0; i < g.bars(); i++) {
            float y0 = i + BAR_Y0, y1 = i + BAR_Y1;
            front(flat, p, BAR_X0, y0, BAR_X1, y1, 3 * STEP, g.colour() == Colour.DEAD ? DEAD : SLOT, lit);
            if (g.colour() == Colour.DEAD) { if (i == 0 && g.ember()) front(glow, p, BAR_X0, y0, BAR_X1, y1, 4 * STEP, shade(EMBER, 0.6f + 0.25f * (float) Math.sin(time * 0.08)), LightTexture.FULL_BRIGHT); continue; }
            if (g.colour() == Colour.OFF) continue;
            float fill, bright;
            if (g.collapseShown() >= 0) { fill = i < g.collapseShown() ? 1 : 0; bright = 1; }
            else {
                fill = (float) Math.max(0, Math.min(1, g.lit() - i));
                bright = switch (g.colour()) {
                    case AMBER -> blink(now, 16);
                    case RED -> blink(now, 6);
                    default -> 0.85f + 0.15f * (float) Math.sin(time * 0.05 + i * 0.9);
                };
                if (i == g.flickering()) bright *= 0.3f + 0.7f * noise(seed, now / 3 + i);
            }
            int lo = gradientLow(g.colour()), hi = colour(g.colour());
            if (fill > 0) gradient(glow, p, BAR_X0, y0, BAR_X0 + fill * (BAR_X1 - BAR_X0), y1, 4 * STEP, shade(lo, bright), shade(mix(lo, hi, fill), bright), LightTexture.FULL_BRIGHT);
            if (i == g.breathing()) {
                float k = 0.15f + 0.6f * (0.5f + 0.5f * (float) Math.sin(time * 2 * Math.PI / 20));
                gradient(glow, p, BAR_X0, y0, BAR_X1, y1, 5 * STEP, shade(lo, k), shade(hi, k), LightTexture.FULL_BRIGHT);
            }
        }
    }

    /** On each side face: the plinth band with a light ledge, a 1 px dark line down the two outer edges and along the
     * bottom, and a light line along the top edge and round the top face, so the silo has a base and a top. */
    private static void trims(PoseStack pose, Layers out, int height, int lit) {
        var flat = out.flat();
        for (int side = 0; side < 4; side++) {
            pose.pushPose(); onSide(pose, side); var p = pose.last();
            front(flat, p, -1, 0, 2, PLINTH, STEP, PLINTH_C, lit);
            front(flat, p, -1, PLINTH - PX, 2, PLINTH, 2 * STEP, EDGE_LIGHT, lit);
            front(flat, p, -1, 0, -1 + PX, height, 3 * STEP, TRIM, lit);
            front(flat, p, 2 - PX, 0, 2, height, 3 * STEP, TRIM, lit);
            front(flat, p, -1, 0, 2, PX, 3 * STEP, TRIM, lit);
            front(flat, p, -1 + PX, height - PX, 2 - PX, height, 2 * STEP, EDGE_LIGHT, lit);
            vertex(flat, p, -1, height + STEP, 0, 0, 0, EDGE_LIGHT, lit, 0, 1, 0); vertex(flat, p, -1, height + STEP, PX, 0, 1, EDGE_LIGHT, lit, 0, 1, 0);
            vertex(flat, p, 2, height + STEP, PX, 1, 1, EDGE_LIGHT, lit, 0, 1, 0); vertex(flat, p, 2, height + STEP, 0, 1, 0, EDGE_LIGHT, lit, 0, 1, 0);
            pose.popPose();
        }
    }

    /** A grey rim round the vent block's top edges, standing CAP_TALL proud of the roof. */
    private static void cap(PoseStack pose, Layers out, int height, int lit) {
        var p = pose.last(); var flat = out.flat(); float y0 = height, y1 = height + CAP_TALL;
        box(flat, p, 0, y0, 1, 1, y1, 1 + CAP, null, CAP_C, lit);
        box(flat, p, 0, y0, 2 - CAP, 1, y1, 2, null, CAP_C, lit);
        box(flat, p, 0, y0, 1 + CAP, CAP, y1, 2 - CAP, null, CAP_C, lit);
        box(flat, p, 1 - CAP, y0, 1 + CAP, 1, y1, 2 - CAP, null, CAP_C, lit);
    }

    /** Four 2 px lamps in the bottom row: on the two front corner blocks and on the middle of each side. */
    private static void lamps(PoseStack pose, Layers out, Lamps lamps, long now, int lit) {
        var glow = out.glow(); var flat = out.flat();
        float bright = switch (lamps) { case AMBER -> blink(now, 16); case RED -> blink(now, 6); default -> 1; };
        int colour = switch (lamps) { case BLUE -> colour(Colour.BLUE); case AMBER -> colour(Colour.AMBER); case RED -> colour(Colour.RED); case OFF -> LAMP_OFF; };
        float[][] spots = {{0, -0.5f}, {0, 1.5f}, {1, 0.5f}, {3, 0.5f}};
        for (var spot : spots) {
            pose.pushPose(); onSide(pose, (int) spot[0]); var p = pose.last();
            float x0 = spot[1] - PX, y0 = 12 * PX;
            if (lamps == Lamps.OFF) front(flat, p, x0, y0, x0 + 2 * PX, y0 + 2 * PX, STEP, colour, lit);
            else front(glow, p, x0, y0, x0 + 2 * PX, y0 + 2 * PX, STEP, shade(colour, bright), LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }

    /** Turns the pose so side k (0 front, 1 east, 2 back, 3 west, as model space faces north) lies on the front plane z = 0, spanning x -1..2. */
    static void onSide(PoseStack pose, int side) {
        if (side == 0) return;
        pose.translate(AXIS_X, 0, AXIS_Z);
        pose.mulPose(Axis.YP.rotationDegrees(-90 * side));
        pose.translate(-AXIS_X, 0, -AXIS_Z);
    }

    static TextureAtlasSprite sprite(ResourceLocation id) { return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id); }
    /** A flat rect on the front plane, out by `out` blocks. */
    static void front(Out vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float out, int argb, int light) {
        quad(vc, p, x0, y0, x1, y1, -out, 0, 0, 1, 1, argb, light);
    }
    /** A front rect shading from argbLeft at x0 to argbRight at x1. */
    static void gradient(Out vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float out, int argbLeft, int argbRight, int light) {
        vc.vertex(p, x0, y0, -out, 0, 1, argbLeft, light, 0, 0, -1); vc.vertex(p, x0, y1, -out, 0, 0, argbLeft, light, 0, 0, -1);
        vc.vertex(p, x1, y1, -out, 1, 0, argbRight, light, 0, 0, -1); vc.vertex(p, x1, y0, -out, 1, 1, argbRight, light, 0, 0, -1);
    }
    static void quad(Out vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z, float u0, float v0, float u1, float v1, int argb, int light) {
        vc.vertex(p, x0, y0, z, u0, v1, argb, light, 0, 0, -1); vc.vertex(p, x0, y1, z, u0, v0, argb, light, 0, 0, -1);
        vc.vertex(p, x1, y1, z, u1, v0, argb, light, 0, 0, -1); vc.vertex(p, x1, y0, z, u1, v1, argb, light, 0, 0, -1);
    }
    static int colour(Colour c) {
        return switch (c) { case BLUE -> 0xFF5FD0FF; case AMBER -> 0xFFFFB048; case RED -> 0xFFFF5A3C; case DEAD -> DEAD; case OFF -> SLOT; };
    }
    /** The dark end of a bar's gradient; colour(c) is the light end. */
    static int gradientLow(Colour c) {
        return switch (c) { case BLUE -> 0xFF1F6FD1; case AMBER -> 0xFFC46A12; case RED -> 0xFFA01C12; default -> colour(c); };
    }
    static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t), gr = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return a & 0xFF000000 | r << 16 | gr << 8 | bl;
    }
    /** A third of the way to white. */
    static int lighten(int argb) {
        return argb & 0xFF000000 | (((argb >> 16) & 255) * 2 + 255) / 3 << 16 | (((argb >> 8) & 255) * 2 + 255) / 3 << 8 | ((argb & 255) * 2 + 255) / 3;
    }
    static int shade(int argb, float k) {
        k = Math.max(0, Math.min(1, k));
        return argb & 0xFF000000 | Math.round(((argb >> 16) & 255) * k) << 16 | Math.round(((argb >> 8) & 255) * k) << 8 | Math.round((argb & 255) * k);
    }
    /** On for the first half of each period, dim for the second. */
    static float blink(long now, int period) { return now % period < period / 2 ? 1 : 0.3f; }
    /** A repeatable 0..1 value per silo and step. */
    static float noise(long seed, long step) {
        long h = seed * 0x9E3779B97F4A7C15L + step * 0xC2B2AE3D27D4EB4FL; h ^= h >>> 31; h *= 0x94D049BB133111EBL; h ^= h >>> 29;
        return (h & 0xFFFF) / 65535f;
    }

    @Override public AABB getRenderBoundingBox(HyperionControllerBlockEntity be) {
        var pos = be.getBlockPos(); var facing = be.getBlockState().getValue(HyperionControllerBlock.FACING); int height = be.snapshot().height();
        if (height <= 0 || !facing.getAxis().isHorizontal()) return new AABB(pos);
        var b = HyperionSiloLayout.renderBox(pos.getX(), pos.getY(), pos.getZ(), facing.getStepX(), facing.getStepZ(), height);
        return new AABB(b[0], b[1], b[2], b[3], b[4], b[5]);
    }
    @Override public boolean shouldRenderOffScreen(HyperionControllerBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 96; }
}
