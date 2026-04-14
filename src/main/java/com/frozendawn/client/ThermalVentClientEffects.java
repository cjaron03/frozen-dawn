package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class ThermalVentClientEffects {

    private static BlockPos eruptionPos;
    private static float eruptionStrength;
    private static float eruptionRadius;
    private static int ticksRemaining;

    private ThermalVentClientEffects() {
    }

    public static void triggerEruption(BlockPos pos, float strength, int durationTicks, float radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        double distance = mc.player.position().distanceTo(Vec3.atCenterOf(pos));
        float effectiveRadius = Math.max(18.0F, radius);
        float falloff = Mth.clamp(1.0F - (float) (distance / effectiveRadius), 0.0F, 1.0F);
        if (falloff <= 0.0F) {
            return;
        }

        float adjustedStrength = strength * falloff;
        if (ticksRemaining <= 0 || adjustedStrength >= eruptionStrength) {
            eruptionPos = pos.immutable();
            eruptionStrength = adjustedStrength;
            eruptionRadius = effectiveRadius;
            ticksRemaining = durationTicks;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ticksRemaining > 0) {
            ticksRemaining--;
            if (ticksRemaining <= 0) {
                eruptionPos = null;
                eruptionStrength = 0.0F;
                eruptionRadius = 0.0F;
            }
        }
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ticksRemaining <= 0 || eruptionPos == null) {
            return;
        }

        double distance = mc.player.position().distanceTo(Vec3.atCenterOf(eruptionPos));
        float distanceFalloff = eruptionRadius > 0.0F
                ? Mth.clamp(1.0F - (float) (distance / eruptionRadius), 0.0F, 1.0F)
                : 1.0F;
        if (distanceFalloff <= 0.0F) {
            return;
        }

        float decay = Mth.clamp(ticksRemaining / 24.0F, 0.20F, 1.0F);
        long time = mc.level != null ? mc.level.getGameTime() : 0L;
        float wobbleX = (float) (Math.sin(time * 0.85D) * 0.55D + Math.sin(time * 1.93D) * 0.22D);
        float wobbleY = (float) (Math.cos(time * 1.12D) * 0.44D + Math.cos(time * 2.71D) * 0.18D);
        float intensity = eruptionStrength * distanceFalloff * decay;

        event.setPitch(event.getPitch() + wobbleX * intensity);
        event.setYaw(event.getYaw() + wobbleY * intensity);
    }
}
