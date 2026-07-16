package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.HearthBoundaryEffectPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client-local warning audio plus a short non-directive Orsathae impact. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class HearthBoundaryEffects {
    private static final int PULSE_DURATION_TICKS = 34;
    private static final int SHAKE_DURATION_TICKS = 18;

    private static int pulseTicks;
    private static int shakeTicks;

    private HearthBoundaryEffects() {
    }

    public static void trigger(HearthBoundaryEffectPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) {
            return;
        }
        if (payload.effectType() == HearthBoundaryEffectPayload.WARNING) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.HEARTH_BOUNDARY_WARNING.get(), 0.92F, 1.15F));
            return;
        }
        if (payload.effectType() != HearthBoundaryEffectPayload.ORSATHAE) {
            return;
        }

        pulseTicks = PULSE_DURATION_TICKS;
        shakeTicks = SHAKE_DURATION_TICKS;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                ModSounds.HEARTH_BOUNDARY_ORSATHAE.get(), 0.95F, 1.25F));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                ModSounds.HEARTH_BOUNDARY_ORSATHAE.get(), 0.76F, 0.62F));
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (pulseTicks <= 0) {
            return;
        }
        int elapsed = PULSE_DURATION_TICKS - pulseTicks;
        float fade = Mth.clamp(pulseTicks / (float) PULSE_DURATION_TICKS, 0.0F, 1.0F);
        float wave = 0.45F + 0.55F * Math.abs(Mth.sin(elapsed * 0.46F));
        int washAlpha = Math.round(68.0F * fade * wave);
        int edgeAlpha = Math.round(128.0F * fade);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        graphics.fill(0, 0, width, height, argb(washAlpha, 0x00151A1D));
        int edge = 3;
        int edgeColor = argb(edgeAlpha, 0x001BC7CF);
        graphics.fill(0, 0, width, edge, edgeColor);
        graphics.fill(0, height - edge, width, height, edgeColor);
        graphics.fill(0, 0, edge, height, edgeColor);
        graphics.fill(width - edge, 0, width, height, edgeColor);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }
        if (pulseTicks > 0) {
            pulseTicks--;
        }
        if (shakeTicks > 0) {
            shakeTicks--;
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (shakeTicks <= 0 || minecraft.level == null) {
            return;
        }
        float strength = shakeTicks / (float) SHAKE_DURATION_TICKS;
        double time = minecraft.level.getGameTime() + shakeTicks * 0.37D;
        float pitch = (float) (Math.sin(time * 3.7D) * 0.72D * strength);
        float yaw = (float) (Math.cos(time * 4.9D) * 0.92D * strength);
        event.setPitch(event.getPitch() + pitch);
        event.setYaw(event.getYaw() + yaw);
    }

    private static int argb(int alpha, int rgb) {
        return Mth.clamp(alpha, 0, 255) << 24 | rgb & 0x00FFFFFF;
    }
}
