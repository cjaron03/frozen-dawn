package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Quiet positional pressure tone inside an active Stillpoint sanctuary. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class StillpointAmbientSound {
    private static FieldHum hum;
    private static boolean crossingStateInitialized;
    private static boolean fieldWasAvailable;
    private static boolean listenerWasInside;
    private static int useCueTicks;
    private static int pendingUse;

    private StillpointAmbientSound() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (useCueTicks > 0 && --useCueTicks == 0 && pendingUse < 3
                && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.STILLPOINT_USE.get(),
                    0.88F + pendingUse * 0.10F, 1.35F));
            pendingUse = 0;
        }
        boolean fieldAvailable = minecraft.level != null && minecraft.player != null
                && StillpointClientState.isActiveHere();
        boolean listenerInside = fieldAvailable
                && StillpointClientState.isListenerInside();
        if (!crossingStateInitialized) {
            crossingStateInitialized = true;
            fieldWasAvailable = fieldAvailable;
            listenerWasInside = listenerInside;
        } else {
            if (fieldAvailable && fieldWasAvailable
                    && listenerInside != listenerWasInside) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                        listenerInside ? ModSounds.STILLPOINT_ENTER.get()
                                : ModSounds.STILLPOINT_EXIT.get(),
                        1.0F, 1.35F));
            }
            fieldWasAvailable = fieldAvailable;
            listenerWasInside = listenerInside;
        }
        if (listenerInside && (hum == null || hum.isStopped())) {
            hum = new FieldHum();
            minecraft.getSoundManager().play(hum);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (hum != null) {
            Minecraft.getInstance().getSoundManager().stop(hum);
            hum = null;
        }
        crossingStateInitialized = false;
        fieldWasAvailable = false;
        listenerWasInside = false;
        useCueTicks = 0;
        pendingUse = 0;
    }

    public static void triggerCoreBreak(int use) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) return;
        minecraft.getSoundManager().stop();
        hum = null;
        pendingUse = Math.clamp(use, 1, 3);
        useCueTicks = pendingUse >= 3 ? 0 : 8;
    }

    private static final class FieldHum extends AbstractTickableSoundInstance {
        private int age;

        private FieldHum() {
            super(ModSounds.STILLPOINT_HUM.get(), SoundSource.MASTER,
                    SoundInstance.createUnseededRandom());
            x = 0.0D;
            y = 0.0D;
            z = 0.0D;
            volume = 0.0F;
            pitch = 0.98F;
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
        }

        @Override
        public void tick() {
            boolean active = StillpointClientState.isListenerInside();
            if (!active) {
                volume = Mth.approach(volume, 0.0F, 0.065F);
                if (volume <= 0.001F) {
                    stop();
                    if (StillpointAmbientSound.hum == this) {
                        StillpointAmbientSound.hum = null;
                    }
                }
                return;
            }

            age++;
            volume = Mth.approach(volume, 0.78F, 0.028F);
            pitch = 0.98F + Mth.sin(age * 0.027F) * 0.018F
                    + Mth.sin(age * 0.009F) * 0.009F;
        }
    }
}
