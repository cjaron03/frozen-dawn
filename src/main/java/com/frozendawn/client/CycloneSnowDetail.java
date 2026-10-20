package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.cyclone.CycloneWind;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Fixed mesh detail for active cyclones; never creates particle entities or scans terrain. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class CycloneSnowDetail {
    public static final int MAX_FLAKES_PER_TICK = 120;
    public static final int MAX_STREAKS = 24;
    private static float strength;
    private static float quality = 1;
    private static int activeTicks;

    private CycloneSnowDetail() {}
    public static float strength() { return strength; }
    public static float quality() { return quality; }
    public static float nearFraction(float intensity) { return .65f * intensity * strength * quality; }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        strength = 0; quality = 1; activeTicks = 0;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        var storm = CycloneClientState.storm().orElse(null);
        float intensity = CycloneClientState.sampleLocal(0).intensity();
        if (mc.player == null || mc.level == null || mc.level.dimension() != Level.OVERWORLD
                || storm == null || intensity <= 0 || mc.options.particles().get() == ParticleStatus.MINIMAL
                || mc.player.isInWaterOrBubble() || mc.player.isInLava()) {
            strength = 0; activeTicks = 0; return;
        }
        float target = intensity * Math.min(StormExposureController.getExposure(),
                CycloneWind.exposure(mc.level, mc.player, storm.headingRad())) * HeartQuietClient.environmentMultiplier();
        if (mc.options.particles().get() == ParticleStatus.DECREASED) target *= .5f;
        strength += Math.clamp(target - strength, -.15f, .10f);
        // Ignore loading/menu FPS. Slowly recover detail so a fluctuating FPS counter cannot flash it.
        if (mc.isWindowActive() && mc.screen == null && ++activeTicks > 60 && activeTicks % 20 == 0) {
            int fps = mc.getFps();
            if (fps > 0 && fps < 40) quality = Math.max(.15f, quality - .15f);
            else if (fps >= 55) quality = Math.min(1, quality + .05f);
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || strength <= .001f) return;
        var mc = Minecraft.getInstance();
        var storm = CycloneClientState.storm().orElse(null);
        if (mc.level == null || mc.player == null || storm == null
                || mc.options.particles().get() == ParticleStatus.MINIMAL) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = CycloneClientState.time(partial);
        var camera = event.getCamera().getPosition();
        var matrix = event.getPoseStack().last().pose();
        var axis = CycloneWind.direction(storm.headingRad());
        var cross = new Vec3(-axis.z, 0, axis.x);
        double alongCamera = camera.dot(axis), acrossCamera = camera.dot(cross);
        float detail = strength * quality;
        int streaks = Math.max(1, (int) Math.ceil(MAX_STREAKS * quality
                * (mc.options.particles().get() == ParticleStatus.DECREASED ? .5f : 1)));
        double gust = CycloneWind.gust((long) time, storm.seed());
        double seed = (storm.seed() & 65535L) * .001;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < streaks; i++) {
                double along = wrap(time * .95 + hash(i + seed) * 24 - alongCamera, 24);
                double across = wrap(hash(i * 2.7 + seed + 37) * 24 - acrossCamera, 24);
                double y = hash(i * 4.1 + seed + 91) * 5 - 1.6;
                var center = axis.scale(along).add(cross.scale(across)).add(0, y, 0);
                float edge = smooth(0, 2.5, 12 - Math.abs(along)) * smooth(0, 2.5, 12 - Math.abs(across));
                double length = (.45 + .90 * hash(i * 3.3 + seed)) * gust;
                streak(buffer, matrix, center, axis, length, .012 + .012 * hash(i + 43), detail * edge * .28f);
            }
            if (mc.player.onGround() && detail > .025f) {
                int sheets = mc.options.particles().get() == ParticleStatus.DECREASED || quality < .6f ? 1 : 2;
                for (int i = 0; i < sheets; i++) {
                    double along = wrap(time * .34 + i * 12 + seed - alongCamera, 24);
                    double across = wrap(i * 9.7 + seed * 3 - acrossCamera, 18);
                    var center = axis.scale(along).add(cross.scale(across))
                            .add(0, mc.player.getY() - camera.y + .08, 0);
                    float edge = smooth(0, 4, 12 - Math.abs(along)) * smooth(0, 3, 9 - Math.abs(across));
                    sheet(buffer, matrix, center, cross, time, i + seed, detail * edge * .10f);
                }
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    private static void streak(BufferBuilder b, Matrix4f m, Vec3 center, Vec3 axis,
                               double length, double width, float alpha) {
        var side = axis.cross(center.scale(-1)).normalize();
        if (side.lengthSqr() < .001) side = new Vec3(0, 1, 0);
        side = side.scale(width);
        for (int segment = 0; segment < 3; segment++) {
            var a = center.add(axis.scale(length * (segment / 3.0 - .5)));
            var z = center.add(axis.scale(length * ((segment + 1) / 3.0 - .5)));
            float start = segment == 0 ? 0 : alpha, end = segment == 2 ? 0 : alpha;
            point(b, m, a.add(side), start); point(b, m, a.subtract(side), start);
            point(b, m, z.subtract(side), end); point(b, m, z.add(side), end);
        }
    }

    private static void sheet(BufferBuilder b, Matrix4f m, Vec3 center, Vec3 axis,
                              double time, double seed, float alpha) {
        // A low rolling veil; depth testing clips it against terrain instead of probing blocks.
        for (int column = 0; column < 12; column++) {
            double u = column / 12.0, next = (column + 1) / 12.0;
            double h = .35 + .25 * Math.sin(u * 9 + seed + time / 80);
            double nh = .35 + .25 * Math.sin(next * 9 + seed + time / 80);
            var a = center.add(axis.scale((u - .5) * 7));
            var z = center.add(axis.scale((next - .5) * 7));
            float side = (float) Math.sin(u * Math.PI), ns = (float) Math.sin(next * Math.PI);
            for (int row = 0; row < 4; row++) {
                double v = row / 4.0, above = (row + 1) / 4.0;
                float low = (float) Math.sin(v * Math.PI), high = (float) Math.sin(above * Math.PI);
                point(b, m, a.add(0, h * v, 0), alpha * side * low);
                point(b, m, z.add(0, nh * v, 0), alpha * ns * low);
                point(b, m, z.add(0, nh * above, 0), alpha * ns * high);
                point(b, m, a.add(0, h * above, 0), alpha * side * high);
            }
        }
    }

    private static void point(BufferBuilder b, Matrix4f m, Vec3 p, float alpha) {
        b.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(.72f, .79f, .87f, alpha);
    }
    private static double hash(double n) { double v = Math.sin(n * 127.1 + 311.7) * 43758.5453; return v - Math.floor(v); }
    private static double wrap(double x, double width) { return x - width * Math.floor((x + width / 2) / width); }
    private static float smooth(double low, double high, double value) {
        double f = Math.clamp((value - low) / (high - low), 0, 1); return (float) (f * f * (3 - 2 * f));
    }
}
