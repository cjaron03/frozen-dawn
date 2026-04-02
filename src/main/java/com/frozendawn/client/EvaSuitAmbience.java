package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.MobFreezeHandler;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModSounds;
import com.frozendawn.item.O2TankItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Plays EVA suit breathing ambience in phase 6 late (vacuum) only while the
 * suit has usable O2. Also plays a suffocation gasp when the player enters or
 * falls into unprotected vacuum.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class EvaSuitAmbience {

    private static final int CLIP_DURATION = 300;  // 15s in ticks (matches the ogg length)
    private static final int OVERLAP = 40;          // 2s overlap for seamless loop
    private static final float TARGET_VOLUME = 0.5f;

    private static TickableWindSound currentSound = null;
    private static SimpleSoundInstance suffocateSound = null;
    private static int ticksUntilNext = 0;
    private static boolean wasSuffocating = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            stopAll(mc);
            resetSuffocationState(mc);
            return;
        }
        if (mc.level.dimension() != Level.OVERWORLD) {
            stopAll(mc);
            resetSuffocationState(mc);
            return;
        }
        if (mc.player.isCreative() || mc.player.isSpectator()) {
            stopAll(mc);
            resetSuffocationState(mc);
            return;
        }

        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();

        boolean inVacuum = phase >= 6 && progress >= 0.85f;
        boolean fullEva = MobFreezeHandler.getFullSetTier(mc.player) == 3;
        boolean hasUsableO2 = hasUsableO2Tank(mc.player);
        boolean vacuumExposure = inVacuum && !ApocalypseClientData.isBreathable();
        boolean canUseO2 = vacuumExposure && fullEva && hasUsableO2;
        boolean suffocating = vacuumExposure && !canUseO2;

        if (suffocating) {
            if (!wasSuffocating) {
                suffocateSound = SimpleSoundInstance.forUI(
                        ModSounds.EVA_SUFFOCATE.get(), 1.0f, 0.8f);
                mc.getSoundManager().play(suffocateSound);
            }
            wasSuffocating = true;
        } else {
            resetSuffocationState(mc);
        }

        if (!canUseO2) {
            stopAll(mc);
            return;
        }

        // Update volume on current sound
        if (currentSound != null && !currentSound.isStopped()) {
            currentSound.setTargetVolume(TARGET_VOLUME);
        }

        if (ticksUntilNext > 0) {
            ticksUntilNext--;
            if (ticksUntilNext > 0) return;
        }

        // Start next clip — old one may still be playing for overlap
        float pitch = 0.98f + mc.level.random.nextFloat() * 0.04f;
        currentSound = new TickableWindSound(
                ModSounds.EVA_BREATHING.get(),
                TARGET_VOLUME, pitch, CLIP_DURATION);
        mc.getSoundManager().play(currentSound);

        ticksUntilNext = CLIP_DURATION - OVERLAP;
    }

    private static void stopAll(Minecraft mc) {
        if (currentSound != null) {
            currentSound.fadeOut();
            currentSound = null;
        }
        ticksUntilNext = 0;
    }

    private static void resetSuffocationState(Minecraft mc) {
        wasSuffocating = false;
        if (suffocateSound != null) {
            mc.getSoundManager().stop(suffocateSound);
            suffocateSound = null;
        }
    }

    private static boolean hasUsableO2Tank(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof O2TankItem
                    && stack.getOrDefault(ModDataComponents.O2_LEVEL.get(), 0) > 0) {
                return true;
            }
        }
        return false;
    }
}
