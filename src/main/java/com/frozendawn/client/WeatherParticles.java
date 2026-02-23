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
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class WeatherParticles {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (mc.level.dimension() != Level.OVERWORLD) return;

        int phase = ApocalypseClientData.getPhase();
        if (phase < 3) return;

        // 2/4/6 particles per tick for phases 3/4/5
        int particleCount = (phase - 2) * 2;
        RandomSource random = mc.level.random;
        double px = mc.player.getX();
        double py = mc.player.getEyeY() + 8;
        double pz = mc.player.getZ();

        for (int i = 0; i < particleCount; i++) {
            double x = px + random.nextGaussian() * 16;
            double y = py + random.nextDouble() * 12;
            double z = pz + random.nextGaussian() * 16;
            mc.level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0, -0.3, 0);
        }
    }
}
