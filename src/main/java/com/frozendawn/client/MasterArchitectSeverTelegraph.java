package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.MasterArchitectSeverTelegraphPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Target-local soundscape duck and EVA distortion for Thermal Sever. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class MasterArchitectSeverTelegraph {
    private static int durationTicks;
    private static int ticksRemaining;

    private MasterArchitectSeverTelegraph() {
    }

    public static void start(MasterArchitectSeverTelegraphPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        durationTicks = Math.max(1, payload.durationTicks());
        ticksRemaining = durationTicks;
        if (minecraft.level == null) {
            return;
        }
        Entity master = minecraft.level.getEntity(payload.entityId());
        if (master != null) {
            minecraft.level.playLocalSound(
                    master.getX(), master.getY(), master.getZ(),
                    ModSounds.MASTER_ARCHITECT_THERMAL_CHARGE.get(),
                    SoundSource.HOSTILE, 1.85F, 0.82F, false);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }
        if (!minecraft.isPaused() && ticksRemaining > 0) {
            ticksRemaining--;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static boolean isActive() {
        return ticksRemaining > 0;
    }

    public static float weatherAudioMultiplier() {
        return isActive() ? 0.12F : 1.0F;
    }

    public static float windFadeRate() {
        return isActive() ? 0.20F : 0.04F;
    }

    public static float evaPitchMultiplier() {
        if (!isActive()) {
            return 1.0F;
        }
        float progress = 1.0F - ticksRemaining / (float) durationTicks;
        float drift = Mth.sin(progress * 31.0F) * 0.025F;
        return Mth.clamp(Mth.lerp(progress, 0.96F, 0.76F) + drift, 0.72F, 1.0F);
    }

    private static void reset() {
        durationTicks = 0;
        ticksRemaining = 0;
    }
}
