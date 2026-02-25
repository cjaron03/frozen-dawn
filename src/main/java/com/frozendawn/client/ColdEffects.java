package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.MobFreezeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client-side cold effects based on player temperature.
 *
 * Below 0C: visible breath (small cloud puffs near mouth).
 * Below -5C: camera shivering that intensifies with cold.
 * Phase 6 early: extreme shivering (higher intensity).
 * Phase 6 mid+: breath particles stop (no air to exhale).
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class ColdEffects {

    private static int breathCooldown = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (mc.player.isCreative() || mc.player.isSpectator()) return;

        float temp = TemperatureHud.getDisplayedTemp();
        if (temp >= 0f) {
            breathCooldown = 0;
            return;
        }

        // Phase 6 mid+ exposed to sky: no breath particles — no atmosphere to exhale
        // Under a roof/underground there's trapped air, so breath is visible
        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();
        if (phase >= 6 && progress > 0.72f
                && mc.level.canSeeSky(mc.player.blockPosition().above())) {
            breathCooldown = 0;
            return;
        }

        // Breath particles: small cloud puffs in front of the player's face
        if (breathCooldown > 0) {
            breathCooldown--;
        } else {
            spawnBreathParticle(mc, temp);
            // Breathe faster when colder: every 30-60 ticks
            float coldness = Math.min(1f, -temp / 40f);
            breathCooldown = (int) Mth.lerp(coldness, 60, 25);
        }
    }

    private static void spawnBreathParticle(Minecraft mc, float temp) {
        // Position: in front of player's face at mouth height
        Vec3 look = mc.player.getLookAngle();
        double eyeY = mc.player.getEyeY() - 0.15; // mouth height
        double x = mc.player.getX() + look.x * 0.5;
        double y = eyeY + look.y * 0.5;
        double z = mc.player.getZ() + look.z * 0.5;

        // Push forward in look direction, strong upward to counteract cloud particle gravity
        double speed = 0.06;
        double vx = look.x * speed + mc.level.random.nextGaussian() * 0.008;
        double vy = look.y * speed + 0.08 + mc.level.random.nextGaussian() * 0.005;
        double vz = look.z * speed + mc.level.random.nextGaussian() * 0.008;

        // More puffs when colder
        int count = -temp > 20f ? 2 : 1;
        for (int i = 0; i < count; i++) {
            mc.level.addParticle(ParticleTypes.CLOUD,
                    x + mc.level.random.nextGaussian() * 0.05,
                    y + mc.level.random.nextGaussian() * 0.03,
                    z + mc.level.random.nextGaussian() * 0.05,
                    vx, vy, vz);
        }
    }

    /**
     * Camera shivering when below -5C effective temperature.
     * Suppressed when armor brings effective temp above -5C.
     * Phase 6: extreme shivering — the cold is beyond survivable.
     */
    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.player.isCreative() || mc.player.isSpectator()) return;

        float temp = TemperatureHud.getDisplayedTemp();
        // Factor in armor cold resistance — no shivering when protected
        float effectiveTemp = temp + MobFreezeHandler.getArmorColdResistance(mc.player);
        if (effectiveTemp >= -5f) return;

        // Shiver intensity: 0 at -5C, max at -50C (based on effective temp)
        float intensity = Math.min(1f, (-effectiveTemp - 5f) / 45f);

        // Phase 6: push intensity higher — extreme shivering
        int phase = ApocalypseClientData.getPhase();
        if (phase >= 6) {
            intensity = Math.min(1.5f, intensity * 1.5f);
        }

        // Small rapid tremors using game time for variation
        long time = mc.level != null ? mc.level.getGameTime() : 0;
        float shakeX = (float) (Math.sin(time * 1.7) * 0.3 + Math.sin(time * 3.1) * 0.15) * intensity;
        float shakeY = (float) (Math.cos(time * 2.3) * 0.2 + Math.cos(time * 4.7) * 0.1) * intensity;

        event.setPitch(event.getPitch() + shakeX);
        event.setYaw(event.getYaw() + shakeY);
    }
}
