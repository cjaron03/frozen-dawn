package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class StormExposureController {

    private static final int COVERED_GRACE_TICKS = 16;
    private static final float FADE_IN_STEP = 1.0F / 30.0F;
    private static final float FADE_OUT_STEP = 1.0F / 50.0F;
    private static final float ACTIVE_THRESHOLD = 0.02F;

    private static float currentExposure = 0.0F;
    private static int coveredGraceTicks = 0;

    private StormExposureController() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            return;
        }

        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();
        tick(mc, phase, progress);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    static float getExposure() {
        return currentExposure;
    }

    static boolean isActive() {
        return currentExposure > ACTIVE_THRESHOLD;
    }

    static void reset() {
        currentExposure = 0.0F;
        coveredGraceTicks = 0;
    }

    private static void tick(Minecraft mc, int phase, float progress) {
        float targetExposure = getTargetExposure(mc, phase, progress);
        float step = targetExposure > currentExposure ? FADE_IN_STEP : FADE_OUT_STEP;
        currentExposure = moveToward(currentExposure, targetExposure, step);

        if (currentExposure < ACTIVE_THRESHOLD && targetExposure <= 0.0F) {
            currentExposure = 0.0F;
        }
    }

    private static float getTargetExposure(Minecraft mc, int phase, float progress) {
        if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD) {
            coveredGraceTicks = 0;
            return 0.0F;
        }
        if (phase < 3 || PhaseManager.isVacuumActive(phase, progress) || mc.player.blockPosition().getY() < 50) {
            coveredGraceTicks = 0;
            return 0.0F;
        }

        boolean strictExposed = ClientStormVisibility.isStormExposed(mc);
        float sampledExposure = sampleSkyExposure(mc);
        if (strictExposed) {
            coveredGraceTicks = COVERED_GRACE_TICKS;
            return sampledExposure;
        }
        if (coveredGraceTicks > 0) {
            coveredGraceTicks--;
            return Math.min(currentExposure, sampledExposure);
        }
        return 0.0F;
    }

    private static float sampleSkyExposure(Minecraft mc) {
        BlockPos center = mc.player.blockPosition().above();
        int skyWeight = 0;
        int totalWeight = 0;

        skyWeight += skyWeight(mc, center, 3);
        totalWeight += 3;

        skyWeight += skyWeight(mc, center.north(), 1);
        skyWeight += skyWeight(mc, center.south(), 1);
        skyWeight += skyWeight(mc, center.east(), 1);
        skyWeight += skyWeight(mc, center.west(), 1);
        totalWeight += 4;

        skyWeight += skyWeight(mc, center.above(), 1);
        totalWeight += 1;

        return Mth.clamp(skyWeight / (float) totalWeight, 0.0F, 1.0F);
    }

    private static int skyWeight(Minecraft mc, BlockPos pos, int weight) {
        return mc.level.canSeeSky(pos) ? weight : 0;
    }

    private static float moveToward(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }
}
