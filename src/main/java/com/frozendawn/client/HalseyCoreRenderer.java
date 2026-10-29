package com.frozendawn.client;

import com.frozendawn.block.HalseyCoreBlockEntity;
import com.frozendawn.block.IcarusInjectorBlockEntity;
import com.frozendawn.block.IcarusStabilizerBlockEntity;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.power.IcarusLook;
import com.frozendawn.power.IcarusPhysics.State;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The Halsey Core sphere, its shield, the four stabilizer beams, the matter stream from the injector, and in a crisis the
 * shield's cracks and the arcs from core to shield. Everything follows the synced Visual through IcarusLook and the eased
 * IcarusCoreAnim; nothing here decides state. */
public final class HalseyCoreRenderer implements BlockEntityRenderer<HalseyCoreBlockEntity> {
    private static final float SHIELD_RADIUS = 4.5f, BEAM_START = IcarusStabilizerBlockEntity.CORE_DISTANCE - 0.5f;
    private static final float NOZZLE_Y = -IcarusInjectorBlockEntity.CORE_DISTANCE + 0.5f;
    private static final int DARK = 0x16121A, SPECK = 0xB45CFF, STREAM = 0xFFB040, BEAM = 0x8CF0FF, WHITE = 0xFFFFFF;
    private static final int CRACKS = 7, ARCS = 3;

    public HalseyCoreRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(HalseyCoreBlockEntity core, float pt, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var v = core.visual();
        if (v.state() == State.DETONATED || core.getLevel() == null) return;
        var a = IcarusCoreAnim.of(core);
        float time = core.getLevel().getGameTime() + pt;
        boolean reduced = FrozenDawnConfig.ICARUS_REDUCED_FLASH.get(), critical = IcarusLook.critical(v.coreC(), v.alarm());
        double heat = IcarusCoreAnim.heat(v.coreC());
        float glow = a.glow(pt), bloom = a.bloom(pt);
        // A white flicker when critical: a hard strobe every few ticks, or with reduced flash a slow gentle swell.
        double flicker = !critical ? 0 : reduced ? 0.12 + 0.08 * Math.sin(time * 0.15)
                : (Mth.murmurHash3Mixer((int) (time / 2) * 31 + core.getBlockPos().hashCode()) & 3) == 0 ? 0.55 : 0.1;
        int coreRgb = IcarusLook.mix(IcarusLook.mix(DARK, IcarusLook.coreColor(v.coreC(), v.alarm()), glow), WHITE, flicker);
        float plasmaRadius = glow > 0.01f ? IcarusCoreAnim.SPECK_RADIUS + (IcarusCoreAnim.CORE_RADIUS - IcarusCoreAnim.SPECK_RADIUS) * (bloom * bloom * (3 - 2 * bloom)) : 0;
        plasmaRadius *= 1 + (float) (0.03 * heat * Math.sin(time * (0.25 + 0.5 * heat)));
        double churn = Math.toRadians(a.churn(pt));

        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        var pose = poses.last();

        var body = buffers.getBuffer(IcarusDraw.body());
        // The cold core: a dark, still shell that the plasma replaces as it brightens and returns to as it dims.
        double shellAlpha = 0.5 * (1 - Math.min(1, glow * 1.5));
        if (shellAlpha > 0.01) IcarusDraw.sphere(body, pose, true, IcarusCoreAnim.CORE_RADIUS, 16, 24, (lat, lon) -> IcarusDraw.argb(DARK, shellAlpha));
        if (plasmaRadius > 0) {
            double alpha = Math.min(0.95, 0.3 + glow);
            IcarusDraw.sphere(body, pose, true, plasmaRadius, 16, 24, (lat, lon) -> {
                double swirl = 0.78 + 0.22 * Math.sin(lat * 5 + lon * 3 + churn) * Math.cos(lat * 3 - lon * 2 + churn * 0.7);
                return IcarusDraw.argb(scale(coreRgb, swirl), alpha);
            });
        }
        double shieldAlpha = IcarusLook.shieldAlpha(v.state(), v.shieldPct()) * (critical && !reduced ? 0.75 + 0.25 * Math.sin(time * 1.7) : 1);
        int shieldRgb = IcarusLook.shieldColor(v.coreC(), v.shieldPct(), v.alarm());
        double drift = Math.toRadians(time * 0.4);
        IcarusDraw.sphere(body, pose, true, SHIELD_RADIUS, 20, 32, (lat, lon) ->
                IcarusDraw.argb(shieldRgb, shieldAlpha * (0.7 + 0.3 * Math.abs(Math.sin(lat * 9) * Math.sin(lon * 9 + drift)))));

        var glowBuf = buffers.getBuffer(IcarusDraw.light());
        if (plasmaRadius > 0) {
            double halo = 0.16 * glow + 0.25 * flicker;
            IcarusDraw.sphere(glowBuf, pose, false, plasmaRadius * 1.12f, 12, 18, (lat, lon) -> IcarusDraw.argb(coreRgb, halo));
        }
        // The antimatter speck, from injection until the bloom swallows it.
        boolean speck = switch (v.state()) { case INJECTION, PRIMING -> true; case IGNITION -> bloom < 0.4f; default -> false; };
        if (speck) {
            float pulse = IcarusCoreAnim.SPECK_RADIUS * (1 + 0.25f * Mth.sin(time * 0.4f)) * (v.state() == State.INJECTION ? Math.min(1, a.stateTicks / 40f) : 1);
            IcarusDraw.sphere(glowBuf, pose, false, pulse, 6, 8, (lat, lon) -> IcarusDraw.argb(SPECK, 0.9));
            IcarusDraw.sphere(glowBuf, pose, false, pulse * 2.4f, 6, 8, (lat, lon) -> IcarusDraw.argb(SPECK, 0.25));
        }
        float flash = a.flash(pt) * (reduced ? 0.35f : 0.9f);
        if (flash > 0.01f) IcarusDraw.sphere(glowBuf, pose, false, 1 + 5 * (1 - a.flash(pt)), 10, 14, (lat, lon) -> IcarusDraw.argb(WHITE, flash));

        for (int i = 0; i < IcarusLook.STABILIZERS; i++) {
            float reach = a.beam(i, pt);
            if (reach < 0.002f) continue;
            var dir = Direction.from2DDataValue(i);
            var from = new Vec3(dir.getStepX(), 0, dir.getStepZ()).scale(BEAM_START);
            var to = new Vec3(dir.getStepX(), 0, dir.getStepZ()).scale(BEAM_START - (BEAM_START - SHIELD_RADIUS) * reach);
            int beamRgb = IcarusLook.mix(BEAM, shieldRgb, 0.5);
            float width = 0.16f + 0.04f * Mth.sin(time * 0.5f + i);
            IcarusDraw.beam(glowBuf, pose, from, to, width, IcarusDraw.argb(beamRgb, 0.8), IcarusDraw.argb(beamRgb, 0.55));
            IcarusDraw.beam(glowBuf, pose, from, to, width * 2.5f, IcarusDraw.argb(beamRgb, 0.15), IcarusDraw.argb(beamRgb, 0.1));
        }

        // The matter stream climbs from the nozzle to the speck or the plasma's underside; a CLIP lifts its tail away.
        float front = a.streamFront(pt), tail = a.streamTail(pt);
        if (front > 0.001f && tail < 0.999f) {
            float top = plasmaRadius > IcarusCoreAnim.SPECK_RADIUS * 2 ? -plasmaRadius * 0.9f : 0;
            float y0 = NOZZLE_Y + (top - NOZZLE_Y) * tail, y1 = NOZZLE_Y + (top - NOZZLE_Y) * front;
            int pieces = 12;
            for (int k = 0; k < pieces; k++) {
                float s0 = y0 + (y1 - y0) * k / pieces, s1 = y0 + (y1 - y0) * (k + 1) / pieces;
                double band = 0.55 + 0.45 * Math.sin((s0 - time * 0.35) * 4);
                IcarusDraw.beam(glowBuf, pose, new Vec3(0, s0, 0), new Vec3(0, s1, 0), 0.2f, IcarusDraw.argb(STREAM, 0.7 * band), IcarusDraw.argb(STREAM, 0.7 * band));
            }
        }

        if (critical && plasmaRadius > 0) {
            float strength = reduced ? 0.45f : 0.9f;
            long seed = core.getBlockPos().asLong();
            // Cracks stay where they opened; the arcs jump every few ticks.
            for (int k = 0; k < CRACKS; k++) {
                var random = RandomSource.create(seed * 31 + k);
                double lat = (random.nextDouble() - 0.5) * 2.4, lon = random.nextDouble() * Math.PI * 2;
                var last = IcarusDraw.onSphere(SHIELD_RADIUS + 0.02, lat, lon);
                for (int s = 0; s < 5; s++) {
                    lat += random.nextGaussian() * 0.12; lon += random.nextGaussian() * 0.12;
                    var next = IcarusDraw.onSphere(SHIELD_RADIUS + 0.02, lat, lon);
                    IcarusDraw.beam(glowBuf, pose, last, next, 0.06f, IcarusDraw.argb(0xFF6A50, strength * 0.8), IcarusDraw.argb(0xFFE0C0, strength * 0.6));
                    last = next;
                }
            }
            long hop = (long) (time / 3);
            for (int k = 0; k < ARCS; k++) {
                var random = RandomSource.create(seed ^ (hop * 977 + k));
                double lat = (random.nextDouble() - 0.5) * 2.2, lon = random.nextDouble() * Math.PI * 2;
                IcarusDraw.bolt(glowBuf, pose, IcarusDraw.onSphere(plasmaRadius, lat, lon), IcarusDraw.onSphere(SHIELD_RADIUS, lat + random.nextGaussian() * 0.2, lon + random.nextGaussian() * 0.2),
                        hop * 13 + k, 6, 0.25, 0.07f, IcarusDraw.argb(0xFFD8D0, strength));
            }
        }
        poses.popPose();
    }

    private static int scale(int rgb, double k) {
        int r = (int) Math.min(255, (rgb >> 16 & 0xFF) * k), g = (int) Math.min(255, (rgb >> 8 & 0xFF) * k), b = (int) Math.min(255, (rgb & 0xFF) * k);
        return r << 16 | g << 8 | b;
    }

    @Override public AABB getRenderBoundingBox(HalseyCoreBlockEntity core) { return new AABB(core.getBlockPos()).inflate(IcarusStabilizerBlockEntity.CORE_DISTANCE + 1); }
    @Override public boolean shouldRenderOffScreen(HalseyCoreBlockEntity core) { return true; }
    @Override public int getViewDistance() { return 128; }
}
