package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.phase.PhaseManager;
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

        // No blizzard particles underground or anywhere with a real roof overhead.
        if (mc.player.blockPosition().getY() < 50) return;
        boolean exposed = ClientStormVisibility.isStormExposed(mc);
        ClientStormVisibility.WindowView windowView = exposed ? null : ClientStormVisibility.findWindowView(mc);
        if (!exposed && windowView == null) return;

        float progress = ApocalypseClientData.getProgress();

        // Phase 6 late: no particles (vacuum)
        if (PhaseManager.isVacuumActive(phase, progress)) return;

        int particleCount = getParticleCount(phase, progress);
        if (windowView != null) {
            particleCount = Math.max(2, (int) (particleCount * 0.45F));
        }

        if (particleCount <= 0) return;

        RandomSource random = mc.level.random;
        double px = windowView == null ? mc.player.getX() : windowView.outsidePoint().x;
        double py = windowView == null ? mc.player.getEyeY() : windowView.outsidePoint().y;
        double pz = windowView == null ? mc.player.getZ() : windowView.outsidePoint().z;

        long gameTime = mc.level.getGameTime();

        if (phase >= 5) {
            // Phase 5+: particles blow sideways at surface level, like a ground blizzard
            float windX = BlizzardWindHelper.getWindX(phase, progress, gameTime);
            float windZ = BlizzardWindHelper.getWindZ(phase, progress, gameTime);
            double fallSpeed = -0.08; // barely falling — almost horizontal

            for (int i = 0; i < particleCount; i++) {
                // Spawn at player height outside, or in a tighter exterior band when viewed through glass.
                double spread = windowView == null ? 20 : 7;
                double verticalSpread = windowView == null ? 3 : 2;
                double x = px + random.nextGaussian() * spread;
                double y = py + random.nextGaussian() * verticalSpread;
                double z = pz + random.nextGaussian() * spread;
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
                double spread = windowView == null ? 16 : 6;
                double x = px + random.nextGaussian() * spread;
                double y = py + (windowView == null ? 8 : 1) + random.nextDouble() * (windowView == null ? 12 : 5);
                double z = pz + random.nextGaussian() * spread;
                mc.level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, windX, fallSpeed, windZ);
            }
        }
    }

    private static int getParticleCount(int phase, float progress) {
        if (phase < 6) {
            return switch (phase) {
                case 3 -> 4;
                case 4 -> 12;
                default -> 40;
            };
        }

        return switch (PhaseManager.getPhase6Stage(phase, progress)) {
            case EARLY -> 60;
            case MID -> (int) Mth.lerp(PhaseManager.getPhase6MidFadeProgress(progress), 60f, 0f);
            case VACUUM, INACTIVE -> 0;
        };
    }
}
