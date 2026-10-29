package com.frozendawn.client;

import com.frozendawn.block.HalseyCoreBlock;
import com.frozendawn.block.HalseyCoreBlockEntity;
import com.frozendawn.block.IcarusInjectorBlockEntity;
import com.frozendawn.block.IcarusStabilizerBlockEntity;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.power.IcarusLook;
import com.frozendawn.power.IcarusPhysics.State;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
    private static final int PLASMA_HOT = 0xFFF4D8;
    /** The antimatter side of the plasma, and the two halves of each electron-positron pair. */
    private static final int ELECTRON = 0xFFB878, POSITRON = 0xB47CFF;
    private static final int SPARKS = 18, SPARK_LIFE = 7, PAIRS = 3, PAIR_LIFE = 28;
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

        // The plasma, all light and no surface: a blinding white heart; three counter-turning shells, clear at the middle and
        // bright at the rim, antimatter violet inside, the white annihilation front between, matter amber outside;
        // plasma-ball filaments crawling from heart to shell; two gyroscope rings, one matter and one antimatter; then a
        // corona, a lens streak and the flashes of single annihilations.
        if (plasmaRadius > 0.01f) {
            float r = plasmaRadius, k = (float) (0.4 + 0.6 * glow);
            int matter = IcarusLook.mix(coreRgb, PLASMA_HOT, 0.3), anti = IcarusLook.mix(POSITRON, WHITE, flicker), front = IcarusLook.mix(PLASMA_HOT, WHITE, 0.5);
            var lightBuf = buffers.getBuffer(IcarusDraw.light());
            IcarusDraw.sphere(lightBuf, pose, false, r * 0.3f, 12, 16, (lat, lon) -> IcarusDraw.argb(WHITE, 0.95));
            int filaments = 6, crackle = reduced ? 6 : 2;
            for (int i = 0; i < filaments; i++) {
                double lat = 1.1 * Math.sin(time * 0.021 + i * 1.3), lon = time * 0.017 * (i % 2 == 0 ? 1 : -1) + i * 1.05;
                long seed = (long) Mth.floor(time / crackle) * 31 + i + core.getBlockPos().asLong();
                IcarusDraw.bolt(lightBuf, pose, IcarusDraw.onSphere(r * 0.28, lat + 0.3, lon - 0.2), IcarusDraw.onSphere(r * 0.97, lat, lon), seed, 8, r * 0.07,
                        0.05f, IcarusDraw.argb(IcarusLook.mix(WHITE, anti, 0.45), 0.8 * k));
            }
            var shells = buffers.getBuffer(IcarusDraw.additive(IcarusDraw.PLASMA));
            float[] radii = {0.55f, 0.78f, 1f}, rims = {0.75f, 0.55f, 0.95f};
            int[] tints = {anti, front, matter};
            for (int sh = 0; sh < 3; sh++) {
                float sr = r * radii[sh], rimK = rims[sh]; int tint = tints[sh]; boolean frontShell = sh == 1;
                float spin = (float) ((sh % 2 == 0 ? 1 : -1) * (churn * (1 + 0.6 * sh) + time * 0.002 * (sh + 1)));
                IcarusDraw.texturedSphere(shells, pose, sr, 20, 32, 2 + sh, 1.5f, spin, (float) (time * 0.001 * (sh + 1)), (lat, lon) -> {
                    double rim = Math.pow(1 - facing(eye, sr, lat, lon), 2.2), lift = 1;
                    if (frontShell) { double f = domain(lat, lon, churn, time) / 0.25; lift = 0.3 + 1.2 * Math.exp(-f * f); }
                    return IcarusDraw.glow(tint, (0.08 + rimK * rim) * lift * k);
                });
            }
            var rings = buffers.getBuffer(IcarusDraw.additive(IcarusDraw.WHITE));
            for (int ring = 0; ring < 2; ring++) {
                int rgb = ring == 0 ? matter : anti; float radius = r * (1.22f + 0.1f * ring), half = 0.035f * r;
                poses.pushPose();
                poses.mulPose(Axis.YP.rotationDegrees(ring * 70));
                poses.mulPose(Axis.XP.rotationDegrees(ring == 0 ? 72 : -64));
                poses.mulPose(Axis.ZP.rotationDegrees(time * (ring == 0 ? 2.4f : -3.1f)));
                IcarusDraw.annulus(rings, poses.last(), radius - half, radius + half, 0, 72,
                        j -> IcarusDraw.glow(rgb, (0.3 + 0.7 * Math.pow(Math.max(0, Math.cos(j * Math.PI * 2 / 18)), 6)) * k));
                poses.popPose();
            }
            var halo = buffers.getBuffer(IcarusDraw.additive(IcarusDraw.GLOW));
            poses.pushPose();
            poses.mulPose(camera.rotation());
            IcarusDraw.billboard(halo, poses.last(), r * 1.5f, IcarusDraw.glow(IcarusLook.mix(front, WHITE, 0.4), 0.6 * glow + 0.3 * flicker));
            IcarusDraw.billboard(halo, poses.last(), r * 3.4f, IcarusDraw.glow(IcarusLook.mix(matter, POSITRON, 0.5), 0.25 * glow));
            IcarusDraw.streak(halo, poses.last(), r * 5.5f, r * 0.12f, IcarusDraw.glow(IcarusLook.mix(BEAM, WHITE, 0.5), (reduced ? 0.25 : 0.5) * glow));
            poses.popPose();
            int count = Math.round(SPARKS * (0.35f + 0.65f * glow) + 8 * (float) heat);
            sparks(halo, poses, camera, core, r * 0.85f, count, r * 0.15f, time, glow * (reduced ? 0.4f : 1f));
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
            // Where the matter stream touches the speck, single annihilations flicker around it.
            if (a.streamFront(pt) > 0.98f && a.streamTail(pt) < 0.5f)
                sparks(halo, poses, camera, core, pulse * 1.6f, 5, 0.16f, time, speck * (reduced ? 0.4f : 1f));
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

        // Gamma rays leaving the plasma, each ending in an electron-positron pair that curls away in opposite spirals, as
        // tracks do in a bubble chamber; the pair shrinks inward as it loses energy.
        if (plasmaRadius > 0.3f && glow > 0.05f) pairs(glowBuf, pose, core, plasmaRadius, time, glow);

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

    /** The matter and antimatter domains over the plasma, -1 to 1 and drifting: matter where positive, antimatter where
     * negative, the annihilation front along zero. */
    private static double domain(double lat, double lon, double churn, float time) {
        return Math.sin(3 * lon + churn * Math.PI * 2 + 1.7 * Math.sin(2 * lat + time * 0.013))
                * Math.cos(2 * lat - 0.8 * Math.sin(2 * lon - time * 0.009));
    }
    private static double hash01(int seed) { return (Mth.murmurHash3Mixer(seed) & 0xFFFFFF) / (double) 0x1000000; }

    /** Brief star-like flashes on a sphere of radius r, each living SPARK_LIFE ticks at a fresh place; staggered so a
     * steady handful burn at once. */
    private static void sparks(com.mojang.blaze3d.vertex.VertexConsumer c, PoseStack poses, net.minecraft.client.Camera camera,
                               HalseyCoreBlockEntity core, float r, int count, float size, float time, float strength) {
        int salt = core.getBlockPos().hashCode();
        for (int i = 0; i < count; i++) {
            float t = time + i * 2.37f;
            int gen = Mth.floor(t / SPARK_LIFE), seed = gen * 7919 + i * 104729 + salt;
            float age = t / SPARK_LIFE - gen;
            double lat = Math.asin(2 * hash01(seed) - 1), lon = 2 * Math.PI * hash01(seed + 1), depth = 0.92 + 0.14 * hash01(seed + 2);
            var at = IcarusDraw.onSphere(r * depth, lat, lon);
            int rgb = IcarusLook.mix(WHITE, hash01(seed + 3) < 0.5 ? ELECTRON : POSITRON, 0.35);
            poses.pushPose();
            poses.translate(at.x, at.y, at.z);
            poses.mulPose(camera.rotation());
            IcarusDraw.billboard(c, poses.last(), size * (0.5f + age), IcarusDraw.glow(rgb, (1 - age) * (1 - age) * strength));
            poses.popPose();
        }
    }

    /** PAIRS gamma rays at a time: each streaks out from the plasma, then splits into a warm electron track and a violet
     * positron track spiralling apart in a tilted plane, all fading over PAIR_LIFE ticks. */
    private static void pairs(com.mojang.blaze3d.vertex.VertexConsumer c, PoseStack.Pose pose, HalseyCoreBlockEntity core, float r, float time, float glow) {
        int salt = core.getBlockPos().hashCode() * 31;
        for (int j = 0; j < PAIRS; j++) {
            float t = time + j * (PAIR_LIFE / (float) PAIRS) + j * 3.1f;
            int gen = Mth.floor(t / PAIR_LIFE), seed = gen * 6151 + j * 92821 + salt;
            if (hash01(seed + 9) < 0.25) continue; // some beats stay dark, so the rhythm never settles
            float age = t / PAIR_LIFE - gen;
            var dir = IcarusDraw.onSphere(1, Math.asin(2 * hash01(seed) - 1), 2 * Math.PI * hash01(seed + 1));
            var start = dir.scale(r);
            var vertex = dir.scale(r + (SHIELD_RADIUS - r) * (0.3 + 0.3 * hash01(seed + 2)));
            double fade = (1 - age) * (1 - age) * glow;
            float shot = Math.min(1, age / 0.15f);
            IcarusDraw.beam(c, pose, start, start.add(vertex.subtract(start).scale(shot)), 0.03f,
                    IcarusDraw.argb(WHITE, 0.15 * fade), IcarusDraw.argb(BEAM, 0.6 * fade));
            if (age < 0.15f) continue;
            float grown = Math.min(1, (age - 0.15f) / 0.35f);
            var up = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            var n = dir.cross(up).normalize(); var b = dir.cross(n);
            double tilt = 2 * Math.PI * hash01(seed + 3);
            var plane = n.scale(Math.cos(tilt)).add(b.scale(Math.sin(tilt)));
            double r0 = 0.3 + 0.3 * hash01(seed + 4), turns = 4 * Math.PI * grown;
            int steps = Math.max(2, Math.round(32 * grown));
            for (int sign = -1; sign <= 1; sign += 2) {
                int rgb = sign > 0 ? ELECTRON : POSITRON;
                var centre = vertex.add(plane.scale(sign * r0));
                Vec3 last = vertex;
                for (int k = 1; k <= steps; k++) {
                    double th = turns * k / steps, rad = r0 * Math.exp(-0.2 * th);
                    var next = centre.add(plane.scale(-sign * rad * Math.cos(th))).add(dir.scale(rad * Math.sin(th)));
                    double along = 1 - 0.6 * k / (double) steps;
                    IcarusDraw.beam(c, pose, last, next, 0.035f, IcarusDraw.argb(rgb, 0.85 * fade * along), IcarusDraw.argb(rgb, 0.85 * fade * along));
                    last = next;
                }
            }
        }
    }

    /** How squarely the eye looks at a point on a sphere: 1 at the middle of the disc, 0 at its edge. */
    private static double facing(Vec3 eye, double radius, double lat, double lon) {
        double nx = Math.cos(lat) * Math.cos(lon), ny = Math.sin(lat), nz = Math.cos(lat) * Math.sin(lon);
        double vx = eye.x - nx * radius, vy = eye.y - ny * radius, vz = eye.z - nz * radius, len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        return len < 1e-6 ? 1 : Math.abs(nx * vx + ny * vy + nz * vz) / len;
    }

    @Override public AABB getRenderBoundingBox(HalseyCoreBlockEntity core) { return new AABB(core.getBlockPos()).inflate(IcarusStabilizerBlockEntity.CORE_DISTANCE + 1); }
    @Override public boolean shouldRenderOffScreen(HalseyCoreBlockEntity core) { return true; }
    @Override public int getViewDistance() { return 128; }
}
