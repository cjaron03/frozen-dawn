package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.MobFreezeHandler;
import com.frozendawn.init.ModEffects;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.client.Minecraft;
import com.frozendawn.init.ModParticles;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
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
 * Phase 6 late without breathable air: breath particles stop.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class ColdEffects {

    private static final ColdBreathRhythm BREATH = new ColdBreathRhythm();

    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BREATH.reset();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (mc.player.isCreative() || mc.player.isSpectator()) {
            BREATH.reset();
            return;
        }

        // EVA suit is climate-controlled — suppress vanilla freeze overlay every tick
        if (MobFreezeHandler.getFullSetTier(mc.player) == 3
                && !mc.player.hasEffect(ModEffects.RIMEBOUND_ENCASEMENT)
                && !mc.player.hasEffect(ModEffects.RESONANT_GRASP)
                && mc.player.getTicksFrozen() > 0) {
            mc.player.setTicksFrozen(0);
        }

        float temp = TemperatureHud.getDisplayedTemp();
        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();
        boolean vacuum = PhaseManager.isVacuumActive(phase, progress) && !ApocalypseClientData.isBreathable();
        boolean visible = ColdBreathPolicy.visible(temp, false,
                mc.player.isUnderWater() || mc.player.isInLava(), vacuum);
        boolean running = mc.player.isSprinting() && !mc.player.isPassenger()
                && !mc.player.isFallFlying()
                && mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.0025;
        if (BREATH.tick(temp, visible, running)) spawnBreathParticle(mc);
    }

    private static void spawnBreathParticle(Minecraft mc) {
        // Position: in front of player's face at mouth height
        Vec3 look = mc.player.getLookAngle();
        double eyeY = mc.player.getEyeY() - 0.15; // mouth height
        double x = mc.player.getX() + look.x * 0.5;
        double y = eyeY + look.y * 0.5;
        double z = mc.player.getZ() + look.z * 0.5;

        // A small forward exhale with gentle buoyancy; the custom wisps have no cloud gravity.
        double speed = 0.035;
        double vx = look.x * speed + mc.level.random.nextGaussian() * 0.002;
        double vy = look.y * speed + 0.008 + mc.level.random.nextGaussian() * 0.002;
        double vz = look.z * speed + mc.level.random.nextGaussian() * 0.002;

        mc.level.addParticle(ModParticles.COLD_BREATH.get(),
                x + mc.level.random.nextGaussian() * 0.012,
                y + mc.level.random.nextGaussian() * 0.008,
                z + mc.level.random.nextGaussian() * 0.012, vx, vy, vz);
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

        // Full EVA suit (tier 3+) is climate-controlled — no shivering
        if (MobFreezeHandler.getFullSetTier(mc.player) == 3) return;

        float temp = TemperatureHud.getDisplayedTemp();
        // Factor in armor cold resistance — no shivering when protected
        float effectiveTemp = temp + MobFreezeHandler.getArmorColdResistance(mc.player);
        if (effectiveTemp >= -5f) return;

        // Shiver intensity: 0 at -5C, max at -50C (based on effective temp)
        float intensity = Math.min(1f, (-effectiveTemp - 5f) / 45f);

        // Phase 6: push intensity higher — extreme shivering
        int phase = ApocalypseClientData.getPhase();
        if (PhaseManager.isPhase6Active(phase)) {
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
