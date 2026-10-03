package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.EmergencyEvaHandler;
import com.frozendawn.init.ModSounds;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Local emergency reserve alarms; the shared EVA HUD renders its telemetry. */
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

    private static void beep(float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(ModSounds.SUIT_OXYGEN_BEEP.get(), volume, pitch));
    }
}
