package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Plays deep geothermal rumbling ambience when the player is underground in phase 6.
 * Uses vanilla basalt_deltas.loop as a placeholder for deep earth sounds.
 * Volume scales with depth: louder the deeper you go.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class GeothermalAmbience {

    private static final ResourceLocation GEOTHERMAL_SOUND =
            ResourceLocation.withDefaultNamespace("ambient.basalt_deltas.loop");

    private static SoundInstance currentSound = null;
    private static float currentVolume = 0f;
    private static int retriggerCooldown = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (mc.level.dimension() != Level.OVERWORLD) return;

        int phase = ApocalypseClientData.getPhase();
        int playerY = mc.player.blockPosition().getY();

        // Only active in phase 6, below Y=0
        boolean shouldPlay = phase >= 6 && playerY < 0;

        if (!shouldPlay) {
            fadeOut(mc);
            return;
        }

        // Target volume scales with depth: 0.3 at Y=0, 0.7 at Y=-64
        float depthFraction = Mth.clamp((float) -playerY / 64f, 0f, 1f);
        float targetVolume = Mth.lerp(depthFraction, 0.3f, 0.7f);

        // Smooth fade toward target
        if (currentVolume < targetVolume) {
            currentVolume = Math.min(targetVolume, currentVolume + 0.01f);
        } else if (currentVolume > targetVolume) {
            currentVolume = Math.max(targetVolume, currentVolume - 0.01f);
        }

        // Start or retrigger the looping sound
        if (retriggerCooldown > 0) {
            retriggerCooldown--;
        }

        if (currentSound == null || !mc.getSoundManager().isActive(currentSound)) {
            if (retriggerCooldown <= 0) {
                startSound(mc);
                // Basalt deltas loop is ~17s; retrigger slightly before it ends
                retriggerCooldown = 300; // 15s
            }
        }
    }

    private static void startSound(Minecraft mc) {
        // Stop old instance if any
        if (currentSound != null) {
            mc.getSoundManager().stop(currentSound);
        }

        currentSound = new SimpleSoundInstance(
                GEOTHERMAL_SOUND,
                SoundSource.AMBIENT,
                Math.max(0.05f, currentVolume),
                0.8f + mc.level.random.nextFloat() * 0.1f,  // slight pitch variation
                SoundInstance.createUnseededRandom(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0, 0.0, 0.0,
                true
        );
        mc.getSoundManager().play(currentSound);
    }

    private static void fadeOut(Minecraft mc) {
        if (currentVolume > 0f) {
            currentVolume = Math.max(0f, currentVolume - 0.02f);
        }
        if (currentVolume <= 0.01f && currentSound != null) {
            mc.getSoundManager().stop(currentSound);
            currentSound = null;
            currentVolume = 0f;
            retriggerCooldown = 0;
        }
    }
}
