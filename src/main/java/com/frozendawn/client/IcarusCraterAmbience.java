package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.power.IcarusBlast;
import com.frozendawn.power.IcarusLook;
import com.frozendawn.world.IcarusCraterData;
import com.frozendawn.world.IcarusCraterHazards;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** A fresh crater seen from nearby: smoke columns and embers rising off the floor while it cools, lava popping while it
 * still burns, and for the first minutes grey ash drifting down out to twice the radius, with the fog closing in grey.
 * Overhead a soot pall spreads out over the region, in air and vacuum alike at the owner's request: two dark layers hide
 * the sun, lit orange from beneath while the floor burns, and under them the fog and the daylight dim. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class IcarusCraterAmbience {
    /** Floor particles are tried within this many blocks of the player. */
    private static final int SMOKE_REACH = 48;
    private static final float ASH_RED = 0.42f, ASH_GREEN = 0.40f, ASH_BLUE = 0.38f;
    private static List<IcarusCraterData.Site> sites = List.of();
    private static final int SOOT = 0x2C2724, UNDERGLOW = 0xFF6A20;
    /** How thick the ash is where the player stands, 0 to 1; drives the fog. */
    private static float haze;
    /** How thickly the soot pall covers the sky where the player stands, 0 to 1; dims fog and daylight. */
    private static float cover;

    private IcarusCraterAmbience() {}

    public static void receive(List<IcarusCraterData.Site> next) { sites = List.copyOf(next); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); var level = mc.level; var player = mc.player;
        if (level == null || player == null) { haze = 0; cover = 0; return; }
        if (mc.isPaused()) return;
        var status = mc.options.particles().get();
        float share = status == ParticleStatus.MINIMAL ? 0.25f : status == ParticleStatus.DECREASED ? 0.5f : 1;
        var random = level.getRandom(); float thickest = 0, pall = 0;
        for (var site : sites) {
            long age = level.getGameTime() - site.blastTick();
            var c = site.center();
            double d = Math.hypot(player.getX() - (c.getX() + 0.5), player.getZ() - (c.getZ() + 0.5));
            float ash = (float) IcarusBlast.ash(age, d, site.radius());
            pall = Math.max(pall, (float) IcarusBlast.pall(age, d, site.radius()));
            thickest = Math.max(thickest, ash);
            if (ash > 0) fallingAsh(mc, random, ash * share);
            double smoke = IcarusBlast.smokeHeat(age), floor = IcarusBlast.floorHeat(age);
            if (smoke < 0.03 || d > site.radius() + SMOKE_REACH) continue;
            int tries = Math.round((float) (10 * smoke * share));
            for (int i = 0; i < tries; i++) {
                double x = player.getX() + (random.nextDouble() * 2 - 1) * SMOKE_REACH, z = player.getZ() + (random.nextDouble() * 2 - 1) * SMOKE_REACH;
                if (Math.hypot(x - (c.getX() + 0.5), z - (c.getZ() + 0.5)) > 0.95 * site.radius()) continue;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) Math.floor(x), (int) Math.floor(z));
                if (y > c.getY() + IcarusCraterHazards.BOWL_ABOVE) continue;
                float roll = random.nextFloat();
                if (roll < 0.25f) level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, x, y + 0.2, z,
                        0.01 * random.nextGaussian(), 0.04 + 0.05 * random.nextFloat(), 0.01 * random.nextGaussian());
                else if (roll < 0.25f + 0.6f * floor) level.addParticle(ParticleTypes.SMALL_FLAME, x, y + 0.1, z,
                        0.02 * random.nextGaussian(), 0.03 + 0.06 * random.nextFloat(), 0.02 * random.nextGaussian());
                else if (floor >= IcarusBlast.FLOOR_BURNS_ABOVE && roll > 0.97f) level.addParticle(ParticleTypes.LAVA, x, y + 0.1, z, 0, 0, 0);
                else level.addParticle(ParticleTypes.SMOKE, x, y + 0.2, z, 0, 0.03 + 0.03 * random.nextFloat(), 0);
            }
        }
        haze += (thickest - haze) * 0.05f;
        cover += (pall - cover) * 0.05f;
    }

    /** How much the pall dims daylight, for the lightmap: up to 0.55 under a full pall. */
    public static float skyDim() { return 0.55f * cover; }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || sites.isEmpty()) return;
        var mc = Minecraft.getInstance(); var level = mc.level;
        if (level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var cam = event.getCamera().getPosition();
        double now = level.getGameTime() + pt, reach = mc.options.getEffectiveRenderDistance() * 16 * 1.3;
        float day = level.getSkyDarken(pt);
        var buffers = mc.renderBuffers().bufferSource(); var pose = event.getPoseStack().last();
        for (var site : sites) {
            double age = now - site.blastTick(), r = site.radius();
            if (age < 0 || age >= IcarusBlast.PALL_TICKS) continue;
            var c = site.center();
            if (Math.hypot(cam.x - c.getX(), cam.z - c.getZ()) > IcarusBlast.PALL_REACH * r + reach) continue;
            double altitude = Math.min(c.getY() + 140, 300);
            var buffer = buffers.getBuffer(IcarusDraw.surface(IcarusDraw.PLASMA));
            ceiling(buffer, pose, site, age, cam, reach, altitude, (float) (now * 0.00004), 0.7f, day);
            ceiling(buffer, pose, site, age, cam, reach, altitude + 18, (float) (-now * 0.00003), 0.7f, day);
        }
        buffers.endBatch();
    }

    /** One soot layer as a world-aligned grid around the camera, each corner as thick as the pall over it. */
    private static void ceiling(VertexConsumer buffer, PoseStack.Pose pose, IcarusCraterData.Site site, double age, Vec3 cam, double reach,
                                double y, float scroll, float alpha, float day) {
        int cells = 32; double cell = 2 * reach / cells;
        double x0 = Math.floor((cam.x - reach) / cell) * cell, z0 = Math.floor((cam.z - reach) / cell) * cell;
        float floor = (float) IcarusBlast.floorHeat(age);
        for (int i = 0; i < cells; i++) for (int j = 0; j < cells; j++) {
            double ax = x0 + i * cell, az = z0 + j * cell, bx = ax + cell, bz = az + cell;
            int c00 = shade(site, age, ax, az, alpha, day, floor), c10 = shade(site, age, bx, az, alpha, day, floor),
                    c11 = shade(site, age, bx, bz, alpha, day, floor), c01 = shade(site, age, ax, bz, alpha, day, floor);
            if ((c00 | c10 | c11 | c01) >>> 24 == 0) continue;
            corner(buffer, pose, cam, ax, y, az, c00, scroll); corner(buffer, pose, cam, bx, y, az, c10, scroll);
            corner(buffer, pose, cam, bx, y, bz, c11, scroll); corner(buffer, pose, cam, ax, y, bz, c01, scroll);
        }
    }

    private static int shade(IcarusCraterData.Site site, double age, double x, double z, float alpha, float day, float floor) {
        var c = site.center(); double d = Math.hypot(x - c.getX(), z - c.getZ());
        double k = IcarusBlast.pall(age, d, site.radius());
        if (k <= 0) return 0;
        float glow = floor * 0.6f * (float) Math.max(0, 1 - d / (1.5 * site.radius()));
        int rgb = IcarusLook.mix(SOOT, UNDERGLOW, glow);
        return IcarusDraw.argb(IcarusDraw.glow(rgb, Math.max(day, glow)), alpha * k);
    }

    private static void corner(VertexConsumer buffer, PoseStack.Pose pose, Vec3 cam, double x, double y, double z, int argb, float scroll) {
        buffer.addVertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(argb)
                .setUv((float) (x / 160) + scroll, (float) (z / 160)).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, -1, 0);
    }

    private static void fallingAsh(Minecraft mc, RandomSource random, float amount) {
        var player = mc.player; int count = Math.round(30 * amount);
        for (int i = 0; i < count; i++) {
            double x = player.getX() + (random.nextDouble() * 2 - 1) * 20, y = player.getY() + random.nextDouble() * 16 - 2,
                    z = player.getZ() + (random.nextDouble() * 2 - 1) * 20;
            mc.level.addParticle(random.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH, x, y, z, 0, -0.02, 0);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        float k = 0.75f * haze;
        if (k > 0.01f) {
            event.setRed(event.getRed() + (ASH_RED - event.getRed()) * k);
            event.setGreen(event.getGreen() + (ASH_GREEN - event.getGreen()) * k);
            event.setBlue(event.getBlue() + (ASH_BLUE - event.getBlue()) * k);
        }
        // Under the pall the light is dim and brown: darken, the blue most.
        if (cover > 0.01f) {
            event.setRed(event.getRed() * (1 - 0.62f * cover));
            event.setGreen(event.getGreen() * (1 - 0.66f * cover));
            event.setBlue(event.getBlue() * (1 - 0.72f * cover));
        }
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog event) {
        if (haze <= 0.01f && cover <= 0.01f) return;
        float far = event.getFarPlaneDistance(), ash = Math.min(far, 48) - far, pall = Math.max(64, far * 0.55f) - far;
        event.setFarPlaneDistance(far + Math.min(ash * 0.85f * haze, pall * 0.8f * cover));
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), 0f));
        event.setCanceled(true);
    }
}
