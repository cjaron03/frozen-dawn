package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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

        CoverProfile cover = sampleCoverProfile(mc);
        if (cover.directSky()) {
            coveredGraceTicks = COVERED_GRACE_TICKS;
            return cover.isOpenAir() ? 1.0F : cover.weightedSkyExposure();
        }
        if (cover.isTemporaryCover()) {
            coveredGraceTicks = COVERED_GRACE_TICKS;
            return Math.max(currentExposure, 0.85F);
        }
        if (coveredGraceTicks > 0) {
            coveredGraceTicks--;
            return currentExposure;
        }
        return 0.0F;
    }

    private static CoverProfile sampleCoverProfile(Minecraft mc) {
        BlockPos center = mc.player.blockPosition().above();
        boolean directSky = mc.level.canSeeSky(center);
        int cardinalSky = skyCount(mc, center.north())
                + skyCount(mc, center.south())
                + skyCount(mc, center.east())
                + skyCount(mc, center.west());
        int nearbySky = 0;
        int nearbySamples = 0;
        boolean leafCover = false;

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    continue;
                }
                BlockPos sample = center.offset(x, 0, z);
                nearbySky += skyCount(mc, sample);
                nearbySamples++;
            }
        }

        for (int x = -1; x <= 1 && !leafCover; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos sample = center.offset(x, 0, z);
                BlockState state = mc.level.getBlockState(sample);
                BlockState above = mc.level.getBlockState(sample.above());
                if (state.is(BlockTags.LEAVES) || above.is(BlockTags.LEAVES)) {
                    leafCover = true;
                    break;
                }
            }
        }

        return new CoverProfile(directSky, cardinalSky, nearbySky, nearbySamples, leafCover);
    }

    private static int skyCount(Minecraft mc, BlockPos pos) {
        return mc.level.canSeeSky(pos) ? 1 : 0;
    }

    private static float moveToward(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }

    private record CoverProfile(boolean directSky, int cardinalSky, int nearbySky, int nearbySamples, boolean leafCover) {
        private float nearbySkyRatio() {
            if (nearbySamples <= 0) {
                return 0.0F;
            }
            return nearbySky / (float) nearbySamples;
        }

        private float weightedSkyExposure() {
            float weighted = (directSky ? 3.0F : 0.0F) + cardinalSky + nearbySkyRatio();
            return Mth.clamp(weighted / 8.0F, 0.0F, 1.0F);
        }

        private boolean isOpenAir() {
            return cardinalSky >= 2 || nearbySkyRatio() >= 0.35F;
        }

        private boolean isTemporaryCover() {
            return leafCover || cardinalSky >= 1 || nearbySkyRatio() >= 0.25F;
        }
    }
}
