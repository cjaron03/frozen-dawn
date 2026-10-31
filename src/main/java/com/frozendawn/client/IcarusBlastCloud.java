package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.power.IcarusBlast;
import com.frozendawn.power.IcarusLook;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

/** The shapes of an Icarus detonation, drawn over IcarusBlastEffects' particles and timed by its clock. A
 * violet-white fireball that rolls up into a mushroom cloud (a churning cap and crown on a rising stem, a base surge
 * spreading over the ground), lit orange from within and cooling to grey over two minutes, with lightning crawling through
 * it for the first half minute; a pale shock shell racing out at the speed of sound and a milky condensation dome
 * billowing behind it. At the owner's request all of that shows in vacuum too, with a plasma sphere swelling from violet to
 * orange around it. Everywhere: the flash lights the world itself for a moment,
 * and blocks torn from the crater tumble out and rain down. All of it cosmetic: the crater is carved on the server. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class IcarusBlastCloud {
    /** Ticks the cloud hangs, the last FADE_TICKS of them thinning away; the fireball's glow and the lightning's span. */
    static final int CLOUD_TICKS = 2400, FADE_TICKS = 600, LIGHTNING_TICKS = 600, PLASMA_TICKS = 200, LIGHT_TICKS = 70, CONDENSATION_TICKS = 120;
    /** The condensation shell is drawn out to this many blast radii. */
    private static final double DOME_REACH = 2.5;
    private static final int DEBRIS_COUNT = 90, DEBRIS_LIFE = 400;
    private static final double GRAVITY = 0.06;
    private static final int SMOKE = 0x5E5852, SMOKE_PALE = 0x9C958C, FIRE = 0xFF7022, EMBER = 0xFFB060, ANTI = 0xD8B8FF, SHOCK = 0xEEF2FF,
            BOLT = 0xE6D6FF;
    private static final List<Debris> DEBRIS = new ArrayList<>();
    private static boolean visible;

    private static final class Debris {
        final BlockState state; final Vector3f axis; final float spin, scale;
        Vec3 pos, posO, vel; float angle, angleO; int age;
        Debris(BlockState state, Vec3 pos, Vec3 vel, Vector3f axis, float spin, float scale) {
            this.state = state; this.pos = this.posO = pos; this.vel = vel; this.axis = axis; this.spin = spin; this.scale = scale;
        }
    }

    private IcarusBlastCloud() {}

    /** A new blast: the cloud shows unless it is too far to see, and the crater's surface blocks are flung out. */
    static void start(Minecraft mc, boolean inView) {
        visible = inView;
        DEBRIS.clear();
        if (!visible || mc.level == null) return;
        var center = IcarusBlastEffects.center(); double radius = IcarusBlastEffects.radius();
        var random = RandomSource.create(Double.doubleToLongBits(center.x) * 31 + Double.doubleToLongBits(center.z));
        for (int i = 0; i < DEBRIS_COUNT; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * radius * 0.5;
            int x = Mth.floor(center.x + Math.cos(a) * d), z = Mth.floor(center.z + Math.sin(a) * d);
            BlockState state = Blocks.STONE.defaultBlockState();
            double y = center.y;
            if (mc.level.hasChunk(x >> 4, z >> 4)) {
                y = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                var found = mc.level.getBlockState(BlockPos.containing(x, y - 1, z));
                if (!found.isAir() && found.getRenderShape() == RenderShape.MODEL) state = found;
            }
            // Thrown to land 0.6 to 1.6 radii out from the centre, higher and slower or flatter and faster.
            double up = 1.2 + 2.4 * random.nextDouble(), flight = 2 * up / GRAVITY, land = radius * (0.6 + random.nextDouble());
            double out = (land - d) / flight;
            DEBRIS.add(new Debris(state, new Vec3(x + 0.5, y + 1, z + 0.5), new Vec3(Math.cos(a) * out, up, Math.sin(a) * out),
                    new Vector3f((float) random.nextGaussian(), (float) random.nextGaussian(), (float) random.nextGaussian()).normalize(),
                    (float) (4 + 14 * random.nextDouble()), (float) (1.5 + 2.5 * random.nextDouble())));
        }
    }

    static void clear() { DEBRIS.clear(); visible = false; }

    static void tick(Minecraft mc, int ticks) {
        var level = mc.level;
        for (var it = DEBRIS.iterator(); it.hasNext(); ) {
            var d = it.next();
            d.posO = d.pos; d.angleO = d.angle;
            d.vel = d.vel.add(0, -GRAVITY, 0);
            d.pos = d.pos.add(d.vel); d.angle += d.spin; d.age++;
            if (d.age % 2 == 0) add(level, d.age < 30 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, d.pos, Vec3.ZERO);
            int x = Mth.floor(d.pos.x), z = Mth.floor(d.pos.z);
            boolean landed = d.vel.y < 0 && level.hasChunk(x >> 4, z >> 4) && d.pos.y <= level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 0.5;
            if (landed) {
                for (int i = 0; i < 12; i++)
                    add(level, new BlockParticleOption(ParticleTypes.BLOCK, d.state), d.pos.add(level.random.nextGaussian() * d.scale * 0.4, 0.3, level.random.nextGaussian() * d.scale * 0.4),
                            new Vec3(level.random.nextGaussian() * 0.2, 0.3, level.random.nextGaussian() * 0.2));
                add(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, d.pos, new Vec3(0, 0.05, 0));
            }
            if (landed || d.age > DEBRIS_LIFE) it.remove();
        }
        // Each new lightning stroke in the cloud thunders, heard as late as the boom from the same place.
        if (visible && ticks < LIGHTNING_TICKS && ticks % 3 == 0 && strikes(ticks / 3))
            IcarusBlastEffects.scheduleFromBlast(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7f, 0.55f + 0.3f * level.random.nextFloat());
    }

    private static void add(net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.core.particles.ParticleOptions kind, Vec3 at, Vec3 motion) {
        level.addAlwaysVisibleParticle(kind, true, at.x, at.y, at.z, motion.x, motion.y, motion.z);
    }

    /** Whether lightning strikes in this three-tick slot: often at first, rarely after half a minute. */
    private static boolean strikes(int slot) {
        return IcarusFx.hash01(slot * 7919 + 13) < 0.6 * Math.exp(-slot * 3 / 250.0);
    }

    /** How strongly the flash lights the world, 0 to 1: everyone near enough to see it, whether or not they look. */
    public static float worldLight(float pt) {
        int ticks = IcarusBlastEffects.ticks();
        if (ticks >= LIGHT_TICKS) return 0;
        float t = ticks + pt, k = (float) Math.exp(-t / 14) * IcarusBlastEffects.baseFlash();
        return Math.min(1, k) * (IcarusBlastEffects.reduced() ? 0.35f : 0.85f);
    }

    /** The light's colour, rgb: violet-white in the first instant of annihilation, then plain white. */
    public static int lightColor() { return IcarusLook.mix(ANTI, 0xFFF8F0, Math.min(1, IcarusBlastEffects.ticks() / 8f)); }

    /** The sky and fog wash toward the flash's colour as it lights the world. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fog(ViewportEvent.ComputeFogColor event) {
        float k = worldLight((float) event.getPartialTick());
        if (k <= 0.003f) return;
        int c = lightColor();
        event.setRed(Mth.lerp(k, event.getRed(), (c >> 16 & 0xFF) / 255f));
        event.setGreen(Mth.lerp(k, event.getGreen(), (c >> 8 & 0xFF) / 255f));
        event.setBlue(Mth.lerp(k, event.getBlue(), (c & 0xFF) / 255f));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        int ticks = IcarusBlastEffects.ticks();
        if (mc.level == null || (DEBRIS.isEmpty() && (!visible || ticks >= CLOUD_TICKS))) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false), t = ticks + pt;
        var cam = event.getCamera().getPosition();
        var center = IcarusBlastEffects.center(); float radius = (float) IcarusBlastEffects.radius();
        boolean reduced = IcarusBlastEffects.reduced();
        var poses = event.getPoseStack(); var buffers = mc.renderBuffers().bufferSource();

        if (visible && ticks < CLOUD_TICKS) {
            // The cloud stands hundreds of blocks off, past where distance fog would swallow it whole: it is drawn clear of
            // fog, then the fog is put back for everything else.
            float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
            var fogShape = RenderSystem.getShaderFogShape();
            FogRenderer.setupNoFog();
            poses.pushPose();
            poses.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);
            var rel = center.subtract(cam);
            // At the owner's request the cloud and the shock show in vacuum too, with the plasma sphere besides.
            if (IcarusBlastEffects.vacuum()) plasma(buffers, poses, radius, t, reduced);
            dome(buffers, poses, rel, radius, t, reduced);
            condensation(buffers, poses, rel, radius, t, reduced);
            mushroom(buffers, poses, radius, t, reduced);
            poses.popPose();
            buffers.endBatch();
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
        }

        for (var d : DEBRIS) {
            var at = d.posO.lerp(d.pos, pt);
            poses.pushPose();
            poses.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            poses.mulPose(Axis.of(d.axis).rotationDegrees(Mth.lerp(pt, d.angleO, d.angle)));
            poses.scale(d.scale, d.scale, d.scale);
            poses.translate(-0.5, -0.5, -0.5);
            int light = d.age < 40 ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
            mc.getBlockRenderer().renderSingleBlock(d.state, poses, buffers, light, OverlayTexture.NO_OVERLAY);
            poses.popPose();
        }
        buffers.endBatch();
    }

    /** The pale condensation shell: rim-bright, racing out at the speed of sound, gone by DOME_REACH radii. */
    private static void dome(net.minecraft.client.renderer.MultiBufferSource buffers, PoseStack poses, Vec3 rel, float radius, float t, boolean reduced) {
        float front = (float) (IcarusBlast.SPEED_OF_SOUND * t / 20 * 1.02);
        if (front > DOME_REACH * radius || front < 1) return;
        float k = 1 - front / (float) (DOME_REACH * radius);
        float strength = k * k * (reduced ? 0.5f : 1f);
        IcarusDraw.sphere(buffers.getBuffer(IcarusDraw.additive(IcarusDraw.WHITE)), poses.last(), true, front, 24, 48, (lat, lon) -> {
            double nx = Math.cos(lat) * Math.cos(lon), ny = Math.sin(lat), nz = Math.cos(lat) * Math.sin(lon);
            double vx = rel.x + nx * front, vy = rel.y + ny * front, vz = rel.z + nz * front, len = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double rim = 1 - Math.abs((nx * vx + ny * vy + nz * vz) / Math.max(1e-6, len));
            return IcarusDraw.glow(SHOCK, strength * (0.12 + 0.88 * rim * rim));
        });
    }

    /** The condensation dome left behind the shock: a milky hemisphere that billows out to 1.3 radii over a few seconds,
     * thickest a second in, after the flash has faded, and thinning away by six seconds. */
    private static void condensation(net.minecraft.client.renderer.MultiBufferSource buffers, PoseStack poses, Vec3 rel, float radius, float t, boolean reduced) {
        if (t >= CONDENSATION_TICKS) return;
        float r = (float) (radius * 1.3 * (1 - Math.exp(-t / 25.0))) + 1;
        float rise = Math.min(1, t / 12f), fall = 1 - Math.clamp((t - 20) / (CONDENSATION_TICKS - 20f), 0, 1);
        float strength = rise * fall * fall * (reduced ? 0.6f : 1f);
        if (strength <= 0.01f) return;
        IcarusDraw.texturedSphere(buffers.getBuffer(IcarusDraw.surface(IcarusDraw.PLASMA)), poses.last(), r, 20, 48, 6, 3, t * 0.002f, 0, (lat, lon) -> {
            if (lat < -0.05) return 0;
            double nx = Math.cos(lat) * Math.cos(lon), ny = Math.sin(lat), nz = Math.cos(lat) * Math.sin(lon);
            double vx = rel.x + nx * r, vy = rel.y + ny * r, vz = rel.z + nz * r, len = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double rim = 1 - Math.abs((nx * vx + ny * vy + nz * vz) / Math.max(1e-6, len));
            return IcarusDraw.argb(SHOCK, strength * (0.12 + 0.5 * rim * rim) * Math.min(1, lat * 6 + 0.3));
        });
    }

    /** The fireball and the mushroom cloud it rolls into. */
    private static void mushroom(net.minecraft.client.renderer.MultiBufferSource buffers, PoseStack poses, float radius, float t, boolean reduced) {
        double grow = 1 - Math.exp(-t / 300.0);
        float heat = (float) Math.exp(-t / 220.0), life = Math.clamp((CLOUD_TICKS - t) / FADE_TICKS, 0, 1);
        float capY = (float) (radius * (0.2 + 1.4 * grow)), major = (float) (radius * (0.1 + 0.5 * (1 - Math.exp(-t / 420.0)))), minor = major * 0.6f;
        float smoke = Math.clamp((t - 20) / 100f, 0, 1) * life * 0.92f, roll = t * 0.004f;
        float anti = Math.clamp(1 - t / 8f, 0, 1);
        int core = IcarusLook.mix(IcarusLook.mix(EMBER, FIRE, Math.min(1, t / 60f)), ANTI, anti);

        // The fireball, violet-white for its first instant, swelling and rising to become the cap, its glow cooling.
        float ball = (float) (radius * (0.06 + 0.24 * Math.min(1, t / 50f)));
        if (heat > 0.05f) {
            poses.pushPose();
            poses.translate(0, Math.min(capY, radius * 0.05f + t * radius * 0.006f), 0);
            IcarusDraw.texturedSphere(buffers.getBuffer(IcarusDraw.additive(IcarusDraw.PLASMA)), poses.last(), ball, 16, 32, 3, 2, roll, -roll * 2,
                    (lat, lon) -> IcarusDraw.glow(core, heat * (reduced ? 0.6 : 1)));
            IcarusDraw.texturedSphere(buffers.getBuffer(IcarusDraw.additive(IcarusDraw.GLOW)), poses.last(), ball * 0.6f, 10, 20, 1, 1, 0, 0,
                    (lat, lon) -> IcarusDraw.glow(IcarusLook.mix(core, 0xFFFFFF, 0.5), heat * heat * (reduced ? 0.5 : 1)));
            poses.popPose();
        }
        if (smoke <= 0.01f) return;

        // Smoke, lit from below by the fire inside while it lasts, paling as it cools.
        var surface = buffers.getBuffer(IcarusDraw.surface(IcarusDraw.PLASMA));
        float stemTop = capY - minor * 0.5f, stemBase = (float) (radius * 0.16), stemNeck = (float) (radius * 0.05 + major * 0.12);
        stem(surface, poses.last(), stemBase, stemNeck, stemTop, t, (h, under) -> smokeColor(heat * (1 - h * 0.5f), under, smoke));
        poses.pushPose();
        poses.translate(0, capY, 0);
        torus(surface, poses.last(), major, minor, 48, 20, roll, (under) -> smokeColor(heat, under, smoke));
        poses.pushPose();
        poses.translate(0, minor * 0.25f, 0);
        poses.scale(1, 0.62f, 1);
        IcarusDraw.texturedSphere(surface, poses.last(), major * 0.95f, 14, 32, 4, 2, roll * 0.5f, -roll,
                (lat, lon) -> smokeColor(heat * 0.6f, (float) Math.max(0, -Math.sin(lat)), smoke));
        poses.popPose();
        poses.popPose();
        // The base surge: a low ring of dust and smoke rolling out over the ground and settling.
        float surge = (float) (radius * (0.15 + 0.85 * (1 - Math.exp(-t / 200.0)))), surgeAlpha = smoke * Math.clamp(1 - t / 900f, 0, 1);
        if (surgeAlpha > 0.01f)
            torus(surface, poses.last(), surge, (float) (radius * 0.06), 64, 10, roll * 1.5f, (under) -> smokeColor(heat * 0.4f, under, surgeAlpha * 0.7f));

        // The fire glowing through the smoke from inside, and lightning crawling through the cloud.
        if (heat > 0.03f) {
            var glow = buffers.getBuffer(IcarusDraw.additive(IcarusDraw.PLASMA));
            poses.pushPose();
            poses.translate(0, capY, 0);
            torus(glow, poses.last(), major, minor * 0.8f, 32, 14, -roll, (under) -> IcarusDraw.glow(FIRE, heat * (0.25 + 0.6 * under) * (reduced ? 0.6 : 1)));
            poses.popPose();
        }
        lightning(buffers, poses, radius, capY, major, minor, stemTop, t, reduced, life);
    }

    private interface StemShade { int argb(float height, float under); }
    private interface RingShade { int argb(float under); }

    private static int smokeColor(float heat, float under, float alpha) {
        int rgb = IcarusLook.mix(SMOKE_PALE, SMOKE, 0.5f + 0.5f * heat);
        rgb = IcarusLook.mix(rgb, FIRE, Math.min(1, heat * (0.35f + 0.65f * under)));
        return IcarusDraw.argb(rgb, alpha);
    }

    /** The stem: a column from the ground to the cap, flaring at its base and wavering as it climbs. */
    private static void stem(VertexConsumer c, PoseStack.Pose pose, float base, float neck, float top, float t, StemShade shade) {
        int rings = 12, segments = 28;
        for (int i = 0; i < rings; i++) {
            float h0 = i / (float) rings, h1 = (i + 1) / (float) rings;
            for (int j = 0; j < segments; j++) {
                double a0 = 2 * Math.PI * j / segments, a1 = 2 * Math.PI * (j + 1) / segments;
                stemVertex(c, pose, base, neck, top, t, h0, a0, j / (float) segments, shade);
                stemVertex(c, pose, base, neck, top, t, h0, a1, (j + 1) / (float) segments, shade);
                stemVertex(c, pose, base, neck, top, t, h1, a1, (j + 1) / (float) segments, shade);
                stemVertex(c, pose, base, neck, top, t, h1, a0, j / (float) segments, shade);
            }
        }
    }
    private static void stemVertex(VertexConsumer c, PoseStack.Pose pose, float base, float neck, float top, float t, float h, double a, float u, StemShade shade) {
        float flare = (float) Math.pow(1 - h, 4);
        float r = neck + (base - neck) * flare + neck * 0.12f * Mth.sin(h * 9 - t * 0.02f + (float) a * 2);
        float nx = (float) Math.cos(a), nz = (float) Math.sin(a);
        c.addVertex(pose, nx * r, h * top, nz * r).setColor(shade.argb(h, flare)).setUv(u * 3, h * 4 - t * 0.003f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, 0, nz);
    }

    /** A torus lying flat about the y axis; its tube's texture scrolls by roll so the cap churns, rising outside and
     * curling over the top. */
    private static void torus(VertexConsumer c, PoseStack.Pose pose, float major, float minor, int segments, int sides, float roll, RingShade shade) {
        for (int i = 0; i < segments; i++) {
            double u0 = 2 * Math.PI * i / segments, u1 = 2 * Math.PI * (i + 1) / segments;
            for (int j = 0; j < sides; j++) {
                double v0 = 2 * Math.PI * j / sides, v1 = 2 * Math.PI * (j + 1) / sides;
                torusVertex(c, pose, major, minor, u0, v0, i / (float) segments, j / (float) sides, roll, shade);
                torusVertex(c, pose, major, minor, u1, v0, (i + 1) / (float) segments, j / (float) sides, roll, shade);
                torusVertex(c, pose, major, minor, u1, v1, (i + 1) / (float) segments, (j + 1) / (float) sides, roll, shade);
                torusVertex(c, pose, major, minor, u0, v1, i / (float) segments, (j + 1) / (float) sides, roll, shade);
            }
        }
    }
    private static void torusVertex(VertexConsumer c, PoseStack.Pose pose, float major, float minor, double u, double v, float tu, float tv, float roll, RingShade shade) {
        float cu = (float) Math.cos(u), su = (float) Math.sin(u), cv = (float) Math.cos(v), sv = (float) Math.sin(v);
        float ring = major + minor * cv;
        c.addVertex(pose, ring * cu, minor * sv, ring * su).setColor(shade.argb(Math.max(0, -sv))).setUv(tu * 6, tv * 2 - roll * 4)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, cv * cu, sv, cv * su);
    }

    /** Lightning in the cloud: strokes hop every three ticks between points of the cap and down the stem. */
    private static void lightning(net.minecraft.client.renderer.MultiBufferSource buffers, PoseStack poses, float radius, float capY, float major, float minor,
                                  float stemTop, float t, boolean reduced, float life) {
        int slot = (int) (t / 3);
        if (t >= LIGHTNING_TICKS || !strikes(slot)) return;
        var c = buffers.getBuffer(IcarusDraw.light());
        var random = RandomSource.create(slot * 104729L + 7);
        int strokes = 1 + random.nextInt(3);
        float k = (reduced ? 0.45f : 0.95f) * life;
        for (int s = 0; s < strokes; s++) {
            double a = random.nextDouble() * Math.PI * 2, b = a + (random.nextDouble() - 0.5) * 2.5;
            var from = new Vec3(Math.cos(a) * major, capY - minor * 0.3, Math.sin(a) * major);
            var to = random.nextInt(3) == 0 ? new Vec3(0, stemTop * (0.3 + 0.5 * random.nextDouble()), 0)
                    : new Vec3(Math.cos(b) * major * (0.4 + 0.8 * random.nextDouble()), capY + minor * (random.nextDouble() - 0.5), Math.sin(b) * major * (0.4 + 0.8 * random.nextDouble()));
            IcarusDraw.bolt(c, poses.last(), from, to, slot * 31L + s, 10, 0.12, radius * 0.004f, IcarusDraw.argb(BOLT, k));
        }
    }

    /** In vacuum: a plasma sphere swelling to twice the blast radius, violet at first, then orange, fading as it thins. */
    private static void plasma(net.minecraft.client.renderer.MultiBufferSource buffers, PoseStack poses, float radius, float t, boolean reduced) {
        if (t >= PLASMA_TICKS) return;
        float r = (float) (2 * radius * (1 - Math.exp(-t / 40.0))) + 1, fade = 1 - t / PLASMA_TICKS;
        int rgb = IcarusLook.mix(ANTI, FIRE, Math.min(1, t / 30f));
        IcarusDraw.texturedSphere(buffers.getBuffer(IcarusDraw.additive(IcarusDraw.PLASMA)), poses.last(), r, 20, 40, 4, 2, t * 0.003f, 0,
                (lat, lon) -> IcarusDraw.glow(rgb, fade * fade * (reduced ? 0.4 : 0.8)));
        lightning(buffers, poses, r * 0.5f, 0, r * 0.6f, r * 0.2f, -r * 0.5f, t, reduced, fade);
    }
}
