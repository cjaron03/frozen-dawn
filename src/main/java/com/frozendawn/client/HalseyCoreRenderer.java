package com.frozendawn.client;

import com.frozendawn.block.HalseyCoreBlock;
import com.frozendawn.block.HalseyCoreBlockEntity;
import com.frozendawn.block.IcarusInjectorBlockEntity;
import com.frozendawn.block.IcarusStabilizerBlockEntity;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.power.IcarusLook;
import com.frozendawn.power.IcarusPhysics.State;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
    private static final int SPECK = 0xB45CFF, SPECK_HOT = 0xE8D0FF, STREAM = 0xFFB040, BEAM = 0x8CF0FF, WHITE = 0xFFFFFF;
    private static final int PLASMA_HOT = 0xFFF4D8, LIMB = 0x6A0C04;
    private static final ResourceLocation CORE_TEXTURE = ResourceLocation.fromNamespaceAndPath("frozendawn", "textures/block/halsey_core.png");
    private static final int CRACKS = 7, ARCS = 3;

    public HalseyCoreRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(HalseyCoreBlockEntity core, float pt, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var v = core.visual();
        if (v.state() == State.DETONATED || core.getLevel() == null) return;
        var a = IcarusCoreAnim.of(core);
        float time = core.getLevel().getGameTime() + pt;
        boolean reduced = FrozenDawnConfig.ICARUS_REDUCED_FLASH.get(), critical = IcarusLook.critical(v.coreC(), v.alarm());
        double heat = IcarusCoreAnim.heat(v.coreC());
        float glow = a.glow(pt), plasmaRadius = a.radius(pt);
        // A white flicker when critical: a hard strobe every few ticks, or with reduced flash a slow gentle swell.
        double flicker = !critical ? 0 : reduced ? 0.12 + 0.08 * Math.sin(time * 0.15)
                : (Mth.murmurHash3Mixer((int) (time / 2) * 31 + core.getBlockPos().hashCode()) & 3) == 0 ? 0.55 : 0.1;
        int coreRgb = IcarusLook.mix(IcarusLook.coreColor(Math.max(v.coreC(), 1500), v.alarm()), WHITE, flicker);
        plasmaRadius *= 1 + (float) (0.03 * heat * Math.sin(time * (0.25 + 0.5 * heat)));
        double churn = a.churn(pt) / 360.0;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var eye = camera.getPosition().subtract(Vec3.atCenterOf(core.getBlockPos()));

        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        var pose = poses.last();

        // The core block's stand-in while HIDDEN: it shrinks into violet light at injection and condenses back at the end.
        float solid = a.solid(pt);
        if (solid > 0.01f && core.getBlockState().getValue(HalseyCoreBlock.HIDDEN)) {
            float melt = 1 - solid;
            IcarusDraw.cube(buffers.getBuffer(IcarusDraw.surface(CORE_TEXTURE)), pose, 0.5f * (0.15f + 0.85f * solid),
                    IcarusDraw.argb(IcarusLook.mix(WHITE, SPECK_HOT, melt), solid * solid));
            IcarusDraw.cube(buffers.getBuffer(IcarusDraw.additive(CORE_TEXTURE)), pose, 0.5f * (0.15f + 0.85f * solid) + 0.01f,
                    IcarusDraw.glow(SPECK, 4 * melt * solid));
        }

        // The plasma: a granulated surface, hot white at the centre of the disc and deep red at the limb, a second layer
        // drifting the other way, and a corona.
        if (plasmaRadius > 0.01f) {
            float r = plasmaRadius;
            int hot = IcarusLook.mix(coreRgb, PLASMA_HOT, 0.45 + 0.35 * glow), limb = IcarusLook.mix(scale(coreRgb, 0.55), LIMB, 0.35);
            double bright = 0.35 + 0.65 * glow;
            IcarusDraw.texturedSphere(buffers.getBuffer(IcarusDraw.surface(IcarusDraw.PLASMA)), pose, r, 20, 32, 3, 2, (float) churn, (float) (time * 0.0015),
                    (lat, lon) -> {
                        double mu = facing(eye, r, lat, lon);
                        return IcarusDraw.argb(scale(IcarusLook.mix(limb, hot, Math.pow(mu, 0.8)), bright), 0.97);
                    });
            IcarusDraw.texturedSphere(buffers.getBuffer(IcarusDraw.additive(IcarusDraw.PLASMA)), pose, r * 1.004f, 20, 32, 2, 1.5f, (float) (-churn * 1.3), (float) (-time * 0.002),
                    (lat, lon) -> IcarusDraw.glow(hot, (0.15 + 0.3 * Math.pow(facing(eye, r, lat, lon), 2)) * glow));
            var halo = buffers.getBuffer(IcarusDraw.additive(IcarusDraw.GLOW));
            poses.pushPose();
            poses.mulPose(camera.rotation());
            IcarusDraw.billboard(halo, poses.last(), r * 1.9f, IcarusDraw.glow(coreRgb, 0.55 * glow + 0.3 * flicker));
            IcarusDraw.billboard(halo, poses.last(), r * 3.6f, IcarusDraw.glow(coreRgb, 0.22 * glow));
            poses.popPose();
        }

        // The antimatter speck, a violet point with a soft halo, whenever antimatter sits in the core without plasma.
        float speck = a.speck(pt);
        if (speck > 0.01f) {
            float pulse = IcarusCoreAnim.SPECK_RADIUS * speck * (1 + 0.25f * Mth.sin(time * 0.4f));
            IcarusDraw.sphere(buffers.getBuffer(IcarusDraw.light()), pose, false, pulse, 8, 10, (lat, lon) -> IcarusDraw.argb(SPECK_HOT, 0.95));
            poses.pushPose();
            poses.mulPose(camera.rotation());
            var halo = buffers.getBuffer(IcarusDraw.additive(IcarusDraw.GLOW));
            IcarusDraw.billboard(halo, poses.last(), pulse * 6, IcarusDraw.glow(SPECK, 0.9 * speck));
            IcarusDraw.billboard(halo, poses.last(), pulse * 14, IcarusDraw.glow(SPECK, 0.3 * speck));
            poses.popPose();
        }
        float flash = a.flash(pt) * (reduced ? 0.35f : 0.9f);
        if (flash > 0.01f) {
            poses.pushPose();
            poses.mulPose(camera.rotation());
            IcarusDraw.billboard(buffers.getBuffer(IcarusDraw.additive(IcarusDraw.GLOW)), poses.last(), 2 + 12 * (1 - a.flash(pt)), IcarusDraw.glow(WHITE, flash));
            poses.popPose();
        }

        // The shield: a hexagon lattice that grows from the four beam strikes, bright at its rim where the eye looks
        // through the most of it, rippling out from each strike, with a glowing front while it spreads or withdraws.
        double shieldAlpha = a.shield(pt) * (critical && !reduced ? 0.75 + 0.25 * Math.sin(time * 1.7) : 1);
        int shieldRgb = IcarusLook.shieldColor(v.coreC(), v.shieldPct(), v.alarm());
        float[] spread = new float[IcarusLook.STABILIZERS];
        float anySpread = 0;
        for (int i = 0; i < spread.length; i++) { spread[i] = a.spread(i, pt); anySpread = Math.max(anySpread, spread[i]); }
        if (shieldAlpha > 0.003 && anySpread > 0.003f) {
            float drift = (float) (time * 0.0006);
            IcarusDraw.Shade lattice = (lat, lon) -> {
                double nx = Math.cos(lat) * Math.cos(lon), ny = Math.sin(lat), nz = Math.cos(lat) * Math.sin(lon);
                double reveal = 0, ripple = 0;
                for (int i = 0; i < spread.length; i++) {
                    if (spread[i] <= 0) continue;
                    var dir = Direction.from2DDataValue(i);
                    double d = Math.acos(Math.clamp(nx * dir.getStepX() + nz * dir.getStepZ(), -1, 1));
                    reveal = Math.max(reveal, Math.clamp((spread[i] * 2.3 - d) / 0.35, 0, 1));
                    ripple += spread[i] * Math.pow(Math.max(0, Math.sin(d * 7 - time * 0.22 + i)), 12) * Math.exp(-d * 0.9);
                }
                if (reveal <= 0) return 0;
                double rim = Math.pow(1 - facing(eye, SHIELD_RADIUS, lat, lon), 3), front = 4 * reveal * (1 - reveal);
                return IcarusDraw.argb(shieldRgb, Math.min(1, shieldAlpha * (0.45 + 2.4 * rim + 1.6 * ripple + 2 * front) * reveal));
            };
            // Additive only: the shield is light, so it brightens what lies behind it and never hides the injector or frame.
            IcarusDraw.texturedSphere(buffers.getBuffer(IcarusDraw.additive(IcarusDraw.SHIELD)), pose, SHIELD_RADIUS, 24, 40, 4, 2, drift, 0,
                    (lat, lon) -> { int c = lattice.argb(lat, lon); return IcarusDraw.glow(c, (c >>> 24) / 255.0); });
        }

        var glowBuf = buffers.getBuffer(IcarusDraw.light());
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
        // A flare where each beam strikes the shield.
        var flares = buffers.getBuffer(IcarusDraw.additive(IcarusDraw.GLOW));
        for (int i = 0; i < IcarusLook.STABILIZERS; i++) {
            float reach = a.beam(i, pt);
            if (reach < 0.98f) continue;
            var dir = Direction.from2DDataValue(i);
            poses.pushPose();
            poses.translate(dir.getStepX() * SHIELD_RADIUS, 0, dir.getStepZ() * SHIELD_RADIUS);
            poses.mulPose(camera.rotation());
            IcarusDraw.billboard(flares, poses.last(), 0.8f + 0.15f * Mth.sin(time * 0.7f + i * 1.9f), IcarusDraw.glow(IcarusLook.mix(BEAM, shieldRgb, 0.5), 0.7));
            poses.popPose();
        }
        glowBuf = buffers.getBuffer(IcarusDraw.light());

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

    /** How squarely the eye looks at a point on a sphere: 1 at the middle of the disc, 0 at its edge. */
    private static double facing(Vec3 eye, double radius, double lat, double lon) {
        double nx = Math.cos(lat) * Math.cos(lon), ny = Math.sin(lat), nz = Math.cos(lat) * Math.sin(lon);
        double vx = eye.x - nx * radius, vy = eye.y - ny * radius, vz = eye.z - nz * radius, len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        return len < 1e-6 ? 1 : Math.abs(nx * vx + ny * vy + nz * vz) / len;
    }

    private static int scale(int rgb, double k) {
        int r = (int) Math.min(255, (rgb >> 16 & 0xFF) * k), g = (int) Math.min(255, (rgb >> 8 & 0xFF) * k), b = (int) Math.min(255, (rgb & 0xFF) * k);
        return r << 16 | g << 8 | b;
    }

    @Override public AABB getRenderBoundingBox(HalseyCoreBlockEntity core) { return new AABB(core.getBlockPos()).inflate(IcarusStabilizerBlockEntity.CORE_DISTANCE + 1); }
    @Override public boolean shouldRenderOffScreen(HalseyCoreBlockEntity core) { return true; }
    @Override public int getViewDistance() { return 128; }
}
