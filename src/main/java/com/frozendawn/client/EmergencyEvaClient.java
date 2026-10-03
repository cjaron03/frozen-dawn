package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.EmergencyEvaHandler;
import com.frozendawn.init.ModSounds;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Only two HUD lines; deterioration and local beeps communicate the recovery limit. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class EmergencyEvaClient {
    private static UUID announcedIssue;
    private static int beepCooldown;
    private static int creakCooldown;
    private EmergencyEvaClient() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        announcedIssue = null;
        beepCooldown = 0;
        creakCooldown = 0;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || mc.player.isCreative() || mc.player.isSpectator()
                || !mc.player.isAlive() || !EmergencyEvaHandler.isWearingIssuedPiece(mc.player)) return;
        var state = mc.player.getData(com.frozendawn.init.ModAttachments.EMERGENCY_EVA);
        if (!state.issue().equals(announcedIssue)) {
            announcedIssue = state.issue();
            beepCooldown = 0;
            creakCooldown = 0;
            beep(0.65F, 0.85F);
        }
        if (beepCooldown > 0) beepCooldown--;
        if (creakCooldown > 0) creakCooldown--;
        int seconds = (state.remainingTicks() + 19) / 20;
        if (seconds > 0 && seconds <= 120 && beepCooldown == 0) {
            beep(seconds <= 15 ? 0.85F : 0.65F, seconds <= 60 ? 1.16F : 1.0F);
            beepCooldown = seconds <= 15 ? 20 : seconds <= 60 ? 60 : 200;
        }
        if (seconds > 0 && seconds <= 300 && creakCooldown == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GLASS_BREAK, 0.07F, 0.55F));
            creakCooldown = seconds <= 120 ? 600 : 1200;
        }
    }

    public static void renderReadout(GuiGraphics graphics) {
        var mc = Minecraft.getInstance();
        int seconds = (EmergencyEvaHandler.remainingTicks(mc.player) + 19) / 20;
        var header = Component.translatable(seconds > 0
                ? "hud.frozendawn.emergency_eva.active" : "hud.frozendawn.emergency_eva.depleted");
        var reserve = Component.translatable("hud.frozendawn.emergency_eva.reserve", AirStatusEtaPolicy.format(seconds));
        int x = TemperatureHud.HUD_X;
        int y = TemperatureHud.HUD_Y + TemperatureHud.TOTAL_HEIGHT + 2;
        int width = Math.max(mc.font.width(header), mc.font.width(reserve)) + 8;
        int color = seconds <= 60 ? 0xFFFF7770 : 0xFFFFCC70;
        graphics.fill(x, y, x + width, y + 24, 0xAA0B1217);
        int alpha = seconds <= 15 && seconds > 0
                ? 130 + (int) (50 * (1 + Math.sin(mc.player.tickCount * 0.15))) : 210;
        graphics.fill(x, y, x + 2, y + 24, (alpha << 24) | (color & 0xFFFFFF));
        graphics.drawString(mc.font, header, x + 4, y + 3, color);
        graphics.drawString(mc.font, reserve, x + 4, y + 13, color);
    }

    private static void beep(float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(ModSounds.SUIT_OXYGEN_BEEP.get(), volume, pitch));
    }
}
