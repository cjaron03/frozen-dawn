package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.IcarusBlastPayload;
import com.frozendawn.power.IcarusBlast;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** What one player sees, hears and feels of an Icarus detonation. Everyone gets the flash and the lingering bright point.
 * In air the boom arrives at the speed of sound with a shockwave shake, then a fireball and smoke column rise. In vacuum
 * there is no boom: the ground shock reaches the suit as a thud, a low rumble and a micro-shake, and a plasma shell
 * expands instead of a fireball. Minecraft has no controller rumble, so the suit vibration is the camera alone. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class IcarusBlastEffects {
    private static final int FLASH_TICKS = 30, POINT_TICKS = 100, STROBE_TICKS = 4, PLUME_TICKS = 160, SHELL_TICKS = 60;
    /** Degrees of camera roll and pitch at the strongest shove, and for a vacuum suit's micro-shake. */
    private static final float AIR_SHAKE = 4f, SUIT_SHAKE = 0.6f;
    /** The bright point is drawn this far from the camera toward the blast, so it reads at any distance. */
    private static final double POINT_DISTANCE = 48;

    private record Pending(int at, SoundEvent sound, float volume, float pitch, Vec3 from) {}
    private static final List<Pending> SOUNDS = new ArrayList<>();
    private static Vec3 center = Vec3.ZERO;
    private static double radius;
    private static boolean vacuum, reduced;
    private static float flashPeak;
    private static int ticks = Integer.MAX_VALUE / 2, shakeStart, shakeTicks, plumeTicks;
    private static float shakeStrength;

    private IcarusBlastEffects() {}

    public static void receive(IcarusBlastPayload p) {
        var mc = Minecraft.getInstance(); if (mc.player == null) return;
        center = p.center(); radius = p.radius(); vacuum = p.vacuum(); ticks = 0;
        reduced = FrozenDawnConfig.ICARUS_REDUCED_FLASH.get();
        flashPeak = (float) IcarusBlast.flashStrength(p.distance(), p.radius(), reduced);
        SOUNDS.clear();
        float far = (float) Math.clamp(1.2 - p.distance() / (10 * p.radius()), 0.35, 1);
        if (vacuum) {
            // Ground shock outruns sound in air; it reaches the boots after about a quarter of the airborne delay.
            int at = (int) Math.round(IcarusBlast.soundDelaySeconds(p.distance()) * 5);
            SOUNDS.add(new Pending(at, ModSounds.ICARUS_VACUUM_THUD.get(), far, 1f, center));
            SOUNDS.add(new Pending(at + 4, ModSounds.ICARUS_RUMBLE.get(), far * 0.8f, 0.6f, center));
            shake(at, 20, SUIT_SHAKE);
            plumeTicks = SHELL_TICKS;
        } else {
            int at = (int) Math.round(IcarusBlast.soundDelaySeconds(p.distance()) * 20);
            SOUNDS.add(new Pending(at, ModSounds.ICARUS_BOOM.get(), far, 1f, center));
            SOUNDS.add(new Pending(at + 10, ModSounds.ICARUS_RUMBLE.get(), far, 1f, center));
            double shove = IcarusBlast.knockback(p.distance(), p.radius()) / Math.max(1e-6, IcarusBlast.knockback(p.radius() * 1.0001, p.radius()));
            float strength = (float) Math.max(shove * AIR_SHAKE, IcarusBlast.witness(p.distance(), p.radius()) ? 0.4 : 0);
            if (strength > 0) shake(at, 40, strength);
            plumeTicks = PLUME_TICKS;
        }
        // Too far to see the cloud: no particles, only the flash and sound.
        double view = mc.options.getEffectiveRenderDistance() * 16;
        if (p.distance() > view + 2 * p.radius()) plumeTicks = 0;
    }

    private static void shake(int at, int duration, float strength) { shakeStart = at; shakeTicks = duration; shakeStrength = strength; }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        SOUNDS.clear(); ticks = Integer.MAX_VALUE / 2; plumeTicks = 0; shakeTicks = 0; flashPeak = 0;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused() || ticks >= Integer.MAX_VALUE / 2) return;
        ticks++;
        for (var it = SOUNDS.iterator(); it.hasNext(); ) {
            var s = it.next(); if (s.at() > ticks) continue;
            it.remove();
            // Placed a few blocks toward the blast with no falloff, so it pans from the right side at any range.
            var toward = s.from().subtract(mc.player.getEyePosition());
            var at = mc.player.getEyePosition().add(toward.lengthSqr() < 1e-6 ? Vec3.ZERO : toward.normalize().scale(6));
            mc.getSoundManager().play(new SimpleSoundInstance(s.sound().getLocation(), SoundSource.BLOCKS, s.volume(), s.pitch(),
                    SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false));
        }
        if (ticks <= plumeTicks) { if (vacuum) shell(mc); else plume(mc); }
    }

    /** A fireball rising and swelling over the crater, under a column of smoke and debris that keeps climbing. */
    private static void plume(Minecraft mc) {
        var random = mc.level.random; double t = ticks / (double) PLUME_TICKS;
        double rise = radius * (0.3 + 1.7 * t), ball = radius * (0.25 + 0.25 * t);
        for (int i = 0; i < 18; i++) {
            var dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(ball * Math.cbrt(random.nextDouble()));
            ParticleOptions kind = t < 0.35 ? (random.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.LAVA) : ParticleTypes.LARGE_SMOKE;
            add(mc, kind, center.add(dir).add(0, rise, 0), dir.scale(0.01).add(0, 0.15, 0));
        }
        for (int i = 0; i < 10; i++) {
            double h = random.nextDouble() * rise, spread = radius * 0.12;
            add(mc, random.nextInt(3) == 0 ? ParticleTypes.ASH : ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    center.add(random.nextGaussian() * spread, h, random.nextGaussian() * spread), new Vec3(0, 0.08, 0));
        }
    }

    /** An expanding glowing shell of plasma and debris, with nothing to rise through. */
    private static void shell(Minecraft mc) {
        var random = mc.level.random; double r = radius * 2 * ticks / (double) SHELL_TICKS;
        for (int i = 0; i < 40; i++) {
            var dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            add(mc, i % 4 == 0 ? ParticleTypes.ASH : random.nextBoolean() ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
                    center.add(dir.scale(r)), dir.scale(radius / SHELL_TICKS));
        }
    }

    private static void add(Minecraft mc, ParticleOptions kind, Vec3 at, Vec3 motion) {
        mc.level.addAlwaysVisibleParticle(kind, true, at.x, at.y, at.z, motion.x, motion.y, motion.z);
    }

    /** Full-screen white, fading over about 1.5 s; a short strobe at the start unless reduced flash is on. */
    public static void renderFlash(GuiGraphics g, DeltaTracker delta) {
        if (ticks >= FLASH_TICKS || flashPeak <= 0.01f) return;
        float t = (ticks + delta.getGameTimeDeltaPartialTick(false)) / FLASH_TICKS;
        float alpha = flashPeak * (1 - t) * (1 - t);
        if (!reduced && ticks < STROBE_TICKS && ticks % 2 == 1) alpha *= 0.55f;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), Math.clamp(Math.round(alpha * 255), 0, 255) << 24 | 0xFFFFFF);
    }

    /** The lingering bright point in the blast's direction, under the flash. */
    @SubscribeEvent public static void renderPoint(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ticks >= POINT_TICKS || flashPeak <= 0.01f) return;
        var camera = event.getCamera(); var toward = center.subtract(camera.getPosition());
        if (toward.lengthSqr() < 1e-6) return;
        var at = toward.length() > POINT_DISTANCE ? toward.normalize().scale(POINT_DISTANCE) : toward;
        float fade = 1 - ticks / (float) POINT_TICKS, alpha = Math.min(1, flashPeak * 1.5f) * fade * fade;
        var poses = event.getPoseStack(); var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(at.x, at.y, at.z);
        poses.mulPose(camera.rotation());
        var lines = buffers.getBuffer(RenderType.lightning());
        // Stacked additive squares at two angles read as a soft round glow.
        for (int layer = 0; layer < 3; layer++) for (int turn = 0; turn < 2; turn++) {
            poses.pushPose(); poses.mulPose(Axis.ZP.rotationDegrees(45 * turn));
            float size = 0.6f + 1.1f * layer, a = alpha * 0.32f / (1 + layer);
            var m = poses.last().pose();
            lines.addVertex(m, -size, -size, 0).setColor(1f, 0.97f, 0.9f, a);
            lines.addVertex(m, size, -size, 0).setColor(1f, 0.97f, 0.9f, a);
            lines.addVertex(m, size, size, 0).setColor(1f, 0.97f, 0.9f, a);
            lines.addVertex(m, -size, size, 0).setColor(1f, 0.97f, 0.9f, a);
            poses.popPose();
        }
        buffers.endBatch(RenderType.lightning());
        poses.popPose();
    }

    /** The shockwave in air, or the suit's micro-shake in vacuum, starting when it arrives. */
    @SubscribeEvent public static void shakeCamera(ViewportEvent.ComputeCameraAngles event) {
        int into = ticks - shakeStart;
        if (into < 0 || into >= shakeTicks || shakeStrength <= 0) return;
        float scale = Minecraft.getInstance().options.screenEffectScale().get().floatValue();
        float left = 1 - into / (float) shakeTicks, time = (float) (into + event.getPartialTick());
        float amount = shakeStrength * left * left * scale;
        event.setRoll(event.getRoll() + amount * (float) Math.sin(time * 2.7));
        event.setPitch(event.getPitch() + amount * 0.6f * (float) Math.sin(time * 3.9 + 1.3));
        event.setYaw(event.getYaw() + amount * 0.4f * (float) Math.sin(time * 3.1 + 0.7));
    }
}
