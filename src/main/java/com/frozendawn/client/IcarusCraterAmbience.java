package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.power.IcarusBlast;
import com.frozendawn.world.IcarusCraterData;
import com.frozendawn.world.IcarusCraterHazards;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** A fresh crater seen from nearby: smoke columns and embers rising off the floor while it cools, lava popping while it
 * still burns, and for the first minutes grey ash drifting down out to twice the radius, with the fog closing in grey. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class IcarusCraterAmbience {
    /** Floor particles are tried within this many blocks of the player. */
    private static final int SMOKE_REACH = 48;
    private static final float ASH_RED = 0.42f, ASH_GREEN = 0.40f, ASH_BLUE = 0.38f;
    private static List<IcarusCraterData.Site> sites = List.of();
    /** How thick the ash is where the player stands, 0 to 1; drives the fog. */
    private static float haze;

    private IcarusCraterAmbience() {}

    public static void receive(List<IcarusCraterData.Site> next) { sites = List.copyOf(next); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); var level = mc.level; var player = mc.player;
        if (level == null || player == null) { haze = 0; return; }
        if (mc.isPaused()) return;
        var status = mc.options.particles().get();
        float share = status == ParticleStatus.MINIMAL ? 0.25f : status == ParticleStatus.DECREASED ? 0.5f : 1;
        var random = level.getRandom(); float thickest = 0;
        for (var site : sites) {
            long age = level.getGameTime() - site.blastTick();
            var c = site.center();
            double d = Math.hypot(player.getX() - (c.getX() + 0.5), player.getZ() - (c.getZ() + 0.5));
            float ash = (float) IcarusBlast.ash(age, d, site.radius());
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
        if (k <= 0.01f) return;
        event.setRed(event.getRed() + (ASH_RED - event.getRed()) * k);
        event.setGreen(event.getGreen() + (ASH_GREEN - event.getGreen()) * k);
        event.setBlue(event.getBlue() + (ASH_BLUE - event.getBlue()) * k);
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog event) {
        if (haze <= 0.01f) return;
        float far = event.getFarPlaneDistance(), target = Math.min(far, 48);
        event.setFarPlaneDistance(far + (target - far) * 0.85f * haze);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), 0f));
        event.setCanceled(true);
    }
}
