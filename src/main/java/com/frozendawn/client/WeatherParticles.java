package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Spawns ambient snowflake particles around the player in phases 3+.
 * Particle density increases with phase progression.
 * Wind variation via sine wave over game time.
 * Hard cap of 8 particles/tick. Respects Minecraft particle settings.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class WeatherParticles {

    private static final int MAX_PARTICLES_PER_TICK = 8;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (mc.level.dimension() != Level.OVERWORLD) return;

        // Respect Minecraft particle settings
        if (mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL) return;

        int phase = ApocalypseClientData.getPhase();
        if (phase < 3) return;

        // 2/4/6 particles per tick for phases 3/4/5, capped at MAX_PARTICLES_PER_TICK
        int baseCount = (phase - 2) * 2;
        int particleCount = Math.min(baseCount, MAX_PARTICLES_PER_TICK);

        RandomSource random = mc.level.random;
        double px = mc.player.getX();
        double py = mc.player.getEyeY() + 8;
        double pz = mc.player.getZ();

        // Wind variation: oscillating intensity via sine wave over game time
        long gameTime = mc.level.getGameTime();
        float windStrength = 0.5f + 0.5f * (float) Math.sin(gameTime * 0.02);
        float windX = windStrength * 0.3f * (float) Math.sin(gameTime * 0.007);
        float windZ = windStrength * 0.3f * (float) Math.cos(gameTime * 0.011);

        for (int i = 0; i < particleCount; i++) {
            double x = px + random.nextGaussian() * 16;
            double y = py + random.nextDouble() * 12;
            double z = pz + random.nextGaussian() * 16;
            mc.level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, windX, -0.3, windZ);
        }
    }
}
