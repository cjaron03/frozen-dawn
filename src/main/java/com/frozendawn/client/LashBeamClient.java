package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.defense.WhipBend;
import com.frozendawn.init.ModParticles;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.LashBeamPayload;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Draws every Sever Lash beam the server reports ({@link LashBeamPayload}): a white core in an orange sheath with a
 * violet halo, bent by {@link WhipBend} so a fast sweep trails and snaps straight with a crack, and a faint red
 * afterimage of the last few ticks. In vacuum nothing scatters the light, so only the core and the impact show. The
 * hum, crack and impact embers play here too, on every client that sees the beam.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class LashBeamClient {
    /** A beam the server stops reporting goes dark after this many client ticks. */
    private static final int GRACE_TICKS = 2;
    private static final int AFTERIMAGES = 3;
    private static final int SEGMENTS = 16;
    /** Half-widths in blocks: the halo makes the beam about a third of a block across. */
    private static final float HALO = .17F, SHEATH = .08F, CORE = .028F, TRACE = .022F;
    private static final Map<Integer, Beam> BEAMS = new HashMap<>();
    /** Each shooter's last vacuum reading, kept through a lockout so the vents smoke only in air. */
    private static final Map<Integer, Boolean> VACUUM = new HashMap<>();
    private LashBeamClient() {}

    private record Trace(Vec3 eye, Vec3 muzzle, Vec3 aim, Vec3 far, double length) {}
    private static final class Beam {
        final WhipBend whip = new WhipBend();
        final ArrayDeque<Trace> traces = new ArrayDeque<>();
        Vec3 aim, lastAim, lastFar;
        float length, heat;
        boolean landed, vacuum, fresh;
        int quiet;
        Hum hum;
    }

    public static void receive(LashBeamPayload payload) {
        var beam = BEAMS.computeIfAbsent(payload.shooter(), id -> new Beam());
        beam.aim = new Vec3(payload.direction()).normalize();
        beam.length = payload.length(); beam.heat = payload.heat();
        beam.landed = payload.landed(); beam.vacuum = payload.vacuum();
        beam.quiet = 0; beam.fresh = true;
        VACUUM.put(payload.shooter(), payload.vacuum());
    }

    /** Whether this shooter's last beam was in vacuum; air when it has not fired in view. */
    public static boolean inVacuum(int shooter) { return VACUUM.getOrDefault(shooter, false); }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        BEAMS.values().forEach(LashBeamClient::silence); BEAMS.clear(); VACUUM.clear();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        for (var it = BEAMS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            var beam = entry.getValue();
            if (!(mc.level.getEntity(entry.getKey()) instanceof LivingEntity shooter) || beam.quiet++ > GRACE_TICKS) {
                silence(beam); it.remove(); continue;
            }
            // The shooter's own beam follows their view at once; everyone else's follows the server's aim, stepped only
            // on ticks that brought one, so a tick the network skipped is not taken for the sweep stopping.
            boolean own = shooter == mc.player;
            var aim = own ? shooter.getLookAngle() : beam.aim;
            beam.lastAim = beam.whip.aim() == null ? aim : beam.whip.aim();
            beam.lastFar = beam.whip.far() == null ? aim : beam.whip.far();
            boolean crack = (own || beam.fresh) && beam.whip.step(aim);
            beam.fresh = false;
            var eye = shooter.getEyePosition();
            var muzzle = muzzle(mc, shooter, beam.whip.aim(), 1);
            beam.traces.addFirst(new Trace(eye, muzzle, beam.whip.aim(), beam.whip.far(), beam.length));
            while (beam.traces.size() > AFTERIMAGES + 1) beam.traces.removeLast();
            var curve = curve(mc.level, shooter, eye, muzzle, beam.whip.aim(), beam.whip.far(), beam.length);
            var tip = curve.points.getLast();
            if (crack) {
                var at = curve.points.get(curve.points.size() / 2);
                mc.level.playLocalSound(at.x, at.y, at.z, ModSounds.SEVER_LASH_CRACK.get(), SoundSource.PLAYERS, 1, .9F + mc.level.random.nextFloat() * .2F, false);
            }
            if (curve.hit || beam.landed) for (int i = 0; i < 2; i++) {
                var r = mc.level.random;
                mc.level.addParticle(ModParticles.SEAR_EMBER.get(), tip.x, tip.y, tip.z,
                        (r.nextDouble() - .5) * .12, r.nextDouble() * .08, (r.nextDouble() - .5) * .12);
            }
            if (beam.hum == null || !mc.getSoundManager().isActive(beam.hum)) { beam.hum = new Hum(shooter); mc.getSoundManager().play(beam.hum); }
            beam.hum.heat = beam.heat;
        }
        VACUUM.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    private static void silence(Beam beam) { if (beam.hum != null) beam.hum.released = true; }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        var mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null || BEAMS.isEmpty()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = event.getCamera().getPosition();
        var poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        var matrix = poses.last().pose();
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float time = mc.level.getGameTime() + partial;
        for (var entry : BEAMS.entrySet()) {
            var beam = entry.getValue();
            if (beam.whip.aim() == null || !(mc.level.getEntity(entry.getKey()) instanceof LivingEntity shooter)) continue;
            var aim = shooter == mc.player ? shooter.getViewVector(partial) : lerp(beam.lastAim, beam.whip.aim(), partial);
            var far = lerp(beam.lastFar, beam.whip.far(), partial);
            var eye = shooter.getEyePosition(partial);
            var curve = curve(mc.level, shooter, eye, muzzle(mc, shooter, aim, partial), aim, far, beam.length);
            if (!beam.vacuum) {
                int age = 0;
                for (var trace : beam.traces) {
                    if (age++ == 0) continue;
                    float fade = .35F * (1 - (age - 2F) / AFTERIMAGES);
                    var old = curve(mc.level, shooter, trace.eye(), trace.muzzle(), trace.aim(), trace.far(), trace.length());
                    ribbon(buffer, matrix, camera, old.points, TRACE, 1F, .2F, .14F, fade, 0);
                }
                ribbon(buffer, matrix, camera, curve.points, HALO, .78F, .22F, .56F, .42F, time);
                ribbon(buffer, matrix, camera, curve.points, SHEATH, 1F, .45F, .16F, .85F, time + 7);
            }
            ribbon(buffer, matrix, camera, curve.points, CORE, 1F, .97F, .9F, 1F, 0);
            if (curve.hit || beam.landed) flare(buffer, matrix, event.getCamera(), curve.points.getLast(), beam.vacuum ? .14F : .24F, time);
        }
        var mesh = buffer.build();
        if (mesh != null) {
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            BufferUploader.drawWithShader(mesh);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
        poses.popPose();
    }

    private record Curve(List<Vec3> points, boolean hit) {}

    /** The bent beam as a polyline from the muzzle, cut short where it meets a block. */
    private static Curve curve(Level level, LivingEntity shooter, Vec3 eye, Vec3 muzzle, Vec3 aim, Vec3 far, double length) {
        var points = new ArrayList<Vec3>(SEGMENTS + 1);
        points.add(muzzle);
        for (int i = 1; i <= SEGMENTS; i++) {
            var next = WhipBend.point(eye, muzzle, aim, far, length, (double) i / SEGMENTS);
            var hit = level.clip(new ClipContext(points.getLast(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            if (hit.getType() != HitResult.Type.MISS) { points.add(hit.getLocation()); return new Curve(points, true); }
            points.add(next);
        }
        return new Curve(points, false);
    }

    /**
     * Where the beam leaves the emitter. In first person the Lash sits low right of the view; seen from outside, the
     * lens is ahead of the raised fists, a little above the shoulder line and in toward the body (from the firing model
     * on the third-person arm chain: 1.16 forward, .25 up, .1 in from the shoulder pivot).
     */
    private static Vec3 muzzle(Minecraft mc, LivingEntity shooter, Vec3 aim, float partial) {
        int side = arm(shooter) == HumanoidArm.RIGHT ? 1 : -1;
        var eye = shooter.getEyePosition(partial);
        if (shooter == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
            Camera camera = mc.gameRenderer.getMainCamera();
            var left = new Vec3(camera.getLeftVector()); var up = new Vec3(camera.getUpVector());
            return eye.add(aim.scale(1.0)).add(left.scale(-.5 * side)).add(up.scale(-.3));
        }
        float yaw = Mth.lerp(partial, shooter.yBodyRotO, shooter.yBodyRot) * Mth.DEG_TO_RAD;
        var right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        float scale = shooter.getScale();
        var shoulder = shooter.getPosition(partial).add(0, shooter.getBbHeight() * .765, 0).add(right.scale(.31 * side * scale));
        var lift = unit(new Vec3(0, 1, 0).subtract(aim.scale(aim.y)));
        var across = unit(new Vec3(-aim.z, 0, aim.x));
        return shoulder.add(aim.scale(1.16 * scale)).add(lift.scale(.25 * scale)).add(across.scale(-.1 * side * scale));
    }

    private static Vec3 unit(Vec3 v) { return v.lengthSqr() < 1e-6 ? Vec3.ZERO : v.normalize(); }

    private static HumanoidArm arm(LivingEntity shooter) {
        var hand = shooter.isUsingItem() ? shooter.getUsedItemHand() : InteractionHand.MAIN_HAND;
        return hand == InteractionHand.MAIN_HAND ? shooter.getMainArm() : shooter.getMainArm().getOpposite();
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, float partial) {
        return from == null ? to : from.lerp(to, partial).normalize();
    }

    /**
     * A camera-facing strip along the polyline, bright down the middle and fading to nothing at its edges. A non-zero
     * {@code time} makes the width shimmer a little along the length.
     */
    private static void ribbon(BufferBuilder buffer, Matrix4f matrix, Vec3 camera, List<Vec3> points, float half,
                               float r, float g, float b, float a, float time) {
        for (int i = 1; i < points.size(); i++) {
            var p0 = points.get(i - 1); var p1 = points.get(i);
            var along = p1.subtract(p0);
            var side = along.cross(p0.add(p1).scale(.5).subtract(camera));
            if (side.lengthSqr() < 1e-12) continue;
            float w0 = half * shimmer(time, i - 1), w1 = half * shimmer(time, i);
            var s = side.normalize();
            quad(buffer, matrix, p0.subtract(s.scale(w0)), p0, p1, p1.subtract(s.scale(w1)), r, g, b, a);
            quad(buffer, matrix, p0.add(s.scale(w0)), p0, p1, p1.add(s.scale(w1)), r, g, b, a);
        }
    }

    private static float shimmer(float time, int i) { return time == 0 ? 1 : 1 + .12F * Mth.sin(time * 1.9F + i * 1.3F); }

    /** Edge, centre, centre, edge: the centre pair at full alpha and the edges clear. */
    private static void quad(BufferBuilder buffer, Matrix4f m, Vec3 e0, Vec3 c0, Vec3 c1, Vec3 e1, float r, float g, float b, float a) {
        buffer.addVertex(m, (float) e0.x, (float) e0.y, (float) e0.z).setColor(r, g, b, 0);
        buffer.addVertex(m, (float) c0.x, (float) c0.y, (float) c0.z).setColor(r, g, b, a);
        buffer.addVertex(m, (float) c1.x, (float) c1.y, (float) c1.z).setColor(r, g, b, a);
        buffer.addVertex(m, (float) e1.x, (float) e1.y, (float) e1.z).setColor(r, g, b, 0);
    }

    /** The impact: a small white-hot star facing the camera, flickering. */
    private static void flare(BufferBuilder buffer, Matrix4f m, Camera camera, Vec3 at, float size, float time) {
        var left = new Vec3(camera.getLeftVector()); var up = new Vec3(camera.getUpVector());
        float s = size * (.85F + .15F * Mth.sin(time * 3.1F));
        for (int k = 0; k < 2; k++) {
            var u = k == 0 ? left.add(up).normalize() : left.subtract(up).normalize();
            var v = k == 0 ? left.subtract(up).normalize() : left.add(up).normalize().scale(-1);
            quad(buffer, m, at.add(u.scale(s)), at, at, at.add(v.scale(s)), 1, .85F, .6F, 1);
            quad(buffer, m, at.subtract(u.scale(s)), at, at, at.subtract(v.scale(s)), 1, .85F, .6F, 1);
        }
    }

    /** The beam's hum, following the shooter; it rises in pitch as the emitter heats and fades out when the beam stops. */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final LivingEntity shooter;
        float heat;
        boolean released;

        Hum(LivingEntity shooter) {
            super(ModSounds.SEVER_LASH_HUM.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.shooter = shooter;
            looping = true; delay = 0; volume = .01F; pitch = pitch(0);
            x = shooter.getX(); y = shooter.getEyeY(); z = shooter.getZ();
        }

        /** From a low drone when cool to nearly an octave up at the overheat. */
        static float pitch(float heat) { return .75F + .65F * Mth.clamp(heat, 0, 1); }

        @Override public void tick() {
            if (shooter.isRemoved()) { stop(); return; }
            x = shooter.getX(); y = shooter.getEyeY(); z = shooter.getZ();
            pitch = pitch(heat);
            volume = released ? volume - .25F : Math.min(.7F, volume + .25F);
            if (volume <= 0) stop();
        }
        @Override public boolean canStartSilent() { return true; }
    }
}
