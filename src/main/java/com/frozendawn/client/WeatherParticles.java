package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.world.TemperatureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Spawns ambient snowflake particles around the player in phases 3+.
 * Phase 3: light snow. Phase 4: heavy. Phase 5: extreme blizzard whiteout.
 * Phase 6 early: maximum blizzard (60 particles). Mid: particles fade to 0. Late: none.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class WeatherParticles {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (mc.level.dimension() != Level.OVERWORLD) return;

        if (mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL) return;

        int phase = ApocalypseClientData.getPhase();
        if (phase < 3) return;

        // No blizzard particles underground or inside shelters
        if (mc.player.blockPosition().getY() < 50) return;
        if (isSheltered(mc)) return;

        float progress = ApocalypseClientData.getProgress();

        // Phase 6 late: no particles (vacuum)
        if (phase >= 6 && progress >= 0.85f) return;

        int particleCount;
        if (phase >= 6) {
            if (progress <= 0.72f) {
                // Phase 6 early: extreme blizzard, more than phase 5
                particleCount = 60;
            } else {
                // Phase 6 mid: particles fade out as atmosphere thins
                float fadeProgress = (progress - 0.72f) / 0.13f;
                particleCount = (int) Mth.lerp(Math.min(1f, fadeProgress), 60f, 0f);
            }
        } else {
            particleCount = switch (phase) {
                case 3 -> 4;
                case 4 -> 12;
                default -> 40; // phase 5: blizzard whiteout
            };
        }

        if (particleCount <= 0) return;

        RandomSource random = mc.level.random;
        double px = mc.player.getX();
        double py = mc.player.getEyeY();
        double pz = mc.player.getZ();

        long gameTime = mc.level.getGameTime();

        if (phase >= 5) {
            // Phase 5+: particles blow sideways at surface level, like a ground blizzard
            float windAngle = (float) (gameTime * 0.005);
            float windSpeed = 1.5f + 0.5f * (float) Math.sin(gameTime * 0.015);
            // Phase 6: even stronger wind in early sub-stage
            if (phase >= 6 && progress <= 0.72f) {
                windSpeed *= 1.3f;
            }
            float windX = windSpeed * (float) Math.sin(windAngle);
            float windZ = windSpeed * (float) Math.cos(windAngle);
            double fallSpeed = -0.08; // barely falling — almost horizontal

            for (int i = 0; i < particleCount; i++) {
                // Spawn at player height and slightly above, spread wide
                double x = px + random.nextGaussian() * 20;
                double y = py + random.nextGaussian() * 3; // tight vertical band around player
                double z = pz + random.nextGaussian() * 20;
                mc.level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, windX, fallSpeed, windZ);
            }
        } else {
            // Phase 3-4: normal falling snow with mild wind
            float windStrength = 0.5f + 0.5f * (float) Math.sin(gameTime * 0.02);
            float windMult = phase >= 4 ? 0.4f : 0.2f;
            float windX = windStrength * windMult * (float) Math.sin(gameTime * 0.007);
            float windZ = windStrength * windMult * (float) Math.cos(gameTime * 0.011);
            double fallSpeed = -0.3;

            for (int i = 0; i < particleCount; i++) {
                double x = px + random.nextGaussian() * 16;
                double y = py + 8 + random.nextDouble() * 12;
                double z = pz + random.nextGaussian() * 16;
                mc.level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, windX, fallSpeed, windZ);
            }
        }
    }

    /** Check if the player has a solid block or insulated glass overhead (within 4 blocks). */
    private static boolean isSheltered(Minecraft mc) {
        return TemperatureManager.isSheltered(mc.level, mc.player.blockPosition());
    }
}
