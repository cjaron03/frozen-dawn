package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class TownPABroadcastTracker {

    private static final long CLIP_DURATION_TICKS = 22L * 20L;
    private static final double HEARING_RANGE = 64.0;
    private static final float MAX_ALARM_VOLUME_DUCK = 0.38f;
    private static final float MAX_ALARM_PITCH_DUCK = 0.06f;

    private static long activeUntilGameTime = Long.MIN_VALUE;
    private static Vec3 sourcePos;

    private TownPABroadcastTracker() {
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        SoundInstance sound = event.getSound();
        if (sound == null) {
            return;
        }

        ResourceLocation location = sound.getLocation();
        if (!location.getNamespace().equals(FrozenDawn.MOD_ID)) {
            return;
        }

        String path = location.getPath();
        if (!path.equals("blocks.town_pa_clear") && !path.equals("blocks.town_pa_degraded")) {
            return;
        }

        sourcePos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        activeUntilGameTime = mc.level.getGameTime() + CLIP_DURATION_TICKS;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static float getAlarmVolumeDuck(ClientLevel level, Vec3 listenerPos) {
        float influence = getInfluence(level, listenerPos);
        return 1.0f - (MAX_ALARM_VOLUME_DUCK * influence);
    }

    public static float getAlarmPitchDuck(ClientLevel level, Vec3 listenerPos) {
        float influence = getInfluence(level, listenerPos);
        return 1.0f - (MAX_ALARM_PITCH_DUCK * influence);
    }

    private static float getInfluence(ClientLevel level, Vec3 listenerPos) {
        if (sourcePos == null || ApocalypseClientData.getPhase() > 4) {
            clear();
            return 0.0f;
        }
        if (level.getGameTime() > activeUntilGameTime) {
            clear();
            return 0.0f;
        }

        double distance = listenerPos.distanceTo(sourcePos);
        if (distance >= HEARING_RANGE) {
            return 0.0f;
        }

        return 1.0f - Mth.clamp((float) (distance / HEARING_RANGE), 0.0f, 1.0f);
    }

    private static void clear() {
        activeUntilGameTime = Long.MIN_VALUE;
        sourcePos = null;
    }
}
