package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.SuitIntegrityPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client mirror and presentation for EVA suit punctures. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class SuitIntegrityClient {

    private static final ResourceLocation CRACK_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/destroy_stage_4.png");
    private static int punctures;
    private static int o2Ticks;
    private static int maxO2Ticks;
    private static int patchTicks = -1;
    private static int patchDurationTicks;
    private static int beepCooldown;
    private static int recentVentTicks;
    private static TickableSuitLeakSound leakSound;

    private SuitIntegrityClient() {
    }

    public static void update(SuitIntegrityPayload payload) {
        int previousO2 = o2Ticks;
        punctures = payload.punctures();
        o2Ticks = payload.o2Ticks();
        maxO2Ticks = payload.maxO2Ticks();
        patchTicks = payload.patchTicks();
        patchDurationTicks = payload.patchDurationTicks();
        if (punctures > 0 && o2Ticks > 0 && o2Ticks < previousO2) {
            recentVentTicks = 20;
        }

        Minecraft minecraft = Minecraft.getInstance();
        switch (payload.eventId()) {
            case SuitIntegrityPayload.PUNCTURED -> {
                playUi(SoundEvents.GLASS_BREAK, 0.72F, 0.68F);
                playUi(SoundEvents.FIRE_EXTINGUISH, 0.82F, 1.15F);
                playUi(ModSounds.SUIT_PUNCTURE_WARNING.get(), 1.0F, 1.0F);
                MasterArchitectFloodClient.showWarningSuitDialogue(
                        "ui.frozendawn.suit.puncture_warning");
            }
            case SuitIntegrityPayload.OXYGEN_CRITICAL -> {
                playUi(ModSounds.SUIT_OXYGEN_CRITICAL.get(), 1.0F, 1.0F);
                MasterArchitectFloodClient.showWarningSuitDialogue(
                        "ui.frozendawn.suit.oxygen_critical");
            }
            case SuitIntegrityPayload.PATCHED -> {
                playUi(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.65F, 1.35F);
                MasterArchitectFloodClient.showSuitDialogue(
                        "ui.frozendawn.suit.patch_complete");
            }
            case SuitIntegrityPayload.PATCH_DEGRADED -> {
                playUi(SoundEvents.GLASS_BREAK, 0.65F, 0.55F);
                playUi(SoundEvents.FIRE_EXTINGUISH, 0.75F, 0.85F);
                MasterArchitectFloodClient.showWarningSuitDialogue(
                        "ui.frozendawn.suit.patch_degraded");
            }
            case SuitIntegrityPayload.EMERGENCY_RESERVE -> {
                playUi(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.6F, 1.45F);
                playUi(ModSounds.SUIT_EMERGENCY_RESERVE.get(), 1.0F, 1.0F);
                MasterArchitectFloodClient.showSuitDialogue(
                        "ui.frozendawn.suit.emergency_reserve");
            }
            default -> {
            }
        }
        if (minecraft.player == null) {
            reset();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.isPaused()) {
            stopLeakSound();
            return;
        }
        if (patchTicks >= 0 && !minecraft.player.isUsingItem()) {
            patchTicks = -1;
            patchDurationTicks = 0;
        }
        if (recentVentTicks > 0) {
            recentVentTicks--;
        }
        updateLeakSound(minecraft);
        if (beepCooldown > 0) {
            beepCooldown--;
        }
        float ratio = oxygenRatio();
        if (punctures > 0 && o2Ticks > 0 && ratio <= 0.25F && beepCooldown <= 0) {
            playUi(
                    ModSounds.SUIT_OXYGEN_BEEP.get(),
                    ratio <= 0.10F ? 1.0F : 0.82F,
                    ratio <= 0.10F ? 1.16F : 1.0F);
            beepCooldown = ratio <= 0.10F ? 16 : 34;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.options.hideGui
                || OrsaAwakeningIntro.shouldSuppressSurvivalHud()) {
            return;
        }
        if (punctures > 0 && FrozenDawnConfig.ENABLE_SUIT_PUNCTURE_OVERLAY.get()) {
            renderCracks(graphics);
        }
        if (patchTicks >= 0 && patchDurationTicks > 0) {
            renderPatchProgress(graphics, minecraft);
        }
    }

    public static int punctures() {
        return punctures;
    }

    public static float oxygenRatio() {
        return maxO2Ticks <= 0
                ? 0.0F
                : Mth.clamp(o2Ticks / (float) maxO2Ticks, 0.0F, 1.0F);
    }

    private static void renderCracks(GuiGraphics graphics) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int size = Math.min(112, Math.max(64, Math.min(width, height) / 4));
        float pulse = 0.78F + 0.12F * Mth.sin(
                (Minecraft.getInstance().player.tickCount
                        + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false))
                        * 0.18F);
        float alpha = Math.min(0.78F, (0.28F + punctures * 0.18F) * pulse);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 0.16F, 0.12F, alpha);
        graphics.blit(CRACK_TEXTURE, 0, 0, size, size, 0.0F, 0.0F, 16, 16, 16, 16);
        graphics.blit(CRACK_TEXTURE, width - size, 0, size, size,
                0.0F, 0.0F, 16, 16, 16, 16);
        graphics.blit(CRACK_TEXTURE, 0, height - size, size, size,
                0.0F, 0.0F, 16, 16, 16, 16);
        graphics.blit(CRACK_TEXTURE, width - size, height - size, size, size,
                0.0F, 0.0F, 16, 16, 16, 16);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderPatchProgress(GuiGraphics graphics, Minecraft minecraft) {
        float progress = Mth.clamp(patchTicks / (float) patchDurationTicks, 0.0F, 1.0F);
        int width = 92;
        int height = 7;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 72;
        graphics.fill(x - 1, y - 11, x + width + 1, y + height + 1, 0xB20A1014);
        graphics.drawCenteredString(
                minecraft.font,
                "SEALING EVA BREACH",
                x + width / 2,
                y - 9,
                0xFFE6C96A);
        graphics.fill(x, y, x + width, y + height, 0xFF281A18);
        graphics.fill(x + 1, y + 1,
                x + 1 + Math.round((width - 2) * progress),
                y + height - 1,
                0xFFE8BA39);
    }

    private static void playUi(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(sound, volume, pitch));
        }
    }

    private static void updateLeakSound(Minecraft minecraft) {
        boolean venting = recentVentTicks > 0 && punctures > 0 && o2Ticks > 0;
        if (venting) {
            float targetVolume = punctures >= 2 ? 0.16F : 0.11F;
            if (leakSound == null || leakSound.isStopped()) {
                leakSound = new TickableSuitLeakSound(
                        ModSounds.SUIT_LEAK_HISS.get(), targetVolume);
                minecraft.getSoundManager().play(leakSound);
            } else {
                leakSound.setTargetVolume(targetVolume);
            }
        } else if (leakSound != null) {
            leakSound.fadeOut();
            if (leakSound.isStopped()) {
                leakSound = null;
            }
        }
    }

    private static void stopLeakSound() {
        if (leakSound != null) {
            Minecraft.getInstance().getSoundManager().stop(leakSound);
            leakSound = null;
        }
        recentVentTicks = 0;
    }

    private static void reset() {
        stopLeakSound();
        punctures = 0;
        o2Ticks = 0;
        maxO2Ticks = 0;
        patchTicks = -1;
        patchDurationTicks = 0;
        beepCooldown = 0;
    }
}
